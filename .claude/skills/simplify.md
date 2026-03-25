---
name: simplify
description: Review recently changed code for reuse, quality, and efficiency. Fix any issues found.
---

Review the code that was just written or modified. Check for:

1. **Reuse opportunities** — Is there an existing utility, extension function, or helper that could be used instead of new code? Check `customs/` package.
2. **Duplication** — Is this logic already present elsewhere in the codebase? Consolidate if so.
3. **Efficiency** — Are there unnecessary loops, redundant API calls, or memory-inefficient operations?
4. **Kotlin idioms** — Use `?.let`, `?:`, `apply`, `also`, `run` where appropriate. Prefer `when` over if-else chains.
5. **Null safety** — No unsafe `!!` operators unless unavoidable and justified.
6. **Dead code** — Remove unused variables, imports, parameters.
7. **Readability** — Variable and function names should be clear and follow camelCase conventions.

For each issue found, fix it directly. Do not just report — edit the file.

After fixing, briefly summarize what was changed and why.
