#!/usr/bin/env python3
"""Build porting/manifest.json and porting/inventory/summary.json from porting/scope.json.

Re-runnable after an upstream bump. Files not covered by scope.json get status
"unclassified"; the manifest is still written and the script exits non-zero.

JAVA PATH RULES
- Base package org.graylog.kusto. Namespace Kusto.Language[.X] -> org.graylog.kusto.language[.x]
  (each segment lowercased).
- Namespace Kusto.Language.Generator -> module kusto-language-generator, package
  org.graylog.kusto.language.generator. Everything else -> module kusto-language.
- javaPath = <module>/src/main/java/<package as dirs>/<JavaName>.java
- One Java file per TOP-LEVEL C# type (nested types are not recorded). A C# file with N
  top-level types maps to N javaPaths, in declaration order (entry.types[]).
- Partial classes: every file declaring `partial` type X (same namespace, same Java name) maps to
  the SAME javaPath. Such types carry partialGroup (= Java name) and mergeOrder (ordinal byte-wise
  sort position of the upstream file name inside the group; generated part is always 0).
- Generic-arity families: if >=2 top-level types in one namespace share a name with different
  arity, the LOWEST arity keeps the name; each higher arity gets its arity number appended
  (Parser<A,B> -> Parser2). Detected generically; exactly 11 families are asserted.
- Generated syntax nodes: GeneratedSyntaxNodes.tt expands to one Java file per `Name = "X"` in
  Kusto.Language.Generators/SyntaxNodeInfos.cs (225 expected) plus SyntaxVisitor,
  DefaultSyntaxVisitor, SyntaxVisitor1, DefaultSyntaxVisitor1. Hand-written partials of those
  classes map to the generated path with a mergeNote.
- Stubs: from scope.json; status "stubbed".
- Per-type drops: scope.json "droppedTypes" [{upstreamPath,type,reason}]; the type's javaPath is
  omitted from the entry (and its types[]) and recorded under entry "droppedTypes".
- Synthetic entries: scope.json "synthetic" [{javaPath,module,wave,purpose}] -> entries with
  upstreamPath/upstreamBlob null, scope "synthetic", status "pending".
- Hand-owned fields: porting/status.json (optional, never written by this script) maps
  <upstreamPath> (or <javaPath> for synthetic entries) -> {status, notes, syncedAt, droppedMembers, ...};
  its fields are merged over the computed entry. Defaults: syncedAt = manifest upstreamCommit,
  droppedMembers = [].
Status values: pending, generated, stubbed, excluded, unclassified (ported/partial set by hand).
"""
import json, re, subprocess, sys, os, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))      # porting/
REPO = os.path.dirname(ROOT)
SUB = os.path.join(REPO, 'upstream', 'kusto-query-language')
SCOPE = os.path.join(ROOT, 'scope.json')
INV = os.path.join(ROOT, 'inventory')
NODE_INFOS = 'src/Kusto.Language.Generators/SyntaxNodeInfos.cs'
GEN_TT = 'src/Kusto.Language/Syntax/CodeGen/GeneratedSyntaxNodes.tt'
GEN_NS = 'Kusto.Language.Syntax'
EXTRA_VISITORS = ['SyntaxVisitor', 'DefaultSyntaxVisitor', 'SyntaxVisitor1', 'DefaultSyntaxVisitor1']
EXPECT_NODES = 225
EXPECT_FAMILIES = 11
MERGE_NOTE = 'hand-written partial merged into generated class via protected region'

