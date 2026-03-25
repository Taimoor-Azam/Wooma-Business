---
name: build-check
description: Run ./gradlew assembleDebug and report any errors. Use with loop for continuous build monitoring.
---

Run the debug build and report results:

```bash
cd /Users/nouman.saeed/Desktop/taimoor/Wooma-Business && ./gradlew assembleDebug
```

Parse the output and report:

**If BUILD SUCCESSFUL:**
```
✓ BUILD PASSED — assembleDebug completed successfully
  APK: app/build/outputs/apk/debug/app-debug.apk
```

**If BUILD FAILED:**
```
✗ BUILD FAILED

Errors found:
1. File: path/to/File.kt, Line X
   Error: <error message>

2. ...

Total errors: N
```

For each error:
- Show the exact file path and line number
- Show the compiler error message
- If the fix is obvious (e.g., missing import, typo, type mismatch), apply the fix immediately and re-run the build
- If the fix requires understanding broader context, describe what needs to be changed

After a successful build, confirm: "Build is clean — no errors."
