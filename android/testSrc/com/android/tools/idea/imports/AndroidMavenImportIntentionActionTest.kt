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
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager

class AndroidMavenImportIntentionActionTest : AndroidGradleTestCase() {

  fun testUnresolvedSymbolInKotlin() {
    // In a project not using AndroidX, add a new file which contains an unresolved
    // symbol "RecyclerView"; check that the unresolved action applies to it, has
    // the right label and when invoked adds a com.android.support:recyclerview
    // dependency

    loadProject(TestProjectPaths.MIGRATE_TO_APP_COMPAT) // project not using AndroidX
    assertBuildGradle { !it.contains("com.android.support:recyclerview-v7:") } // not already using recyclerview

    myFixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      import android.support.v7.app.AppCompatActivity;
      class MainActivity2 : AppCompatActivity() {
          val view = RecyclerView() // Here RecyclerView is an unresolvable symbol
      }
      """.trimIndent())
    val source = myFixture.editor.document.text

    val action = AndroidMavenImportIntentionAction()
    val element = myFixture.moveCaret("R|ecyclerView")
    val available = action.isAvailable(project, myFixture.editor, element)
    assertTrue(available)
    assertEquals("Add dependency on com.android.support:recyclerview-v7", action.text)

    // Check corner case: if the caret is at the very end of the word, the element is the element on
    // the right. Make sure we support that one as well:
    val next = myFixture.moveCaret("RecyclerView|")
    assertEquals("(", next.text)
    assertTrue(action.isAvailable(project, myFixture.editor, next))

    performAndSync(action, element)

    assertBuildGradle { it.contains("implementation 'com.android.support:recyclerview-v7:") }

    // Also make sure the action doesn't apply elsewhere, such as on the "MainActivity2" identifier:
    assertFalse(action.isAvailable(project, myFixture.editor, myFixture.moveCaret("Main|Activity2")))

    // Now make sure the action doesn't apply on RecyclerView, since we've already imported it:
    assertFalse(action.isAvailable(project, myFixture.editor, myFixture.moveCaret("Recycler|View")))

    // Make sure we've imported the RecyclerView correctly as well
    val newSource = myFixture.editor.document.text
    val diff = TestUtils.getDiff(source, newSource, 1)
    assertThat(diff.trim()).isEqualTo(
      """
      @@ -3 +3
        import android.support.v7.app.AppCompatActivity;
      + import android.support.v7.widget.RecyclerView
      +
        class MainActivity2 : AppCompatActivity() {
      """.trimIndent().trim()
    )
  }

  fun testUnresolvedSymbolInJava() {
    // Like testUnresolvedSymbolInKotlin but in a Java file
    loadProject(TestProjectPaths.MIGRATE_TO_APP_COMPAT)
    assertBuildGradle { !it.contains("com.android.support:recyclerview-v7:") }
    myFixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private RecyclerView view;
      }
      """.trimIndent())
    val source = myFixture.editor.document.text

    val action = AndroidMavenImportIntentionAction()
    val element = myFixture.moveCaret("RecyclerView|")
    val available = action.isAvailable(project, myFixture.editor, element)
    assertTrue(available)
    assertEquals("Add dependency on com.android.support:recyclerview-v7", action.text)
    performAndSync(action, element)

    assertBuildGradle { it.contains("implementation 'com.android.support:recyclerview-v7:") }

    // Make sure we've imported the RecyclerView correctly as well
    val newSource = myFixture.editor.document.text
    val diff = TestUtils.getDiff(source, newSource, 1)
    assertThat(diff.trim()).isEqualTo(
      """
      @@ -2 +2
        package test.pkg.imports;
      +
      + import android.support.v7.widget.RecyclerView;
      +
        public class Test {
      """.trimIndent().trim()
    )
  }

  fun testUnresolvedSymbolInAndroidX() {
    // Like testUnresolvedSymbolInKotlin, but in an AndroidX project (so the artifact name
    // must be mapped both in the display name and in the dependency inserted into the build.gradle file)
    loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle { !it.contains("androidx.recyclerview:recyclerview:") }
    myFixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val view = RecyclerView() // Here RecyclerView is an unresolvable symbol
      """.trimIndent())
    val source = myFixture.editor.document.text

    val action = AndroidMavenImportIntentionAction()
    val element = myFixture.moveCaret("RecyclerView|")
    val available = action.isAvailable(project, myFixture.editor, element)
    assertTrue(available)
    assertEquals("Add dependency on androidx.recyclerview:recyclerview", action.text)
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    perform(action, element)

    assertBuildGradle { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

    // Make sure we've imported the RecyclerView correctly as well, including transforming to AndroidX package name
    val newSource = myFixture.editor.document.text
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

  fun testDoNotImportAlreadyImported() {
    // Like testUnresolvedSymbolInAndroidX, but in this case the symbol is already imported in
    // the source file; in that case, make sure we don't add an extra import. (In Java this is
    // automatically handled by our use of the ImportHandler, but in Kotlin, the normal ImportHandler
    // is tricky to set up so we call the import utility directly and in that case it's up to us
    // to ensure that it's not done redundantly)
    loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle { !it.contains("androidx.recyclerview:recyclerview:") }
    myFixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      import androidx.recyclerview.widget.RecyclerView
      val view = RecyclerView() // Here RecyclerView is an unresolvable symbol
      """.trimIndent())
    val source = myFixture.editor.document.text

    val action = AndroidMavenImportIntentionAction()
    val element = myFixture.moveCaret("RecyclerView|()")
    val available = action.isAvailable(project, myFixture.editor, element)
    assertTrue(available)
    assertEquals("Add dependency on androidx.recyclerview:recyclerview", action.text)
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    perform(action, element)