# ---------------------------------------------------------------- source stripping
def strip_source(src):
    """Blank comments, string/char literal contents and preprocessor lines (keeps newlines)."""
    out = []
    i, n = 0, len(src)
    line_start = True
    while i < n:
        c = src[i]
        two = src[i:i+2]
        if c == '\n':
            out.append(c); i += 1; line_start = True; continue
        if line_start and c in ' \t﻿\r':
            out.append(c); i += 1; continue
        if line_start and c == '#':
            while i < n and src[i] != '\n':
                i += 1
            continue
        line_start = False
        if two == '//':
            while i < n and src[i] != '\n':
                i += 1
            continue
        if two == '/*':
            j = src.find('*/', i + 2)
            j = n if j < 0 else j + 2
            out.append(''.join('\n' if ch == '\n' else ' ' for ch in src[i:j])); i = j; continue
        # raw string """ ... """
        m = re.match(r'\$*"""+', src[i:i+40])
        if m:
            q = m.group(0).lstrip('$'); j = src.find(q, i + len(m.group(0)))
            j = n if j < 0 else j + len(q)
            out.append('""' + ''.join('\n' if ch == '\n' else ' ' for ch in src[i:j])); i = j; continue
        m = re.match(r'(@\$|\$@|@|\$)?"', src[i:i+3])
        if m and (c in '@$"'):
            prefix = m.group(1) or ''
            verbatim = '@' in prefix
            j = i + len(m.group(0))
            while j < n:
                ch = src[j]
                if verbatim:
                    if ch == '"':
                        if src[j+1:j+2] == '"': j += 2; continue
                        break
                else:
                    if ch == '\\': j += 2; continue
                    if ch == '"' or ch == '\n': break
                j += 1
            seg = src[i:j+1]
            out.append('""' + ''.join('\n' if ch == '\n' else ' ' for ch in seg)); i = j + 1; continue
        if c == "'":
            j = i + 1
            while j < n and src[j] != "'" and src[j] != '\n':
                j += 2 if src[j] == '\\' else 1
            out.append("''" + ' ' * (j - i - 1)); i = j + 1; continue
        out.append(c); i += 1
    return ''.join(out)

NS_RE = re.compile(r'\bnamespace\s+([A-Za-z_][\w.]*)\s*([{;])')
MODS = r'(?:public|internal|private|protected|static|abstract|sealed|readonly|partial|unsafe|new|ref|file)'
TYPE_RE = re.compile(r'\b((?:' + MODS + r'\s+)*)(class|struct|enum|interface|record)\s+([A-Za-z_]\w*)')
DELEG_RE = re.compile(r'\b((?:' + MODS + r'\s+)*)delegate\s+[\w<>\[\],.?\s]+?\s+([A-Za-z_]\w*)\s*(?=[<(])')

def arity_at(text, pos):
    """Count generic params in <...> immediately at text[pos:] (after optional spaces)."""
    m = re.match(r'\s*<', text[pos:])
    if not m:
        return 0
    i = pos + m.end(); depth = 1; commas = 0
    while i < len(text) and depth:
        ch = text[i]
        if ch == '<': depth += 1
        elif ch == '>': depth -= 1
        elif ch == ',' and depth == 1: commas += 1
        i += 1
    return commas + 1

def parse_cs(src):
    code = strip_source(src)
    # depth per position + namespace tracking
    depth_at = [0] * (len(code) + 1)
    d = 0
    for i, ch in enumerate(code):
        if ch == '{': d += 1
        depth_at[i] = d
        if ch == '}': d -= 1
    depth_at[len(code)] = d
    # depth_at[i] for '{' is already incremented; fix: use depth before char
    def depth_before(pos):
        dd = depth_at[pos]
        if code[pos:pos+1] == '{': dd -= 1
        if code[pos:pos+1] == '}': dd += 1
        return dd
    namespaces = []   # (name, body_depth, pos)
    for m in NS_RE.finditer(code):
        bd = depth_before(m.start()) + (1 if m.group(2) == '{' else 0)
        namespaces.append((m.group(1), bd, m.start()))
    ns = namespaces[0][0] if namespaces else None
    types = []
    def prev_ok(pos):
        j = pos - 1
        while j >= 0 and code[j] in ' \t\r\n': j -= 1
        return j < 0 or code[j] in ';{}]'
    def ns_for(pos, dpt):
        best = None
        for name, bd, p in namespaces:
            if p < pos and bd == dpt:
                best = name
        return best
    for rx in (TYPE_RE, DELEG_RE):
        for m in rx.finditer(code):
            dpt = depth_before(m.start())
            owner = ns_for(m.start(), dpt)
            if owner is None or not prev_ok(m.start()):
                continue
            mods = m.group(1).split()
            if rx is TYPE_RE:
                kind, name = m.group(2), m.group(3)
                after = m.end()
            else:
                kind, name = 'delegate', m.group(2)
                after = m.end()
            if kind == 'record': kind = 'class'
            ar = arity_at(code, after)
            vis = next((x for x in mods if x in ('public', 'internal', 'private', 'protected')), 'internal')
            types.append({'pos': m.start(), 'namespace': owner, 'name': name, 'kind': kind, 'arity': ar,
                          'partial': 'partial' in mods, 'visibility': vis, 'modifiers': mods})
    types.sort(key=lambda t: t['pos'])
    return ns, types, d

