# porting/reference

`GeneratedSyntaxNodes.cs`: the last upstream-committed output of the T4 syntax-node generator,
taken from upstream commit `f7bec4f3ab98729e03d3f40a3b83425a8330a38c` (the parent of
`cd24dcf3`, which deleted it). Blob `69076eada7b674bcfc45db8b71a5960834f8b639`.
It is byte-identical to what the generator at the pinned commit produces from
`SyntaxNodeInfos.cs` (see `porting/inventory/generators.md` §1.4). It is the structural golden
for the Java generator (class order, kinds, child names, optional sets, completion hints,
constructor parameter order). Kept here so a shallow submodule clone still has it.