    assertBuildGradle { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

    // Make sure we've haven't added a second import statement; the source code should not have changed
    val newSource = myFixture.editor.document.text
    assertThat(source).isEqualTo(newSource)
  }

  fun testDoNotImportWhenAlreadyFullyQualifiedJava() {
    // If there is a fully qualified reference, we shouldn't import the symbol. And more
    // importantly, the unresolved symbol is typically not the final name, but the first
    // unresolvable package segment. In this case, we have to search a little harder to
    // find the real corresponding library to import.
    loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle { !it.contains("androidx.recyclerview:recyclerview:") }
    myFixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private androidx.recyclerview.widget.RecyclerView view;
      }
      """.trimIndent())
    val source = myFixture.editor.document.text

    val action = AndroidMavenImportIntentionAction()
    val element = myFixture.moveCaret("recyc|lerview")
    val available = action.isAvailable(project, myFixture.editor, element)
    assertTrue(available)
    assertEquals("Add dependency on androidx.recyclerview:recyclerview", action.text)
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    perform(action, element)

    assertBuildGradle { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

    // Make sure we haven't modified the source to add a new import statement since the
    // reference is already fully qualified
    val newSource = myFixture.editor.document.text
    assertThat(source).isEqualTo(newSource)
  }

  fun testDoNotImportWhenAlreadyFullyQualifiedKotlin() {
    // Like testDoNotImportWhenAlreadyFullyQualifiedJava, but for Kotlin
    // Like testUnresolvedSymbolInKotlin, but in an AndroidX project (so the artifact name
    // must be mapped both in the display name and in the dependency inserted into the build.gradle file)
    loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle { !it.contains("androidx.recyclerview:recyclerview:") }
    myFixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val view = androidx.recyclerview.widget.RecyclerView() // Here RecyclerView is an unresolvable symbol
      """.trimIndent())
    val source = myFixture.editor.document.text

    val action = AndroidMavenImportIntentionAction()
    val element = myFixture.moveCaret("recyc|lerview")
    val available = action.isAvailable(project, myFixture.editor, element)
    assertTrue(available)
    assertEquals("Add dependency on androidx.recyclerview:recyclerview", action.text)
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    perform(action, element)

    assertBuildGradle { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

    // Make sure we haven't added an import statement since the reference is already fully qualified
    val newSource = myFixture.editor.document.text
    assertThat(source).isEqualTo(newSource)
  }

  fun testKtx() {
    // Make sure that if we import a symbol from Kotlin and a ktx library is available, we pick it
    loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle { !it.contains("androidx.palette:palette:") }
    myFixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val palette = Palette() // Here "Palette" is an unresolvable symbol
      """.trimIndent())
    val source = myFixture.editor.document.text

    val action = AndroidMavenImportIntentionAction()
    val element = myFixture.moveCaret("Palette|")
    val available = action.isAvailable(project, myFixture.editor, element)
    assertTrue(available)
    assertEquals("Add dependency on androidx.palette:palette-ktx", action.text)
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    perform(action, element)

    assertBuildGradle { it.contains("implementation 'androidx.palette:palette-ktx:") }

    // Make sure we've imported the RecyclerView correctly as well
    val newSource = myFixture.editor.document.text
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

  fun testNotKtx() {
    // Make sure that if we import a symbol from Java and a ktx library is available, we don't pick the ktx version
    loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle { !it.contains("androidx.palette:palette:") }
    myFixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private Palette palette;
      }
      """.trimIndent())

    val action = AndroidMavenImportIntentionAction()
    val element = myFixture.moveCaret("Palette|")
    val available = action.isAvailable(project, myFixture.editor, element)
    assertTrue(available)
    assertEquals("Add dependency on androidx.palette:palette", action.text)
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    perform(action, element)

    assertBuildGradle { it.contains("implementation 'androidx.palette:palette:") }
  }

  fun testAnnotationProcessor() {
    // Ensure that if an annotation processor is available, we also add it
    loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle { !it.contains("androidx.palette:palette-v7:") }
    myFixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private RoomDatabase database;
      }
      """.trimIndent())

    val action = AndroidMavenImportIntentionAction()
    val element = myFixture.moveCaret("Room|Database")
    val available = action.isAvailable(project, myFixture.editor, element)
    assertTrue(available)
    assertEquals("Add dependency on androidx.room:room-runtime", action.text)
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    perform(action, element)

    assertBuildGradle { it.contains("implementation 'androidx.room:room-runtime:") }
    assertBuildGradle { it.contains("annotationProcessor 'androidx.room:room-compiler:") }
  }

  // Testing infrastructure

  private fun perform(action: AndroidMavenImportIntentionAction, element: PsiElement) {
    action.perform(project, element, myFixture.editor.caretModel.offset, false)
  }

  private fun performAndSync(action: AndroidMavenImportIntentionAction, element: PsiElement) {
    val syncFuture = action.perform(project, element, myFixture.editor.caretModel.offset, true)
    val result = syncFuture?.get()
    assertThat(result).named("Second sync result").isEqualTo(ProjectSystemSyncManager.SyncResult.SUCCESS)
  }

  private fun assertBuildGradle(check: (String) -> Unit) {
    val buildGradle = project.guessProjectDir()!!.findFileByRelativePath("app/build.gradle")
    val buildGradlePsi = PsiManager.getInstance(project).findFile(buildGradle!!)
    check(buildGradlePsi!!.text)
  }
}