# ---------------------------------------------------------------- java path helpers
def module_package(namespace):
    segs = namespace.split('.')
    if namespace == 'Kusto.Language.Generator':
        return 'kusto-language-generator', 'org.graylog.kusto.language.generator'
    if segs[:2] != ['Kusto', 'Language']:
        raise ValueError('unexpected namespace ' + namespace)
    return 'kusto-language', '.'.join(['org', 'graylog', 'kusto', 'language'] + [s.lower() for s in segs[2:]])

def java_path(namespace, java_name):
    mod, pkg = module_package(namespace)
    return '%s/src/main/java/%s/%s.java' % (mod, pkg.replace('.', '/'), java_name)

# ---------------------------------------------------------------- scope matching
def under(path, rule):
    return path == rule or (rule.endswith('/') and path.startswith(rule)) or (not rule.endswith('/') and path.startswith(rule + '/'))

def longest(path, keys):
    best = None
    for k in keys:
        if under(path, k) and (best is None or len(k) > len(best)):
            best = k
    return best

def classify(path, rules):
    for st in rules['stubs']:
        if st['upstreamPath'] == path: return 'stub', st
    for cat in ('buildTime', 'optional', 'include', 'exclude'):
        k = longest(path, rules.get(cat, []))
        if k is not None: return {'buildTime': 'buildTime', 'optional': 'optional', 'include': 'include', 'exclude': 'exclude'}[cat], k
    return None, None

# ---------------------------------------------------------------- main
def git(*args):
    return subprocess.check_output(['git', '-C', SUB] + list(args), text=True)

