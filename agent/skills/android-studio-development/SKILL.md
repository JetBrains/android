---
name: android-studio-development
description: Specialist in Android Studio plugin development. Enforces strict search scopes and guides the user through testing and build system configurations.
---
# Android Studio Development Skill
You are an expert in Android Studio plugin development. You must follow these operational rules and read the specific reference files when the context demands it.

## 1. Code Search Constraints (Strict)
**You must adhere to these search limitations to ensure performance and relevance:**
* **Subdirectories Only:** NEVER search at the root of the project (`studio-main`). It is too large.
* **Targeted Sequence:** Always search within smaller, specific directories in sequence:
  * Good candidates: e.g. `tools/vendor/google`, `tools/adt/idea`.
  * Use the context of the user's request to determine relevant submodules.

## 2. Module Management
**New Modules:** If you create a new `.iml` module file, you must strictly remind the user to update the `modules.xml` file so IntelliJ IDEA recognizes the new module.

## 3. Testing Guidelines
**When the user asks about writing, debugging, or running tests:**
* **Action:** You MUST read the file `writing-tests.md` located in this skill directory.
* **Summary:** That file contains critical instructions on:
  * Using JUnit 4 and Google Truth (preferred assertions).
  * The distinction between Pure Unit Tests, IntelliJ Platform "Light" Tests, and Integration Tests.
  * The preference for handwritten Fakes over Mockito.

## 4. Build System (Bazel & JPS)
**When the user asks about builds, dependencies, or configuration:**
* **Action:** You MUST read the file `build-system.md` located in this skill directory.
* **Summary:** `build-system.md` contains critical instructions on:
  * The hybrid JPS (`.iml`) and Bazel (`BUILD.bazel`) workflow.
  * When you should edit `.iml` files (the usual case) vs when you should edit `BUILD`/`BUILD.bazel` files.
  * Running tests via `bazel test` with correct target naming and filtering.
