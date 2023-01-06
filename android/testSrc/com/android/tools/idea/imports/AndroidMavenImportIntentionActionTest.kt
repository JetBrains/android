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
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.junit.Rule
import org.junit.Test


/**
 * Tests for [AndroidMavenImportIntentionAction].
 */
@RunsInEdt
class AndroidMavenImportIntentionActionTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  private fun <T> openTestProject(testProject: TestProjectDefinition, body: PreparedTestProject.Context.(Project) -> T) {
    return projectRule.openTestProject(testProject) {
      ApplicationManager.getApplication().replaceService(
        MavenClassRegistryManager::class.java,
        fakeMavenClassRegistryManager,
        fixture.testRootDisposable
      )
      body(project)
    }
  }
  
  @Test
  fun unresolvedSymbolInAndroidX() {
    // Like testUnresolvedSymbolInKotlin, but in an AndroidX project (so the artifact name
    // must be mapped both in the display name and in the dependency inserted into the build.gradle file)
    openTestProject(AndroidCoreTestProject.ANDROIDX_SIMPLE) {  // this project uses AndroidX
      assertBuildGradle(project) { !it.contains("androidx.recyclerview:recyclerview:") }
      fixture.loadNewFile(
        "app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val view = RecyclerView() // Here RecyclerView is an unresolvable symbol
      """.trimIndent()
      )
      val source = fixture.editor.document.text

      val action = AndroidMavenImportIntentionAction()
      val element = fixture.moveCaret("RecyclerView|")
      val available = action.isAvailable(project, fixture.editor, element)
      assertThat(available).isTrue()
      assertThat(action.text).isEqualTo("Add dependency on androidx.recyclerview:recyclerview and import")
      // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
      // in the test prebuilts right now
      performWithoutSync(action, element)

      assertBuildGradle(project) { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

      // Make sure we've imported the RecyclerView correctly as well, including transforming to AndroidX package name
      val newSource = fixture.editor.document.text
      val diff = TestUtils.getDiff(source, newSource, 1)
      assertThat(diff.trim()).isEqualTo(
        """
      @@ -2 +2
        package test.pkg.imports
      +
      + import androidx.recyclerview.widget.RecyclerView
      +
        val view = RecyclerView() // Here RecyclerView is an unresolvable symbol
      """.trimIndent().trim()
      )
    }
  }

  @Test
  fun doNotImportAlreadyImported() {
    // Like testUnresolvedSymbolInAndroidX, but in this case the symbol is already imported in
    // the source file; in that case, make sure we don't add an extra import. (In Java this is
    // automatically handled by our use of the ImportHandler, but in Kotlin, the normal ImportHandler
    // is tricky to set up so we call the import utility directly and in that case it's up to us
    // to ensure that it's not done redundantly)
    openTestProject(AndroidCoreTestProject.ANDROIDX_SIMPLE) { // this project uses AndroidX
      assertBuildGradle(project) { !it.contains("androidx.recyclerview:recyclerview:") }
      fixture.loadNewFile(
        "app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      import androidx.recyclerview.widget.RecyclerView
      val view = RecyclerView() // Here RecyclerView is an unresolvable symbol
      """.trimIndent()
      )
      val source = fixture.editor.document.text

      val action = AndroidMavenImportIntentionAction()
      val element = fixture.moveCaret("RecyclerView|()")
      val available = action.isAvailable(project, fixture.editor, element)
      assertThat(available).isTrue()
      assertThat(action.text).isEqualTo("Add dependency on androidx.recyclerview:recyclerview and import")
      // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
      // in the test prebuilts right now
      performWithoutSync(action, element)

      assertBuildGradle(project) { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

      // Make sure we've haven't added a second import statement; the source code should not have changed
      val newSource = fixture.editor.document.text
      assertThat(source).isEqualTo(newSource)
    }
  }

  @Test
  fun doNotImportWhenAlreadyFullyQualifiedJava() {
    // If there is a fully qualified reference, we shouldn't import the symbol. And more
    // importantly, the unresolved symbol is typically not the final name, but the first
    // unresolvable package segment. In this case, we have to search a little harder to
    // find the real corresponding library to import.
    openTestProject(AndroidCoreTestProject.ANDROIDX_SIMPLE) { // this project uses AndroidX
      assertBuildGradle(project) { !it.contains("androidx.recyclerview:recyclerview:") }
      fixture.loadNewFile(
        "app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private androidx.recyclerview.widget.RecyclerView view;
      }
      """.trimIndent()
      )
      val source = fixture.editor.document.text

      val action = AndroidMavenImportIntentionAction()
      val element = fixture.moveCaret("recyc|lerview")
      val available = action.isAvailable(project, fixture.editor, element)
      assertThat(available).isTrue()
      assertThat(action.text).isEqualTo("Add dependency on androidx.recyclerview:recyclerview and import")
      // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
      // in the test prebuilts right now
      performWithoutSync(action, element)

      assertBuildGradle(project) { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

      // Make sure we haven't modified the source to add a new import statement since the
      // reference is already fully qualified
      val newSource = fixture.editor.document.text
      assertThat(source).isEqualTo(newSource)
    }
  }

  @Test
  fun doNotImportWhenAlreadyFullyQualifiedJava_nestedClass() {
    // If there is a fully qualified reference, we shouldn't import the symbol. And more
    // importantly, the unresolved symbol is typically not the final name, but the first
    // unresolvable package segment. In this case, we have to search a little harder to
    // find the real corresponding library to import.
    openTestProject(AndroidCoreTestProject.ANDROIDX_SIMPLE) { // this project uses AndroidX
      assertBuildGradle(project) { !it.contains("androidx.recyclerview:recyclerview:") }
      fixture.loadNewFile(
        "app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private androidx.recyclerview.widget.RecyclerView.FakeNestedClass view; // recyclerview(package segment) is an unresolvable symbol
      }
      """.trimIndent()
      )
      val source = fixture.editor.document.text

      val action = AndroidMavenImportIntentionAction()
      val element = fixture.moveCaret("recyc|lerview")
      val available = action.isAvailable(project, fixture.editor, element)
      assertThat(available).isTrue()
      assertThat(action.text).isEqualTo("Add dependency on androidx.recyclerview:recyclerview and import")
      // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
      // in the test prebuilts right now
      performWithoutSync(action, element)

      assertBuildGradle(project) { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

      // Make sure we haven't modified the source to add a new import statement since the
      // reference is already fully qualified
      val newSource = fixture.editor.document.text
      assertThat(source).isEqualTo(newSource)
    }
  }

  @Test
  fun doNotImportWhenAlreadyFullyQualifiedKotlin_dotQualifiedExpressionCase() {
    // Like testDoNotImportWhenAlreadyFullyQualifiedJava, but for Kotlin
    // Like testUnresolvedSymbolInKotlin, but in an AndroidX project (so the artifact name
    // must be mapped both in the display name and in the dependency inserted into the build.gradle file)
    openTestProject(AndroidCoreTestProject.ANDROIDX_SIMPLE) { // this project uses AndroidX
      assertBuildGradle(project) { !it.contains("androidx.recyclerview:recyclerview:") }
      fixture.loadNewFile(
        "app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val view = androidx.recyclerview.widget.RecyclerView() // Here recyclerview(package segment) is an unresolvable symbol
      """.trimIndent()
      )
      val source = fixture.editor.document.text

      val action = AndroidMavenImportIntentionAction()
      val element = fixture.moveCaret("recyc|lerview")
      val available = action.isAvailable(project, fixture.editor, element)
      assertThat(available).isTrue()
      assertThat(action.text).isEqualTo("Add dependency on androidx.recyclerview:recyclerview and import")
      // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
      // in the test prebuilts right now
      performWithoutSync(action, element)

      assertBuildGradle(project) { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

      // Make sure we haven't added an import statement since the reference is already fully qualified
      val newSource = fixture.editor.document.text
      assertThat(source).isEqualTo(newSource)
    }
  }

  @Test
  fun doNotImportWhenAlreadyFQKotlin_dotQualifiedExpressionCase_nestedClass() {
    // Like testDoNotImportWhenAlreadyFullyQualifiedJava, but for Kotlin
    // Like testUnresolvedSymbolInKotlin, but in an AndroidX project (so the artifact name
    // must be mapped both in the display name and in the dependency inserted into the build.gradle file)
    openTestProject(AndroidCoreTestProject.ANDROIDX_SIMPLE) { // this project uses AndroidX
      assertBuildGradle(project) { !it.contains("androidx.recyclerview:recyclerview:") }
      fixture.loadNewFile(
        "app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val view = androidx.recyclerview.widget.RecyclerView.FakeNestedClass() // Here recyclerview(package segment) is an unresolvable symbol
      """.trimIndent()
      )
      val source = fixture.editor.document.text

      val action = AndroidMavenImportIntentionAction()
      val element = fixture.moveCaret("recyc|lerview")
      val available = action.isAvailable(project, fixture.editor, element)
      assertThat(available).isTrue()
      assertThat(action.text).isEqualTo("Add dependency on androidx.recyclerview:recyclerview and import")
      // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
      // in the test prebuilts right now
      performWithoutSync(action, element)

      assertBuildGradle(project) { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

      // Make sure we haven't added an import statement since the reference is already fully qualified
      val newSource = fixture.editor.document.text
      assertThat(source).isEqualTo(newSource)
    }
  }

  @Test
  fun testKtx() {
    // Make sure that if we import a symbol from Kotlin and a ktx library is available, we pick it
    openTestProject(AndroidCoreTestProject.ANDROIDX_SIMPLE) { // this project uses AndroidX
      assertBuildGradle(project) { !it.contains("androidx.palette:palette-ktx:") }
      fixture.loadNewFile(
        "app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val palette = Palette() // Here "Palette" is an unresolvable symbol
      """.trimIndent()
      )
      val source = fixture.editor.document.text

      val action = AndroidMavenImportIntentionAction()
      val element = fixture.moveCaret("Palette|")
      val available = action.isAvailable(project, fixture.editor, element)
      assertThat(available).isTrue()
      assertThat(action.text).isEqualTo("Add dependency on androidx.palette:palette-ktx and import")
      // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
      // in the test prebuilts right now
      performWithoutSync(action, element)

      assertBuildGradle(project) { it.contains("implementation 'androidx.palette:palette-ktx:") }

      // Make sure we've imported the RecyclerView correctly as well
      val newSource = fixture.editor.document.text
      val diff = TestUtils.getDiff(source, newSource, 1)
      assertThat(diff.trim()).isEqualTo(
        """
      @@ -2 +2
        package test.pkg.imports
      +
      + import androidx.palette.graphics.Palette
      +
        val palette = Palette() // Here "Palette" is an unresolvable symbol
      """.trimIndent().trim()
      )
    }
  }

  @Test
  fun testNotKtx() {
    // Make sure that if we import a symbol from Java and a ktx library is available, we don't pick the ktx version
    openTestProject(AndroidCoreTestProject.ANDROIDX_SIMPLE) { // this project uses AndroidX
      assertBuildGradle(project) { !it.contains("androidx.palette:palette:") }
      fixture.loadNewFile(
        "app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private Palette palette;
      }
      """.trimIndent()
      )

      val action = AndroidMavenImportIntentionAction()
      val element = fixture.moveCaret("Palette|")
      val available = action.isAvailable(project, fixture.editor, element)
      assertThat(available)
      assertThat(action.text).isEqualTo("Add dependency on androidx.palette:palette and import")
      // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
      // in the test prebuilts right now
      performWithoutSync(action, element)

      assertBuildGradle(project) { it.contains("implementation 'androidx.palette:palette:") }
    }
  }

  @Test
  fun testAnnotationProcessor() {
    // Ensure that if an annotation processor is available, we also add it
    openTestProject(AndroidCoreTestProject.ANDROIDX_SIMPLE) { // this project uses AndroidX
      assertBuildGradle(project) { !it.contains("androidx.room:room-runtime:") }
      fixture.loadNewFile(
        "app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private RoomDatabase database;
      }
      """.trimIndent()
      )

      val action = AndroidMavenImportIntentionAction()
      val element = fixture.moveCaret("Room|Database")
      val available = action.isAvailable(project, fixture.editor, element)
      assertThat(available).isTrue()
      assertThat(action.text).isEqualTo("Add dependency on androidx.room:room-runtime and import")
      // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
      // in the test prebuilts right now
      performWithoutSync(action, element)

      assertBuildGradle(project) { it.contains("implementation 'androidx.room:room-runtime:") }
      assertBuildGradle(project) { it.contains("annotationProcessor 'androidx.room:room-compiler:") }
    }
  }

  @Test
  fun testProvideExtraArtifacts() {
    // Ensure that if extra artifacts are needed, we also add them.
    openTestProject(AndroidCoreTestProject.ANDROIDX_SIMPLE) { // this project uses AndroidX
      assertBuildGradle(project) {
        !it.contains("androidx.compose.ui:ui-tooling-preview:") && !it.contains("androidx.compose.ui:ui-tooling:")
      }
      fixture.loadNewFile(
        "app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          @Preview
          @Composable
          fun Foo()
      }
      """.trimIndent()
      )

      val action = AndroidMavenImportIntentionAction()
      val element = fixture.moveCaret("@Previe|w")
      val available = action.isAvailable(project, fixture.editor, element)
      assertThat(available).isTrue()
      assertThat(action.text).isEqualTo("Add dependency on androidx.compose.ui:ui-tooling-preview and import")
      // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
      // in the test prebuilts right now
      performWithoutSync(action, element)

      assertBuildGradle(project) { it.contains("implementation 'androidx.compose.ui:ui-tooling-preview:") }
      assertBuildGradle(project) { it.contains("debugImplementation 'androidx.compose.ui:ui-tooling:") }
    }
  }

  @Test
  fun testUnresolvedSymbol_nonAndroidX() {
    // Like testUnresolvedSymbolInKotlin but in a Java file
    openTestProject(AndroidCoreTestProject.MIGRATE_TO_APP_COMPAT) {
      assertBuildGradle(project) { !it.contains("com.android.support:recyclerview-v7:") }
      fixture.loadNewFile(
        "app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private RecyclerView view;
      }
      """.trimIndent()
      )

      val action = AndroidMavenImportIntentionAction()
      val element = fixture.moveCaret("RecyclerView|")
      val available = action.isAvailable(project, fixture.editor, element)
      assertThat(available).isFalse()
    }
  }

  @Test
  fun doNotSuggestIfAnyIsAlreadyDepended() {
    openTestProject(AndroidCoreTestProject.ANDROIDX_SIMPLE) { // this project uses AndroidX
      assertBuildGradle(project) {
        !it.contains("androidx.palette:palette-ktx:") &&
          !it.contains("androidx.room:room-runtime:")
      }
      fixture.loadNewFile(
        "app/src/main/java/test/pkg/imports/MainActivity2.kt",
        """
        package test.pkg.imports
        val someClass = FakeClass() // Here FakeClass is an unresolvable symbol
      """.trimIndent()
      )
      val source = fixture.editor.document.text
      val action = AndroidMavenImportIntentionAction()
      var element = fixture.moveCaret("FakeClass|()")
      var available = action.isAvailable(project, fixture.editor, element)
      assertThat(available).isTrue()
      // Since we have more than one suggestion, we just show general text `Add library dependency` here.
      assertThat(action.text).isEqualTo("Add library dependency and import")

      performAndWaitForSyncEnd {
        action.perform(project, fixture.editor, element, true)
      }

      // The deterministic order of suggestions are ensured, so the first option `androidx.palette:palette` is applied.
      assertBuildGradle(project) {
        it.contains("implementation 'androidx.palette:palette-ktx:")
      }

      val newSource = fixture.editor.document.text
      val diff = TestUtils.getDiff(source, newSource, 1)
      assertThat(diff.trim()).isEqualTo(
        """
      @@ -2 +2
        package test.pkg.imports
      +
      + import androidx.palette.graphics.FakeClass
      +
        val someClass = FakeClass() // Here FakeClass is an unresolvable symbol
      """.trimIndent().trim()
      )

      // Since we have added on `androidx.palette:palette`, no dependencies are to be suggested any more.
      element = fixture.moveCaret("FakeClass|()")
      available = action.isAvailable(project, fixture.editor, element)
      assertThat(available).isFalse()
    }
  }

  @Test
  fun doNotImportWhenAlreadyFullyQualifiedKotlin_userTypeCase() {
    // Like testDoNotImportWhenAlreadyFullyQualifiedJava, but for Kotlin
    // Like testUnresolvedSymbolInKotlin, but in an AndroidX project (so the artifact name
    // must be mapped both in the display name and in the dependency inserted into the build.gradle file)
    openTestProject(AndroidCoreTestProject.ANDROIDX_SIMPLE) { // this project uses AndroidX
      assertBuildGradle(project) { !it.contains("androidx.camera:camera-core:") }
      fixture.loadNewFile(
        "app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val builder = object : androidx.camera.core.ExtendableBuilder { // Here `camera` (package segment) is an unresolvable symbol
      """.trimIndent()
      )
      val source = fixture.editor.document.text

      val action = AndroidMavenImportIntentionAction()
      val element = fixture.moveCaret("came|ra")
      val available = action.isAvailable(project, fixture.editor, element)
      assertThat(available).isTrue()
      assertThat(action.text).isEqualTo("Add dependency on androidx.camera:camera-core (alpha) and import")
      // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
      // in the test prebuilts right now
      performWithoutSync(action, element)

      assertBuildGradle(project) { it.contains("implementation 'androidx.camera:camera-core:") }

      // Make sure we haven't added an import statement since the reference is already fully qualified
      val newSource = fixture.editor.document.text
      assertThat(source).isEqualTo(newSource)
    }
  }

  @Test
  fun doNotImportWhenAlreadyFullyQualifiedKotlin_userTypeCase_nestedClass() {
    // Like testDoNotImportWhenAlreadyFullyQualifiedJava, but for Kotlin
    // Like testUnresolvedSymbolInKotlin, but in an AndroidX project (so the artifact name
    // must be mapped both in the display name and in the dependency inserted into the build.gradle file)
    openTestProject(AndroidCoreTestProject.ANDROIDX_SIMPLE) { // this project uses AndroidX
      assertBuildGradle(project) { !it.contains("androidx.camera:camera-core:") }
      fixture.loadNewFile(
        "app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val callback = object : androidx.camera.core.ImageCapture.OnImageSavedCallback { // Here `camera` is an unresolvable symbol
      """.trimIndent()
      )
      val source = fixture.editor.document.text

      val action = AndroidMavenImportIntentionAction()
      val element = fixture.moveCaret("came|ra")
      val available = action.isAvailable(project, fixture.editor, element)
      assertThat(available).isTrue()
      assertThat(action.text).isEqualTo("Add dependency on androidx.camera:camera-core (alpha) and import")
      // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
      // in the test prebuilts right now
      performWithoutSync(action, element)

      assertBuildGradle(project) { it.contains("implementation 'androidx.camera:camera-core:") }

      // Make sure we haven't added an import statement since the reference is already fully qualified
      val newSource = fixture.editor.document.text
      assertThat(source).isEqualTo(newSource)
    }
  }
}