def main():
    scope = json.load(open(SCOPE))
    rules, waves = scope['rules'], scope['waves']
    reasons = scope.get('reasons', {})
    commit = git('rev-parse', 'HEAD').strip()
    errors, warnings = [], []
    if commit != scope['upstreamCommit']:
        warnings.append('submodule HEAD %s != scope.json upstreamCommit %s' % (commit[:8], scope['upstreamCommit'][:8]))
    tree = {}
    for line in git('ls-tree', '-r', 'HEAD').splitlines():
        meta, p = line.split('\t', 1)
        tree[p] = meta.split()[2]
    # enrichment
    purpose, traps = {}, {}
    for fn in sorted(os.listdir(INV)):
        if fn.endswith('.json') and fn not in ('dependency-cut.json', 'summary.json'):
            try: dd = json.load(open(os.path.join(INV, fn)))
            except Exception: continue
            if isinstance(dd, dict) and 'files' in dd:
                for f in dd['files']:
                    purpose.setdefault(f['upstreamPath'], f.get('purpose') or '')
                    traps.setdefault(f['upstreamPath'], f.get('traps') or [])
    depcut = {}
    dc = os.path.join(INV, 'dependency-cut.json')
    if os.path.exists(dc):
        j = json.load(open(dc))
        for k in ('include', 'exclude'):
            for e in j.get(k, []):
                depcut[e['upstreamPath']] = e.get('reason', '')
    # generated node names
    node_src = git('show', 'HEAD:' + NODE_INFOS)
    node_names = re.findall(r'new\s+SyntaxNodeInfo\s*\{\s*Name\s*=\s*"(\w+)"', node_src)
    nodes_ok = len(node_names) == EXPECT_NODES and len(set(node_names)) == EXPECT_NODES
    gen_set = set(node_names)

    entries = {}
    parsed = {}  # path -> (ns, types, depth)
    for path in sorted(tree):
        kind, rule = classify(path, rules)
        e = {'upstreamPath': path, 'upstreamBlob': tree[path], 'scope': None, 'status': 'unclassified', 'wave': None,
             'lines': 0, 'namespace': None, 'types': [], 'javaPaths': [], 'partialGroup': None,
             'purpose': purpose.get(path, ''), 'traps': traps.get(path, []), 'notes': ''}
        data = git('show', 'HEAD:' + path) if path else ''
        e['lines'] = len(data.splitlines())
        w = longest(path, waves.keys())
        if kind is None:
            pass
        elif kind == 'exclude':
            e.update(scope='exclude', status='excluded', wave=None)
            e['notes'] = reasons.get(rule) or reasons.get(path) or depcut.get(path, '')
        elif kind == 'stub':
            e.update(scope='stub', status='stubbed', wave=waves[w] if w else None, javaPaths=[rule['javaPath']])
            e['stubbedMembers'] = rule['stubbedMembers']; e['notes'] = rule['reason']
        else:
            e['scope'] = kind
            e['status'] = 'pending'
            e['wave'] = waves[w] if w is not None else None
            if w is None:
                errors.append('no wave for in-scope file ' + path)
            e['notes'] = depcut.get(path, '')
        if path.endswith('.cs') and kind not in ('exclude',):
            try:
                ns, types, dend = parse_cs(data)
                if dend != 0: warnings.append('unbalanced braces in %s (depth %d)' % (path, dend))
                parsed[path] = (ns, types)
                e['namespace'] = ns
            except Exception as ex:
                errors.append('parse error %s: %s' % (path, ex))
        entries[path] = e

    # stub .tt entries: namespace from the stub javaPath package
    for path, e in entries.items():
        if e['scope'] == 'stub' and e['javaPaths']:
            jp = e['javaPaths'][0]
            parts = jp.split('/src/main/java/')[1].rsplit('/', 1)[0].split('/')
            e['namespace'] = '.'.join(['Kusto', 'Language'] + [p.capitalize() for p in parts[4:]])
            if e['namespace'].endswith('.Parsing') or e['namespace'].endswith('.Editor'): pass

    # ---- arity families over in-scope (non-excluded) types
    fam_src = collections.defaultdict(dict)   # (ns,name) -> {arity: [paths]}
    for path, (ns, types) in parsed.items():
        if entries[path]['scope'] in ('exclude', None): continue
        for t in types:
            fam_src[(t['namespace'], t['name'])].setdefault(t['arity'], []).append(path)
    # generated visitors also participate (SyntaxVisitor / SyntaxVisitor<R> come from SyntaxVisitor.cs partials)
    families = {}
    for (ns, name), ar in sorted(fam_src.items()):
        if len(ar) > 1:
            families[(ns, name)] = sorted(ar)
    def java_name(ns, name, arity):
        fam = families.get((ns, name))
        if fam and arity != fam[0]:
            return '%s%d' % (name, arity)
        return name

    # ---- type records
    for path, (ns, types) in parsed.items():
        e = entries[path]
        if e['scope'] in ('exclude', None): continue
        recs = []
        for t in types:
            jn = java_name(t['namespace'], t['name'], t['arity'])
            rec = {'name': t['name'], 'kind': t['kind'], 'arity': t['arity'], 'visibility': t['visibility'],
                   'partial': t['partial'], 'namespace': t['namespace'], 'javaName': jn}
            try:
                rec['javaPath'] = java_path(t['namespace'], jn)
            except ValueError as ex:
                errors.append('%s: %s' % (path, ex)); rec['javaPath'] = None
            if t['namespace'] == GEN_NS and jn in gen_set:
                rec['generatedNode'] = True
                if not t['partial']:
                    warnings.append('%s: hand-written type %s shares name with generated node but is not partial' % (path, jn))
            recs.append(rec)
        drops = [d for d in scope.get('droppedTypes', []) if d['upstreamPath'] == path]
        if drops:
            dn = {d['type']: d for d in drops}
            e['droppedTypes'] = [{'type': r['name'], 'javaPath': r['javaPath'], 'reason': dn[r['name']]['reason']} for r in recs if r['name'] in dn]
            recs = [r for r in recs if r['name'] not in dn]
        e['types'] = recs
        if e['scope'] == 'stub':
            for r in recs: r['stub'] = True       # javaPath of the stub is e['javaPaths'], not derived
        else:
            e['javaPaths'] = [r['javaPath'] for r in recs if r['javaPath']]

    # ---- generated entry
    gen = entries.get(GEN_TT)
    if gen and gen['scope'] is not None:
        gen['status'] = 'generated'
        gen['namespace'] = GEN_NS
        names = node_names + EXTRA_VISITORS
        gen['types'] = [{'name': n, 'kind': 'class', 'arity': 0, 'visibility': 'public', 'partial': True,
                         'namespace': GEN_NS, 'javaName': n, 'javaPath': java_path(GEN_NS, n), 'generated': True}
                        for n in names]
        gen['javaPaths'] = [t['javaPath'] for t in gen['types']]
        gen['generatedNodeCount'] = len(node_names)
    elif gen is None:
        errors.append('GeneratedSyntaxNodes.tt missing from tree')

    # ---- partial groups: type-level, keyed by javaPath
    by_jp = collections.defaultdict(list)   # javaPath -> [(entry, type)]
    for e in entries.values():
        for t in e['types']:
            if t.get('javaPath') and not t.get('stub'): by_jp[t['javaPath']].append((e, t))
    for jp, lst in by_jp.items():
        partial_like = [(e, t) for e, t in lst if t.get('partial') or t.get('generated')]
        if len(lst) > 1:
            def sk(et):
                e, t = et
                return (0 if t.get('generated') else 1, os.path.basename(e['upstreamPath']).encode(), e['upstreamPath'])
            lst.sort(key=sk)
            for i, (e, t) in enumerate(lst):
                t['partialGroup'] = t['javaName']
                t['mergeOrder'] = i
                if any(x.get('generated') for _, x in lst) and not t.get('generated'):
                    t['mergeNote'] = MERGE_NOTE
        elif lst[0][1].get('partial') and not lst[0][1].get('generated'):
            lst[0][1]['partialGroup'] = lst[0][1]['javaName']; lst[0][1]['mergeOrder'] = 0
    for e in entries.values():
        groups = [t['partialGroup'] for t in e['types'] if t.get('partialGroup')]
        if len(groups) == 1: e['partialGroup'] = groups[0]
        elif groups: e['partialGroup'] = groups
        if any(t.get('mergeNote') for t in e['types']): e['mergeNote'] = MERGE_NOTE
        if e['partialGroup'] is not None:
            mo = [t.get('mergeOrder') for t in e['types'] if t.get('partialGroup')]
            e['mergeOrder'] = mo[0] if len(mo) == 1 else mo

    # ---- validation
    report = []
    unclassified = sorted(p for p, e in entries.items() if e['status'] == 'unclassified')
    # (a)
    ok_a = set(entries) == set(tree) and len(entries) == len(tree)
    report.append(('(a) one entry per upstream file', ok_a, '%d files, %d entries' % (len(tree), len(entries))))
    # (b)
    bad_b = []
    for jp, lst in by_jp.items():
        if len(lst) > 1:
            if len({t.get('partialGroup') for _, t in lst}) != 1 or not all(t.get('partial') or t.get('generated') for _, t in lst):
                bad_b.append(jp)
    for jp in bad_b: errors.append('javaPath shared outside a partial group: ' + jp)
    # stubs vs types
    stub_paths = collections.Counter(p for e in entries.values() if e['scope'] == 'stub' for p in e['javaPaths'])
    for p, c in stub_paths.items():
        if c > 1 or p in by_jp: bad_b.append(p); errors.append('stub javaPath collides: ' + p)
    report.append(('(b) javaPath sharing only within partial groups', not bad_b, '%d shared javaPaths, %d bad' % (sum(1 for l in by_jp.values() if len(l) > 1), len(bad_b))))
    # (c) unique file names per module
    namecheck = collections.defaultdict(set)
    modnames = collections.defaultdict(lambda: collections.defaultdict(set))
    all_jps = set(by_jp)
    for e in entries.values():
        if e['scope'] == 'stub': all_jps.update(e['javaPaths'])
    for jp in all_jps:
        mod = jp.split('/')[0]
        modnames[mod][os.path.basename(jp)].add(jp)
    dup_c = [(m, n, sorted(v)) for m, d in modnames.items() for n, v in d.items() if len(v) > 1]
    for x in dup_c: errors.append('duplicate Java file name in module %s: %s' % (x[0], x[2]))
    report.append(('(c) Java file names unique per module', not dup_c, '%d modules, %d paths, %d dup names' % (len(modnames), len(all_jps), len(dup_c))))
    # (d)
    report.append(('(d) generated node names', nodes_ok, '%d names found (%d unique), expect %d' % (len(node_names), len(set(node_names)), EXPECT_NODES)))
    # (e)
    fam_table = []
    for (ns, name), ars in sorted(families.items()):
        row = {'namespace': ns, 'name': name, 'arities': ars, 'javaNames': [java_name(ns, name, a) for a in ars]}
        fam_table.append(row)
    ok_e = len(families) == EXPECT_FAMILIES
    report.append(('(e) arity families', ok_e, '%d found, expect %d' % (len(families), EXPECT_FAMILIES)))
    for ok, msg in ((nodes_ok, 'generated node count mismatch'), (ok_e, 'arity family count mismatch')):
        if not ok: errors.append(msg)
    if not ok_a: errors.append('entry/file mismatch')

    # ---- synthetic entries
    status_over = {}
    sp = os.path.join(ROOT, 'status.json')
    if os.path.exists(sp):
        status_over = json.load(open(sp))
    for sy in scope.get('synthetic', []):
        jp = sy['javaPath']
        if jp in entries:
            errors.append('duplicate synthetic javaPath ' + jp); continue
        entries[jp] = {'upstreamPath': None, 'upstreamBlob': None, 'scope': 'synthetic', 'status': 'pending',
                       'wave': sy.get('wave'), 'lines': 0, 'namespace': None, 'types': [], 'javaPaths': [jp],
                       'partialGroup': None, 'purpose': sy.get('purpose', ''), 'traps': [], 'notes': '',
                       'module': sy.get('module')}
    for k in status_over:
        if k not in entries: warnings.append('status.json key not in manifest: ' + k)
    for k, e in entries.items():
        e.update(status_over.get(k, {}))
        e.setdefault('syncedAt', commit)
        e.setdefault('droppedMembers', [])
    # synthetic javaPaths participate in uniqueness checks
    for sy in scope.get('synthetic', []):
        if sy['javaPath'] in by_jp or any(sy['javaPath'] in e['javaPaths'] for e in entries.values() if e['scope'] != 'synthetic'):
            errors.append('synthetic javaPath collides: ' + sy['javaPath'])
        modnames[sy['javaPath'].split('/')[0]][os.path.basename(sy['javaPath'])].add(sy['javaPath'])
        all_jps.add(sy['javaPath'])
    dup_c = [(m, n, sorted(v)) for m, d in modnames.items() for n, v in d.items() if len(v) > 1]
    for x in dup_c:
        msg = 'duplicate Java file name in module %s: %s' % (x[0], x[2])
        if msg not in errors: errors.append(msg)
    report = [r if not r[0].startswith('(c)') else (r[0], not dup_c, '%d modules, %d paths (incl. synthetic), %d dup names' % (len(modnames), len(all_jps), len(dup_c))) for r in report]

    # ---- write manifest
    out = {'upstreamCommit': commit, 'generatedBy': 'porting/tools/build_manifest.py',
           'entries': [entries[p] for p in sorted(entries, key=lambda k: (entries[k]['upstreamPath'] is None, k))]}
    for e in out['entries']:
        for t in e['types']:
            t.pop('namespace', None) if t.get('namespace') == e['namespace'] else None
    with open(os.path.join(ROOT, 'manifest.json'), 'w') as f:
        json.dump(out, f, indent=1); f.write('\n')

    # ---- summary
    ents = out['entries']
    waves_s = collections.defaultdict(lambda: {'files': 0, 'lines': 0, 'javaFiles': 0})
    jfiles_by_wave = collections.defaultdict(set)
    scope_c, status_c = collections.Counter(), collections.Counter()
    inscope_lines = 0
    for e in ents:
        scope_c[e['scope'] or 'none'] += 1; status_c[e['status']] += 1
        if e['scope'] not in ('exclude', None):
            w = str(e['wave']); waves_s[w]['files'] += 1; waves_s[w]['lines'] += e['lines']
            jfiles_by_wave[w].update(e['javaPaths'])
            inscope_lines += e['lines']
    for w in waves_s: waves_s[w]['javaFiles'] = len(jfiles_by_wave[w])
    multi = sorted(((e['upstreamPath'], len(e['types'])) for e in ents if len(e['types']) > 1 and e['status'] != 'generated'), key=lambda x: (-x[1], x[0]))
    summary = {'upstreamCommit': commit, 'perWave': dict(sorted(waves_s.items())), 'perScope': dict(scope_c),
               'perStatus': dict(status_c), 'entriesTotal': len(ents), 'syntheticCount': scope_c.get('synthetic', 0), 'inScopeLines': inscope_lines,
               'totalJavaFiles': len({p for e in ents for p in e['javaPaths']}),
               'arityFamilies': fam_table, 'multiTypeFiles': [{'upstreamPath': p, 'topLevelTypes': c} for p, c in multi],
               'generatedNodeCount': len(node_names), 'unclassified': unclassified}
    with open(os.path.join(INV, 'summary.json'), 'w') as f:
        json.dump(summary, f, indent=1); f.write('\n')

    print('VALIDATION')
    for name, ok, info in report:
        print('  [%s] %s: %s' % ('OK' if ok else 'FAIL', name, info))
    print('ARITY FAMILIES')
    for r in fam_table:
        print('  %s.%s: %s' % (r['namespace'], r['name'], ', '.join('<%d>=%s' % (a, j) for a, j in zip(r['arities'], r['javaNames']))))
    for w in warnings: print('WARNING:', w)
    for x in errors: print('ERROR:', x)
    if unclassified:
        print('UNCLASSIFIED (%d):' % len(unclassified))
        for p in unclassified: print('  ' + p)
    sys.exit(1 if (errors or unclassified) else 0)

if __name__ == '__main__':
    main()
