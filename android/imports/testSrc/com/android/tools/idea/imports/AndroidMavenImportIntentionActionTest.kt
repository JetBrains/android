/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.imports

import com.android.testutils.TestUtils
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject.Companion.openTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.UnindexedFilesScannerExecutor
import com.intellij.psi.PsiElement
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.jetbrains.annotations.CheckReturnValue
import org.junit.After
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/** Tests for [AndroidMavenImportIntentionAction]. */
@RunsInEdt
class AndroidMavenImportIntentionActionTest {

  @get:Rule val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun unresolvedSymbolInAndroidX() {
    // Like testUnresolvedSymbolInKotlin, but in an AndroidX project (so the artifact name
    // must be mapped both in the display name and in the dependency inserted into the build.gradle
    // file)
    AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText = listOf("androidx.recyclerview:recyclerview:"),
        fileContents =
          """
          package test.pkg.imports
          val view = RecyclerView() // Here RecyclerView is an unresolvable symbol
          """
            .trimIndent(),
        caretPlacement = "RecyclerView|",
        actionText = "Add dependency on androidx.recyclerview:recyclerview and import",
        addedGradleText = listOf("implementation 'androidx.recyclerview:recyclerview:1.1.0"),
        addedImports = listOf("androidx.recyclerview.widget.RecyclerView")
      )
      .run()
  }

  @Test
  fun unresolvedTopLevelFunctionSymbolInAndroidX_kotlin() {
    AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText = listOf("androidx.camera:camera-core:"),
        fileContents =
          """
          package test.pkg.imports
          val view = cameraCoreTopLevelFunction() // Here cameraCoreTopLevelFunction is an unresolvable symbol
          """
            .trimIndent(),
        caretPlacement = "cameraCoreTopLevelFunction|",
        actionText = "Add dependency on androidx.camera:camera-core (alpha) and import",
        addedGradleText = listOf("implementation 'androidx.camera:camera-core:1.1.0-alpha03"),
        addedImports = listOf("androidx.camera.core.cameraCoreTopLevelFunction"),
      )
      .run()
  }

  @Test
  fun unresolvedTopLevelFunctionSymbolInAndroidX_java() {
    AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText = listOf("androidx.camera:camera-core:"),
        fileContents =
          """
          package test.pkg.imports;
          public class Test {
              public static void Foo() {
                  cameraCoreTopLevelFunction() // cameraCoreTopLevelFunction is an unresolvable symbol
              }
          }
          """
            .trimIndent(),
        fileExtension = "java",
        caretPlacement = "cameraCoreTopLevelFunction|",
        // Top-level Kotlin functions should not be suggested outside Kotlin files.
        available = false,
      )
      .run()
  }

  @Test
  fun doNotImportAlreadyImported() {
    // Like testUnresolvedSymbolInAndroidX, but in this case the symbol is already imported in
    // the source file; in that case, make sure we don't add an extra import. (In Java this is
    // automatically handled by our use of the ImportHandler, but in Kotlin, the normal
    // ImportHandler
    // is tricky to set up so we call the import utility directly and in that case it's up to us
    // to ensure that it's not done redundantly)
    AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText = listOf("androidx.recyclerview:recyclerview:"),
        fileContents =
          """
          package test.pkg.imports
          import androidx.recyclerview.widget.RecyclerView
          val view = RecyclerView() // Here RecyclerView is an unresolvable symbol
          """
            .trimIndent(),
        caretPlacement = "RecyclerView|()",
        actionText = "Add dependency on androidx.recyclerview:recyclerview and import",
        addedGradleText = listOf("implementation 'androidx.recyclerview:recyclerview:1.1.0"),
      )
      .run()
  }

  @Test
  fun doNotImportWhenAlreadyFullyQualifiedJava() {
    // If there is a fully qualified reference, we shouldn't import the symbol. And more
    // importantly, the unresolved symbol is typically not the final name, but the first
    // unresolvable package segment. In this case, we have to search a little harder to
    // find the real corresponding library to import.
    val baseConfig =
      AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText = listOf("androidx.recyclerview:recyclerview:"),
        fileContents =
          """
          package test.pkg.imports;
          public class Test {
              private androidx.recyclerview.widget.RecyclerView view;
          }
          """
            .trimIndent(),
        fileExtension = "java",
        caretPlacement = "recyc|lerview",
        actionText = "Add dependency on androidx.recyclerview:recyclerview and import",
        addedGradleText = listOf("implementation 'androidx.recyclerview:recyclerview:1.1.0"),
      )

    val caretPlacements =
      listOf("andro|idx", "recyc|lerview", "wid|get", "Recycler|View", "RecyclerView|")

    for (caretPlacement in caretPlacements) {
      baseConfig.copy(caretPlacement = caretPlacement).run()
    }
  }

  @Test
  fun doNotImportWhenAlreadyFullyQualifiedJava_nestedClass() {
    // If there is a fully qualified reference, we shouldn't import the symbol. And more
    // importantly, the unresolved symbol is typically not the final name, but the first
    // unresolvable package segment. In this case, we have to search a little harder to
    // find the real corresponding library to import.
    AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText = listOf("androidx.recyclerview:recyclerview:"),
        fileContents =
          """
          package test.pkg.imports;
          public class Test {
              private androidx.recyclerview.widget.RecyclerView.FakeNestedClass view; // recyclerview is an unresolvable symbol
          }
          """
            .trimIndent(),
        fileExtension = "java",
        caretPlacement = "recyc|lerview",
        actionText = "Add dependency on androidx.recyclerview:recyclerview and import",
        addedGradleText = listOf("implementation 'androidx.recyclerview:recyclerview:1.1.0"),
      )
      .run()
  }

  @Test
  fun doNotImportWhenAlreadyFullyQualifiedKotlin_dotQualifiedExpressionCase() {
    // Like testDoNotImportWhenAlreadyFullyQualifiedJava, but for Kotlin
    // Like testUnresolvedSymbolInKotlin, but in an AndroidX project (so the artifact name
    // must be mapped both in the display name and in the dependency inserted into the build.gradle
    // file)
    val baseConfig =
      AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText = listOf("androidx.recyclerview:recyclerview:"),
        fileContents =
          """
          package test.pkg.imports
          val view = androidx.recyclerview.widget.RecyclerView() // recyclerview is an unresolvable symbol
          """
            .trimIndent(),
        actionText = "Add dependency on androidx.recyclerview:recyclerview and import",
        addedGradleText = listOf("implementation 'androidx.recyclerview:recyclerview:1.1.0"),
      )

    val caretPlacements =
      listOf("andro|idx", "recyc|lerview", "wid|get", "Recycler|View()", "RecyclerView()|")
    for (caretPlacement in caretPlacements) {
      baseConfig.copy(caretPlacement = caretPlacement).run()
    }
  }

  @Test
  fun extensionFunction_literalReceiver() {
    val baseConfig =
      AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText = listOf("my.madeup.package:amazing-package:"),
        caretPlacement = "extension|Function",
        actionText = "Add dependency on my.madeup.pkg:amazing-pkg and import",
        addedGradleText = listOf("implementation 'my.madeup.pkg:amazing-pkg:4.2.0"),
        addedImports = listOf("my.madeup.pkg.amazing.extensionFunction"),
      )

    val withParens =
      baseConfig.copy(
        fileContents =
          """
          package test.pkg.imports
          val v = "foobar".extensionFunction() // Space for caret
          """
            .trimIndent(),
      )

    val withoutParens =
      baseConfig.copy(
        fileContents =
          """
          package test.pkg.imports
          val v = "foobar".extensionFunction // Space for caret
          """
            .trimIndent(),
      )

    withParens.run()

    withoutParens.run()

    withParens.copy(caretPlacement = "extensionFunction()|").run()

    withoutParens.copy(caretPlacement = "extensionFunction|").run()
  }

  @Test
  fun extensionFunction_variableReceiver() {
    val baseConfig =
      AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText = listOf("my.madeup.package:amazing-package:"),
        caretPlacement = "extension|Function",
        actionText = "Add dependency on my.madeup.pkg:amazing-pkg and import",
        addedGradleText = listOf("implementation 'my.madeup.pkg:amazing-pkg:4.2.0"),
        addedImports = listOf("my.madeup.pkg.amazing.extensionFunction"),
      )

    val withParens =
      baseConfig.copy(
        fileContents =
          """
          package test.pkg.imports
          val s = "foobar"
          val v = s.extensionFunction() // Space for caret
          """
            .trimIndent(),
      )

    val withoutParens =
      baseConfig.copy(
        fileContents =
          """
          package test.pkg.imports
          val s = "foobar"
          val v = s.extensionFunction // Space for caret
          """
            .trimIndent(),
      )

    withParens.run()

    withoutParens.run()

    withParens.copy(caretPlacement = "extensionFunction()|").run()

    withoutParens.copy(caretPlacement = "extensionFunction|").run()
  }

  @Test
  fun extensionFunction_inImport() {
    val baseConfig =
      AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText = listOf("my.madeup.package:amazing-package:"),
        fileContents =
          """
          package test.pkg.imports
          import my.madeup.pkg.amazing.extensionFunction // extra space for caret
          """
            .trimIndent(),
        actionText = "Add dependency on my.madeup.pkg:amazing-pkg and import",
        addedGradleText = listOf("implementation 'my.madeup.pkg:amazing-pkg:4.2.0"),
      )

    val caretPlacements =
      listOf(
        "|my.madeup.pkg.amazing.extensionFunction",
        "m|y.madeup.pkg.amazing.extensionFunction",
        "my|.madeup.pkg.amazing.extensionFunction",
        "my.mad|eup.pkg.amazing.extensionFunction",
        "my.madeup|.pkg.amazing.extensionFunction",
        "my.madeup.pk|g.amazing.extensionFunction",
        "my.madeup.pkg|.amazing.extensionFunction",
        "my.madeup.pkg.ama|zing.extensionFunction",
        "my.madeup.pkg.amazing|.extensionFunction",
        "my.madeup.pkg.amazing.extension|Function",
        "my.madeup.pkg.amazing.extensionFunction|",
      )

    for (caretPlacement in caretPlacements) {
      baseConfig.copy(caretPlacement = caretPlacement).run()
    }
  }

  @Test
  fun doNotImportWhenAlreadyFQKotlin_dotQualifiedExpressionCase_nestedClass() {
    // Like testDoNotImportWhenAlreadyFullyQualifiedJava, but for Kotlin
    // Like testUnresolvedSymbolInKotlin, but in an AndroidX project (so the artifact name
    // must be mapped both in the display name and in the dependency inserted into the build.gradle
    // file)
    AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText = listOf("androidx.recyclerview:recyclerview:"),
        fileContents =
          """
          package test.pkg.imports
          val view = androidx.recyclerview.widget.RecyclerView.FakeNestedClass() // "recyclerview" is an unresolvable symbol
          """
            .trimIndent(),
        caretPlacement = "recyc|lerview",
        actionText = "Add dependency on androidx.recyclerview:recyclerview and import",
        addedGradleText = listOf("implementation 'androidx.recyclerview:recyclerview:1.1.0"),
      )
      .run()
  }

  @Test
  fun testKtx() {
    // Make sure that if we import a symbol from Kotlin and a ktx library is available, we pick it
    AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText = listOf("androidx.palette:palette-ktx:"),
        fileContents =
          """
          package test.pkg.imports
          val palette = Palette() // "Palette" is an unresolvable symbol
          """
            .trimIndent(),
        caretPlacement = "Palette|",
        actionText = "Add dependency on androidx.palette:palette-ktx and import",
        addedGradleText = listOf("implementation 'androidx.palette:palette-ktx:1.0.0"),
        addedImports = listOf("androidx.palette.graphics.Palette"),
      )
      .run()
  }

  @Test
  fun testNotKtx() {
    // Make sure that if we import a symbol from Java and a ktx library is available, we don't pick
    // the ktx version
    AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText = listOf("androidx.palette:palette:"),
        fileContents =
          """
          package test.pkg.imports;
          public class Test {
              private Palette palette;
          }
          """
            .trimIndent(),
        fileExtension = "java",
        caretPlacement = "Palette|",
        actionText = "Add dependency on androidx.palette:palette and import",
        addedGradleText = listOf("implementation 'androidx.palette:palette:1.0.0"),
        addedImports = listOf("androidx.palette.graphics.Palette"),
      )
      .run()
  }

  @Test
  fun testAnnotationProcessor() {
    // Ensure that if an annotation processor is available, we also add it
    AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText = listOf("androidx.room:room-runtime:"),
        fileContents =
          """
          package test.pkg.imports;
          public class Test {
              private RoomDatabase database;
          }
          """
            .trimIndent(),
        fileExtension = "java",
        caretPlacement = "Room|Database",
        actionText = "Add dependency on androidx.room:room-runtime and import",
        addedGradleText =
          listOf(
            "implementation 'androidx.room:room-runtime:2.2.6",
            "annotationProcessor 'androidx.room:room-compiler:2.2.6",
          ),
        addedImports = listOf("androidx.room.RoomDatabase"),
      )
      .run()
  }

  @Test
  fun testProvideExtraArtifacts() {
    // Ensure that if extra artifacts are needed, we also add them.
    AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText =
          listOf(
            "androidx.compose.ui:ui-tooling-preview:",
            "androidx.compose.ui:ui-tooling:",
          ),
        fileContents =
          """
          package test.pkg.imports;
          public class Test {
              @Preview
              @Composable
              fun Foo()
          }
          """
            .trimIndent(),
        fileExtension = "java",
        caretPlacement = "@Previe|w",
        actionText = "Add dependency on androidx.compose.ui:ui-tooling-preview and import",
        addedGradleText =
          listOf(
            "implementation 'androidx.compose.ui:ui-tooling-preview:1.0.5",
            "debugImplementation 'androidx.compose.ui:ui-tooling:1.0.5",
          ),
        addedImports = listOf("androidx.compose.ui.tooling.preview.Preview"),
      )
      .run()
  }

  @Test
  fun testUnresolvedSymbol_nonAndroidX() {
    // Like testUnresolvedSymbolInKotlin but in a Java file
    AndroidMavenImportIntentionActionTestConfig(
        testProject = AndroidCoreTestProject.MIGRATE_TO_APP_COMPAT,
        projectRule = projectRule,
        forbiddenGradleText = listOf("com.android.support:recyclerview-v7:"),
        fileContents =
          """
          package test.pkg.imports;
          public class Test {
              private RecyclerView view;
          }
          """
            .trimIndent(),
        fileExtension = "java",
        caretPlacement = "RecyclerView|",
        available = false,
      )
      .run()
  }

  @Test
  fun doNotSuggestIfAnyIsAlreadyDepended() {
    AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText =
          listOf(
            "androidx.palette:palette-ktx:",
            "androidx.room:room-runtime:",
          ),
        fileContents =
          """
          package test.pkg.imports
          val someClass = FakeClass() // "FakeClass" is an unresolvable symbol
          """
            .trimIndent(),
        caretPlacement = "FakeClass|()",
        // Since we have more than one suggestion, we just show general text `Add library
        // dependency` here.
        actionText = "Add library dependency and import",
        // We actually need to sync to check that it is no longer suggested
        syncAfterAction = true,
        // The deterministic order of suggestions are ensured, so the first option
        // `androidx.palette:palette` is applied.
        addedGradleText = listOf("implementation 'androidx.palette:palette-ktx:1.0.0"),
        addedImports = listOf("androidx.palette.graphics.FakeClass")
      )
      .runAndThen { action ->
        // Since we have added on `androidx.palette:palette`, no dependencies are to be suggested
        // anymore.
        val element = fixture.moveCaret("FakeClass|()")
        assertThat(action.isAvailable(project, fixture.editor, element)).isFalse()
      }
  }

  @Test
  fun doNotImportWhenAlreadyFullyQualifiedKotlin_userTypeCase() {
    // Like testDoNotImportWhenAlreadyFullyQualifiedJava, but for Kotlin
    // Like testUnresolvedSymbolInKotlin, but in an AndroidX project (so the artifact name
    // must be mapped both in the display name and in the dependency inserted into the build.gradle
    // file)
    AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText = listOf("androidx.camera:camera-core:"),
        fileContents =
          """
          package test.pkg.imports
          val builder = object : androidx.camera.core.ExtendableBuilder { // "camera" is an unresolvable symbol
          """
            .trimIndent(),
        caretPlacement = "came|ra",
        // Since we have more than one suggestion, we just show general text `Add library
        // dependency` here.
        actionText = "Add dependency on androidx.camera:camera-core (alpha) and import",
        // The deterministic order of suggestions are ensured, so the first option
        // `androidx.palette:palette` is applied.
        addedGradleText = listOf("implementation 'androidx.camera:camera-core:1.1.0-alpha03"),
      )
      .run()
  }

  @Test
  fun doNotImportWhenAlreadyFullyQualifiedKotlin_userTypeCase_nestedClass() {
    // Like testDoNotImportWhenAlreadyFullyQualifiedJava, but for Kotlin
    // Like testUnresolvedSymbolInKotlin, but in an AndroidX project (so the artifact name
    // must be mapped both in the display name and in the dependency inserted into the build.gradle
    // file)
    AndroidMavenImportIntentionActionTestConfig(
        projectRule = projectRule,
        forbiddenGradleText = listOf("androidx.camera:camera-core:"),
        fileContents =
          """
          package test.pkg.imports
          val callback = object : androidx.camera.core.ImageCapture.OnImageSavedCallback { // "camera" is an unresolvable symbol
          """
            .trimIndent(),
        caretPlacement = "came|ra",
        actionText = "Add dependency on androidx.camera:camera-core (alpha) and import",
        addedGradleText = listOf("implementation 'androidx.camera:camera-core:1.1.0-alpha03"),
      )
      .run()
  }

  data class AndroidMavenImportIntentionActionTestConfig
  @CheckReturnValue
  constructor(
    val projectRule: IntegrationTestEnvironmentRule,
    val testProject: AndroidCoreTestProject = AndroidCoreTestProject.ANDROIDX_SIMPLE,
    val forbiddenGradleText: Collection<String> = listOf(),
    val fileContents: String = "",
    val fileExtension: String = "kt",
    val caretPlacement: String = "",
    val available: Boolean = true,
    val syncAfterAction: Boolean = false,
    val actionText: String? = null,
    val addedGradleText: Collection<String> = listOf(),
    val addedImports: Collection<String> = listOf(),
  ) {
    private fun <T> openTestProject(
      testProject: TestProjectDefinition,
      body: PreparedTestProject.Context.(Project) -> T
    ) {
      return projectRule.openTestProject(testProject) {
        ApplicationManager.getApplication()
          .replaceService(
            MavenClassRegistryManager::class.java,
            fakeMavenClassRegistryManager,
            fixture.testRootDisposable
          )
        body(project)
      }
    }

    fun run() {
      runAndThen {}
    }

    fun runAndThen(
      andThen: (PreparedTestProject.Context.(AndroidMavenImportIntentionAction) -> Unit)
    ) {
      openTestProject(testProject) {
        for (forbidden in forbiddenGradleText) {
          assertBuildGradle(project) { !it.contains(forbidden) }
        }
        if (fileContents.isNotEmpty()) {
          fixture.loadNewFile(
            "app/src/main/java/test/pkg/imports/MainActivity2.$fileExtension",
            fileContents
          )
        }
        val source = fixture.editor.document.text

        val action = AndroidMavenImportIntentionAction()
        val element: PsiElement
        try {
          element = fixture.moveCaret(caretPlacement)
        } catch (e: NullPointerException) {
          fail("Failed to move caret to position: $caretPlacement")
          throw e
        }
        assertWithMessage("Action availability not correct.")
          .that(action.isAvailable(project, fixture.editor, element))
          .isEqualTo(available)
        if (!available) return@openTestProject

        assertThat(action.text).isEqualTo(actionText)
        when {
          syncAfterAction ->
            performAndWaitForSyncEnd { action.perform(project, fixture.editor, element, true) }
          // Note: We do perform, not performAndSync here, since in some cases androidx libraries
          // aren't available
          else -> performWithoutSync(action, element)
        }
        for (added in addedGradleText) {
          assertBuildGradle(project) { it.contains(added) }
        }

        val newSource = fixture.editor.document.text
        if (addedImports.isEmpty()) {
          assertThat(newSource).isEqualTo(source)
        } else {
          val diff = TestUtils.getDiff(source, newSource, 1).trim()

          val removedLines = diff.lines().filter { it.startsWith("- ") }
          assertWithMessage("Action should not remove lines.").that(removedLines).isEmpty()

          val addedLines =
            diff
              .lines()
              .filter { it.startsWith("+ ") }
              .map { it.removePrefix("+ ") }
              .filter(String::isNotBlank)
          val (addedImportLines, otherAddedLines) = addedLines.partition { it.startsWith("import ") }
          assertWithMessage("Unexpected lines added to file.").that(otherAddedLines).isEmpty()

          val importedSymbols =
            addedImportLines
              .map { it.trim().removePrefix("import ").removeSuffix(";") }
              .filter(String::isNotBlank)
          assertWithMessage("List of added imports is incorrect.")
            .that(importedSymbols)
            .containsExactlyElementsIn(addedImports)
        }
        // Run whatever is left
        andThen(action)

        UnindexedFilesScannerExecutor.getInstance(project).cancelAllTasksAndWait()
      }
    }
  }
}
