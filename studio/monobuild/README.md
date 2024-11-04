monobuild: a combined Studio/IntelliJ source build
===

The monobuild combines the IntelliJ Community project and the Android Studio project
into a single combined IDE build. This can be helpful when working on features that
span both projects (since it quickens the edit/compile/debug cycle significantly).

Usage
---
Run `./generate.py` and then open this directory in IntelliJ.

From there you can launch the Android Studio run configuration as usual.

Limitations
---

* If you change the project structure (e.g. module dependencies) of Studio code, then
  you will need to re-run `generate.py` for those changes to take effect in the monobuild.

* If you change the project structure of IntelliJ Platform, then you will need to run `generate.py`
  with flag `--rebuild-intellij-module-descriptors` (or update intellij-sdk prebuilts).

* The `generate.py` script is expected to break occasionally, such as when there are new conflicts
  between Studio/IntelliJ configuration files. Please spend a few minutes debugging to see if it
  is an easy fix. Otherwise, file a bug.

Tips
---
* See the [IntelliJ README](https://github.com/JetBrains/intellij-community/blob/master/README.md)
  for IDE build configuration tips.

* To test code changes in `generate.py`, you should diff the generated output against previous runs.
  You can utilize `git diff --no-index <before> <after>` for this.

* You can use the "Load/Unload Modules" action in the IDE to exclude any broken modules
  (assuming these modules are not needed by the run configurations you care about).
