This project uses JPS (IntelliJ's built-in build system) and Bazel.

Bazel is configured by the BUILD.bazel files and JPS is configured by the .iml files.

The Bazel command `bazel` is located in `tools/base/bazel/` under the project root, with the full relative path to the command at `tools/base/bazel/bazel`.

The user is using IntelliJ to develop and test this project so you should only edit the .iml files and then request the user to run / test. The user will then ask you to run the iml_to_build script to update the Bazel files once ready. You should never touch the BUILD.bazel files unless explicitly instructed to by the user.

> **Note:** When running Bazel tests, the test targets always end in `_tests`
> (e.g.,
> `//tools/vendor/google/ml/aiplugin/mcp/client-tests:aiplugin.mcp.client-tests_tests`).

## Running Tests with Bazel

To run tests using Bazel, use the `bazel test` command. You can run all tests in a target or filter specific tests.

### Running All Tests in a Target

```bash
bazel test //path/to/package:target_name_tests
```

### Running Specific Tests with `--test_filter`

Use the `--test_filter` flag to run only specific test classes or methods.

```bash
bazel test //path/to/package:target_name_tests --test_filter=TestClassName
```

Example: `bash bazel test
//tools/vendor/google/ml/aiplugin/mcp/client-tests:aiplugin.mcp.client-tests_tests
--test_filter=McpServerRegistryTest`

### Running iml_to_build

Run iml_to_build with the following command.

```bash
bazel run //tools/base/bazel:iml_to_build
```

Here is a guide for manipulating IntelliJ IDEA module dependencies by directly editing the `.iml` files for your reference:

--------------------------------------------------------------------------------

## 🤖 Guide: Manipulating IntelliJ Module Dependencies (`.iml` File)

This guide outlines how to programmatically manage module dependencies by directly modifying the module's `.iml` file (which is XML).

> **Warning:** This method applies **only** to native IntelliJ IDEA projects. If the project is managed by Maven (`pom.xml`) or Gradle (`build.gradle`/`build.gradle.kts`), you **must** modify the build file instead. Any direct changes to the `.iml` file will be overwritten on the next build script sync.

--------------------------------------------------------------------------------

## Core `.iml` Structure

All module dependencies are defined within the `<component name="NewModuleRootManager">` tag. Each dependency is represented by its own `<orderEntry>` element.

*   **To Add a Dependency:** Insert a new `<orderEntry>` element inside the `<component>` tag.
*   **To Remove a Dependency:** Delete the corresponding `<orderEntry>` element.

--------------------------------------------------------------------------------

## Dependency Types (`<orderEntry>`)

The `type` attribute defines what you are depending on.

### 1\. Module Dependency

This links one module to another module within the same project.

```xml
<orderEntry type="module" module-name="name-of-other-module" />
```

### 2\. Library (Project, Application, or Module)

This links to a pre-defined library. The `level` attribute is common.

```xml
<orderEntry type="library" name="my-project-library" level="project" />

<orderEntry type="library" name="my-global-library" level="application" />
```

### 3\. Module-Specific Library (JARs)

This defines a library used only by this module. It's common for local `.jar` files.

```xml
<orderEntry type="module-library">
  <library>
    <CLASSES>
      <root url="jar://$MODULE_DIR$/lib/some-local.jar!/" />
    </CLASSES>
    <JAVADOC />
    <SOURCES>
      <root url="jar://$MODULE_DIR$/lib/some-local-sources.jar!/" />
    </SOURCES>
  </library>
</orderEntry>
```

### 4\. SDK (JDK)

This specifies the Java SDK (or other SDK) for the module.

```xml
<orderEntry type="jdk" jdkName="17" jdkType="JavaSDK" />
```

--------------------------------------------------------------------------------

## Dependency Configuration

Attributes on the `<orderEntry>` tag control its scope and visibility.

### Dependency Scope

The `scope` attribute controls the dependency's availability on the classpath. If omitted, the default is **COMPILE**.

*   **`scope="COMPILE"`**: (Default) Available for main source compilation, testing, and runtime.
*   **`scope="TEST"`**: Available only for compiling and running tests.
*   **`scope="RUNTIME"`**: Available only at runtime (for main and test).
*   **`scope="PROVIDED"`**: Available for main and test compilation, but *not* included at runtime (assumed to be provided by the container).

**Example:**

```xml
<orderEntry type="library" name="junit-4.13" level="project" scope="TEST" />
```

### Exporting a Dependency

To make a dependency transitive (i.e., available to other modules that depend on *this* module), add the `exported=""` attribute. Its presence (even empty) signifies `true`.

**Example:**

```xml
<orderEntry type="module" module-name="my-api-module" exported="" />
```

--------------------------------------------------------------------------------

## 💡 IntelliJ-Specific Test Dependency Logic

IntelliJ IDEA processes dependencies for test sources differently from other build tools (for example, Gradle and Maven).

If your module (say, module A) depends on another module (module B), IntelliJ IDEA assumes that the test sources in A depend not only on the sources in B but also on its own test sources. Consequently, the test sources of B are also included in the corresponding classpaths.

### Dependency Scope Classpath Summary

The following table summarizes the classpath information for the possible dependency scopes.

| Scope        | Sources, when | Sources, when | Tests, when | Tests, when run |
:              : compiled      : run           : compiled    :                 :
| :----------- | :-----------: | :-----------: | :---------: | :-------------: |
| **Compile**  | +             | +             | +           | +               |
| **Test**     | -             | -             | +           | +               |
| **Runtime**  | -             | +             | -           | +               |
| **Provided** | +             | -             | +           | +               |

--------------------------------------------------------------------------------

## Dependency Order

The order of dependencies is important. The classpath is built based on the **physical order of the `<orderEntry>` tags** as they appear in the `.iml` file. To reorder dependencies, simply move the corresponding XML elements up or down within the `<component name="NewModuleRootManager">` block.
