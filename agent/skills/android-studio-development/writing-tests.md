# Testing Guide for `tools/vendor/google/ml/aiplugin`

This document serves as a comprehensive guide to writing tests within the `tools/vendor/google/ml/aiplugin` codebase. It consolidates best practices, patterns, and examples for ensuring high-quality code.

## Overview

The codebase uses a mix of standard JUnit 4 tests, IntelliJ Platform tests, and Android Studio integration tests.

-   **Test Framework**: [JUnit 4](https://junit.org/junit4/) is the primary framework.
-   **Assertions**: [Google Truth](https://truth.dev/) is strongly preferred over standard JUnit assertions for better readability and failure messages.
-   **Mocking**: Mockito is widely used, but handwritten **fakes** are preferred for complex stateful interactions.
-   **Coroutines**: `kotlinx-coroutines-test` is used for testing suspending functions.

## Test Directory Structure

Tests are generally located in `src/test` directories alongside the code they test, often separated into different modules based on their dependencies.

Directory                            | Description                                                 | Key Examples
:----------------------------------- | :---------------------------------------------------------- | :-----------
`core/core-tests`                    | Tests for core plugin logic, minimal IntelliJ dependencies. | [`BotModelImplTest.kt`](file:///Users/justchan/studio-main/tools/vendor/google/ml/aiplugin/core/core-tests/src/kotlin/com/android/studio/ml/bot/BotModelImplTest.kt)
`android/src/test`                   | Tests requiring Android Studio specific classes.            | [`GeminiInStudioTest.kt`](file:///Users/justchan/studio-main/tools/vendor/google/ml/aiplugin/android/src/test/kotlin/com/android/studio/ml/GeminiInStudioTest.kt)
`mcp/client-tests`                   | Tests for Model Context Protocol client.                    | [`McpHostTest.kt`](file:///Users/justchan/studio-main/tools/vendor/google/ml/aiplugin/mcp/client-tests/src/test/kotlin/com/google/aiplugin/mcp/McpHostTest.kt)
`agents/agents-core-tests`           | Tests for the AI agents framework.                          | [`LlmAgentTest.kt`](file:///Users/justchan/studio-main/tools/vendor/google/ml/aiplugin/agents/agents-core-tests/src/test/kotlin/com/google/aiplugin/agents/LlmAgentTest.kt)
`android-plugin/integration/testSrc` | End-to-end integration tests.                               | [`GeminiChatTest.kt`](file:///Users/justchan/studio-main/tools/vendor/google/ml/aiplugin/android-plugin/integration/testSrc/com/android/studio/ml/GeminiChatTest.kt)

## Types of Tests

### 1. Pure Unit Tests

These tests do not require the IntelliJ Platform or Android Studio infrastructure. They are fast and should be preferred for logic that can be isolated.

-   **Base Class**: None, or standard JUnit 4 setup.
-   **Example**:
    [`MimeTypeUtilsTest.kt`](file:///Users/justchan/studio-main/tools/vendor/google/ml/aiplugin/model/api-tests/src/test/kotlin/com/android/tools/idea/studiobot/mimetype/MimeTypeUtilsTest.kt)

### 2. IntelliJ Platform Tests (Light Tests)

These tests require the IntelliJ Platform to be running in a "light" mode. They are used for testing components that interact with standard IDE services like the Project model, Virtual Files, or Editor.

-   **Base Class**: `com.intellij.testFramework.fixtures.BasePlatformTestCase`
-   **Key Features**: Provides `project`, `myFixture` (for manipulating
    files/editors), and `testRootDisposable`.

### 3. Integration Tests (End-to-End)

These tests run a full instance of Android Studio and can interact with the UI. They are slower and more brittle but test the entire system.

-   **Location**: `android-plugin/integration/testSrc`
-   **Example**:
    [`GeminiChatTest.kt`](file:///Users/justchan/studio-main/tools/vendor/google/ml/aiplugin/android-plugin/integration/testSrc/com/android/studio/ml/GeminiChatTest.kt)

--------------------------------------------------------------------------------

## Deep Dive: IntelliJ Platform Testing

Many components are tightly integrated with the IntelliJ Platform. Tests often need to control services, extensions, and feature flags.

### Service Replacement & Dependency Injection

For IntelliJ Platform services, prefer using `getInstance()` methods (Service Locator pattern) over constructor injection. This keeps production code simpler and aligns with platform patterns.

For testing, use `replaceService` to swap out the real service with a test double (fake or mock).

```kotlin
// Production Code
class MyComponent {
    private val myService = MyService.getInstance() // Prefer this over constructor injection
}

// Test Code
override fun setUp() {
    super.setUp()
    // Replace the real service with a fake one for the duration of the test
    ApplicationManager.getApplication()
      .replaceService(MyService::class.java, fakeMyService, testRootDisposable)
}
```

### Extension Masking

Extension points can be "masked" to provide fake implementations for the duration of the test.

```kotlin
ExtensionTestUtil.maskExtensions(
  ModelProvider.EP_NAME,
  listOf(FakeModelProvider()),
  testRootDisposable
)
```

### Controlling Feature Flags

Features gated by flags can be tested by masking the flag service with a mutable fake.

```kotlin
val fakeFlags = FakeStudioBotFlags()
ExtensionTestUtil.maskExtensions(StudioBotFlags.EP_NAME, listOf(fakeFlags), testRootDisposable)

// In a test:
fakeFlags.isRemoteModelsEnabled = false
```

--------------------------------------------------------------------------------

## Deep Dive: Fakes and Mocks

We prefer using handwritten fakes over mocking libraries like Mockito for complex interactions. Fakes provide more control and result in tests that are often clearer and easier to debug.

### Patterns for Fakes

1.  **Dedicated Fake Classes**: Reusable fakes in `test-utils` modules (e.g. `FakeGeminiApiKeyService`).
2.  **Test-Local Inner Classes**: Fakes specific to a single test file, keeping implementation close to usage.
    ```kotlin
    class FakeAgentLogger : AgentLogger {
      val calls = mutableListOf<String>() // ... overrides that record calls ...
    }
    ```
3.  **Anonymous Objects**: Simple, one-off fakes for overriding a single method.
    ```kotlin
    private val fakeModel = object : Model {
      override fun generateContent(...) = flowOf(...)
      // ... other methods can throw or be no-op ...
    }
    ```

--------------------------------------------------------------------------------

## Asynchronous Testing with Coroutines

Use `runTest` from `kotlinx-coroutines-test` for testing suspending functions. This allows for controlling virtual time and ensuring all coroutines complete.

```kotlin
@Test
fun testAsyncOperation() = runTest {
    val result = myAsyncComponent.doSomething()
    assertThat(result).isTrue()
}
```

## BUILD File Configuration

Tests are defined in `BUILD` files using `iml_module` with `test_srcs`.

**Common Test Dependencies**: `starlark test_deps = [
"//tools/adt/idea/.idea/libraries:junit4",
"//tools/adt/idea/.idea/libraries:truth",
"//tools/adt/idea/.idea/libraries:mockito",
"//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
"@intellij//:test-framework", ]`
