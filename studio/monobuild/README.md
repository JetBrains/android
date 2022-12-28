monobuild: a combined Studio/IntelliJ source build
===

Usage
---
Run `./generate.py` and then open this directory in IntelliJ.

From there you can launch the Android Studio run configuration as usual.

Tips
---
* See the [IntelliJ README](https://github.com/JetBrains/intellij-community/blob/master/README.md)
  for IDE build configuration tips.

* If you change the project structure (e.g. module dependencies) of either Studio or IntelliJ, then
  you will need to re-run `generate.py` for those changes to take effect in the monobuild.

* The `generate.py` script is expected to break occasionally, such as when there are new conflicts
  between Studio/IntelliJ configuration files. Please spend a few minutes debugging to see if it
  is an easy fix. Otherwise, file a bug.

* If you are running `generate.py` frequently, but not changing the IntelliJ project structure,
  then you can use `--recycle-intellij-source-map` to speed up iteration times.

* To test code changes in `generate.py`, you should diff the generated output against previous runs.
  You can utilize `git diff --no-index <before> <after>` for this.

* You can use the "Load/Unload Modules" action in the IDE to exclude any broken modules
  (assuming these modules are not needed by the run configurations you care about).
