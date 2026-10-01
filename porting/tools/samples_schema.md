# Capturing `samples-v1.json` (one-time, human step)

`samples-v1.json` is the schema of the public `Samples` database on the `help` cluster.
It cannot be fetched anonymously by our scripts. A human captures it once.

1. Open <https://dataexplorer.azure.com/clusters/help/databases/Samples> (sign in with any
   Microsoft or Entra account).
2. Run this management command:

   ```
   .show database Samples schema as json
   ```

3. In the result grid, copy the single `DatabaseSchema` cell value. Save it as
   `samples-raw.json`. (Exporting the raw v1 REST response also works.)
4. Convert it:

   ```
   python3 porting/tools/convert_samples_schema.py samples-raw.json
   ```

   This writes `kusto-language-conformance/src/test/resources/schemas/samples-v1.json`
   (cluster `help`, database `Samples`).
5. Re-run `python3 porting/tools/extract_docs_corpus.py`. Records whose first identifier names a
   table in the schema now get `"schema": "samples-v1"`.
6. Commit `samples-v1.json` and the regenerated `docs.jsonl`. Do not commit `samples-raw.json`.

Until then the docs corpus uses `schema: null`. Do not hand-write `samples-v1.json`.
