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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized


/**
 * Tests for [AndroidMavenImportIntentionAction], regardless of enabling or disabling [StudioFlags.SUGGESTED_IMPORT].
 */
@RunsInEdt
@RunWith(Parameterized::class)
class AndroidMavenImportIntentionActionTest(private val autoImportEnabled: Boolean) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun getParameters() = listOf(true, false)
  }

  private val projectRule = AndroidGradleProjectRule()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  @Before
  fun setUp() {
    StudioFlags.ENABLE_SUGGESTED_IMPORT.override(autoImportEnabled)
    ApplicationManager.getApplication().replaceService(
      MavenClassRegistryManager::class.java,
      fakeMavenClassRegistryManager,
      projectRule.fixture.testRootDisposable
    )
  }

  @After
  fun tearDown() {
    StudioFlags.ENABLE_SUGGESTED_IMPORT.clearOverride()
  }

  @Test
  fun unresolvedSymbolInAndroidX() {
    // Like testUnresolvedSymbolInKotlin, but in an AndroidX project (so the artifact name
    // must be mapped both in the display name and in the dependency inserted into the build.gradle file)
    projectRule.loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle(projectRule.project) { !it.contains("androidx.recyclerview:recyclerview:") }
    projectRule.fixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val view = RecyclerView() // Here RecyclerView is an unresolvable symbol
      """.trimIndent())
    val source = projectRule.fixture.editor.document.text

    val action = AndroidMavenImportIntentionAction()
    val element = projectRule.fixture.moveCaret("RecyclerView|")
    val available = action.isAvailable(projectRule.project, projectRule.fixture.editor, element)
    assertThat(available).isTrue()
    assertThat(action.text).isEqualTo("Add dependency on androidx.recyclerview:recyclerview and import")
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    performWithoutSync(projectRule, action, element)

    assertBuildGradle(projectRule.project) { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

    // Make sure we've imported the RecyclerView correctly as well, including transforming to AndroidX package name
    val newSource = projectRule.fixture.editor.document.text
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

  @Test
  fun doNotImportAlreadyImported() {
    // Like testUnresolvedSymbolInAndroidX, but in this case the symbol is already imported in
    // the source file; in that case, make sure we don't add an extra import. (In Java this is
    // automatically handled by our use of the ImportHandler, but in Kotlin, the normal ImportHandler
    // is tricky to set up so we call the import utility directly and in that case it's up to us
    // to ensure that it's not done redundantly)
    projectRule.loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle(projectRule.project) { !it.contains("androidx.recyclerview:recyclerview:") }
    projectRule.fixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      import androidx.recyclerview.widget.RecyclerView
      val view = RecyclerView() // Here RecyclerView is an unresolvable symbol
      """.trimIndent())
    val source = projectRule.fixture.editor.document.text

    val action = AndroidMavenImportIntentionAction()
    val element = projectRule.fixture.moveCaret("RecyclerView|()")
    val available = action.isAvailable(projectRule.project, projectRule.fixture.editor, element)
    assertThat(available).isTrue()
    assertThat(action.text).isEqualTo("Add dependency on androidx.recyclerview:recyclerview and import")
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    performWithoutSync(projectRule, action, element)

    assertBuildGradle(projectRule.project) { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

    // Make sure we've haven't added a second import statement; the source code should not have changed
    val newSource = projectRule.fixture.editor.document.text
    assertThat(source).isEqualTo(newSource)
  }

  @Test
  fun doNotImportWhenAlreadyFullyQualifiedJava() {
    // If there is a fully qualified reference, we shouldn't import the symbol. And more
    // importantly, the unresolved symbol is typically not the final name, but the first
    // unresolvable package segment. In this case, we have to search a little harder to
    // find the real corresponding library to import.
    projectRule.loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle(projectRule.project) { !it.contains("androidx.recyclerview:recyclerview:") }
    projectRule.fixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private androidx.recyclerview.widget.RecyclerView view;
      }
      """.trimIndent())
    val source = projectRule.fixture.editor.document.text

    val action = AndroidMavenImportIntentionAction()
    val element = projectRule.fixture.moveCaret("recyc|lerview")
    val available = action.isAvailable(projectRule.project, projectRule.fixture.editor, element)
    assertThat(available).isTrue()
    assertThat(action.text).isEqualTo("Add dependency on androidx.recyclerview:recyclerview and import")
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    performWithoutSync(projectRule, action, element)

    assertBuildGradle(projectRule.project) { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

    // Make sure we haven't modified the source to add a new import statement since the
    // reference is already fully qualified
    val newSource = projectRule.fixture.editor.document.text
    assertThat(source).isEqualTo(newSource)
  }

  @Test
  fun doNotImportWhenAlreadyFullyQualifiedJava_nestedClass() {
    // If there is a fully qualified reference, we shouldn't import the symbol. And more
    // importantly, the unresolved symbol is typically not the final name, but the first
    // unresolvable package segment. In this case, we have to search a little harder to
    // find the real corresponding library to import.
    projectRule.loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle(projectRule.project) { !it.contains("androidx.recyclerview:recyclerview:") }
    projectRule.fixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private androidx.recyclerview.widget.RecyclerView.FakeNestedClass view; // recyclerview(package segment) is an unresolvable symbol
      }
      """.trimIndent())
    val source = projectRule.fixture.editor.document.text

    val action = AndroidMavenImportIntentionAction()
    val element = projectRule.fixture.moveCaret("recyc|lerview")
    val available = action.isAvailable(projectRule.project, projectRule.fixture.editor, element)
    assertThat(available).isTrue()
    assertThat(action.text).isEqualTo("Add dependency on androidx.recyclerview:recyclerview and import")
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    performWithoutSync(projectRule, action, element)

    assertBuildGradle(projectRule.project) { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

    // Make sure we haven't modified the source to add a new import statement since the
    // reference is already fully qualified
    val newSource = projectRule.fixture.editor.document.text
    assertThat(source).isEqualTo(newSource)
  }

  @Test
  fun doNotImportWhenAlreadyFullyQualifiedKotlin_dotQualifiedExpressionCase() {
    // Like testDoNotImportWhenAlreadyFullyQualifiedJava, but for Kotlin
    // Like testUnresolvedSymbolInKotlin, but in an AndroidX project (so the artifact name
    // must be mapped both in the display name and in the dependency inserted into the build.gradle file)
    projectRule.loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle(projectRule.project) { !it.contains("androidx.recyclerview:recyclerview:") }
    projectRule.fixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val view = androidx.recyclerview.widget.RecyclerView() // Here recyclerview(package segment) is an unresolvable symbol
      """.trimIndent())
    val source = projectRule.fixture.editor.document.text

    val action = AndroidMavenImportIntentionAction()
    val element = projectRule.fixture.moveCaret("recyc|lerview")
    val available = action.isAvailable(projectRule.project, projectRule.fixture.editor, element)
    assertThat(available).isTrue()
    assertThat(action.text).isEqualTo("Add dependency on androidx.recyclerview:recyclerview and import")
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    performWithoutSync(projectRule, action, element)

    assertBuildGradle(projectRule.project) { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

    // Make sure we haven't added an import statement since the reference is already fully qualified
    val newSource = projectRule.fixture.editor.document.text
    assertThat(source).isEqualTo(newSource)
  }

  @Test
  fun doNotImportWhenAlreadyFQKotlin_dotQualifiedExpressionCase_nestedClass() {
    // Like testDoNotImportWhenAlreadyFullyQualifiedJava, but for Kotlin
    // Like testUnresolvedSymbolInKotlin, but in an AndroidX project (so the artifact name
    // must be mapped both in the display name and in the dependency inserted into the build.gradle file)
    projectRule.loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle(projectRule.project) { !it.contains("androidx.recyclerview:recyclerview:") }
    projectRule.fixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val view = androidx.recyclerview.widget.RecyclerView.FakeNestedClass() // Here recyclerview(package segment) is an unresolvable symbol
      """.trimIndent())
    val source = projectRule.fixture.editor.document.text

    val action = AndroidMavenImportIntentionAction()
    val element = projectRule.fixture.moveCaret("recyc|lerview")
    val available = action.isAvailable(projectRule.project, projectRule.fixture.editor, element)
    assertThat(available).isTrue()
    assertThat(action.text).isEqualTo("Add dependency on androidx.recyclerview:recyclerview and import")
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    performWithoutSync(projectRule, action, element)

    assertBuildGradle(projectRule.project) { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

    // Make sure we haven't added an import statement since the reference is already fully qualified
    val newSource = projectRule.fixture.editor.document.text
    assertThat(source).isEqualTo(newSource)
  }

  @Test
  fun testKtx() {
    // Make sure that if we import a symbol from Kotlin and a ktx library is available, we pick it
    projectRule.loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle(projectRule.project) { !it.contains("androidx.palette:palette-ktx:") }
    projectRule.fixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val palette = Palette() // Here "Palette" is an unresolvable symbol
      """.trimIndent())
    val source = projectRule.fixture.editor.document.text

    val action = AndroidMavenImportIntentionAction()
    val element = projectRule.fixture.moveCaret("Palette|")
    val available = action.isAvailable(projectRule.project, projectRule.fixture.editor, element)
    assertThat(available).isTrue()
    assertThat(action.text).isEqualTo("Add dependency on androidx.palette:palette-ktx and import")
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    performWithoutSync(projectRule, action, element)

    assertBuildGradle(projectRule.project) { it.contains("implementation 'androidx.palette:palette-ktx:") }

    // Make sure we've imported the RecyclerView correctly as well
    val newSource = projectRule.fixture.editor.document.text
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

  @Test
  fun testNotKtx() {
    // Make sure that if we import a symbol from Java and a ktx library is available, we don't pick the ktx version
    projectRule.loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle(projectRule.project) { !it.contains("androidx.palette:palette:") }
    projectRule.fixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private Palette palette;
      }
      """.trimIndent())

    val action = AndroidMavenImportIntentionAction()
    val element = projectRule.fixture.moveCaret("Palette|")
    val available = action.isAvailable(projectRule.project, projectRule.fixture.editor, element)
    assertThat(available)
    assertThat(action.text).isEqualTo("Add dependency on androidx.palette:palette and import")
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    performWithoutSync(projectRule, action, element)

    assertBuildGradle(projectRule.project) { it.contains("implementation 'androidx.palette:palette:") }
  }

  @Test
  fun testAnnotationProcessor() {
    // Ensure that if an annotation processor is available, we also add it
    projectRule.loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle(projectRule.project) { !it.contains("androidx.room:room-runtime:") }
    projectRule.fixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private RoomDatabase database;
      }
      """.trimIndent())

    val action = AndroidMavenImportIntentionAction()
    val element = projectRule.fixture.moveCaret("Room|Database")
    val available = action.isAvailable(projectRule.project, projectRule.fixture.editor, element)
    assertThat(available).isTrue()
    assertThat(action.text).isEqualTo("Add dependency on androidx.room:room-runtime and import")
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    performWithoutSync(projectRule, action, element)

    assertBuildGradle(projectRule.project) { it.contains("implementation 'androidx.room:room-runtime:") }
    assertBuildGradle(projectRule.project) { it.contains("annotationProcessor 'androidx.room:room-compiler:") }
  }
}
