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

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

class AndroidMavenImportIntentionActionTest : AndroidGradleTestCase() {
  fun testUnresolvedSymbolInKotlin() {
    // In a project not using AndroidX, add a new file which contains an unresolved
    // symbol "RecyclerView"; check that the unresolved action applies to it, has
    // the right label and when invoked adds a com.android.support:recyclerview
    // dependency

    loadProject(TestProjectPaths.MIGRATE_TO_ANDROID_X) // project not using AndroidX
    assertBuildGradle { !it.contains("com.android.support:recyclerview-v7:") } // not already using recyclerview

    val file = loadNewFile("/app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      import android.support.v7.app.AppCompatActivity;
      class MainActivity2 : AppCompatActivity() {
          val view = RecyclerView() // Here RecyclerView is an unresolvable symbol
      }
      """.trimIndent())

    val action = createAction()
    val element = moveCaret(file, "R|ecyclerView")
    val available = action.isAvailable(project, myFixture.editor, element)
    assertTrue(available)
    assertEquals("Add dependency on com.android.support:recyclerview-v7", action.text)

    // Check corner case: if the caret is at the very end of the word, the element is the element on
    // the right. Make sure we support that one as well:
    val next = moveCaret(file, "RecyclerView|")
    assertEquals("(", next.text)
    assertTrue(action.isAvailable(project, myFixture.editor, next))

    performAndSync(action, element)

    assertBuildGradle { it.contains("implementation 'com.android.support:recyclerview-v7:") }

    // Also make sure the action doesn't apply elsewhere, such as on the "MainActivity2" identifier:
    assertFalse(action.isAvailable(project, myFixture.editor, moveCaret(file, "Main|Activity2")))

    // Now make sure the action doesn't apply on RecyclerView, since we've already imported it:
    assertFalse(action.isAvailable(project, myFixture.editor, moveCaret(file, "Recycler|View")))
  }

  fun testUnresolvedSymbolInJava() {
    // Like testUnresolvedSymbolInKotlin but in a Java file
    loadProject(TestProjectPaths.MIGRATE_TO_ANDROID_X)
    assertBuildGradle { !it.contains("com.android.support:recyclerview-v7:") }
    val file = loadNewFile("/app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private RecyclerView view;
      }
      """.trimIndent())

    val action = createAction()
    val element = moveCaret(file, "RecyclerView|")
    val available = action.isAvailable(project, myFixture.editor, element)
    assertTrue(available)
    assertEquals("Add dependency on com.android.support:recyclerview-v7", action.text)
    performAndSync(action, element)

    assertBuildGradle { it.contains("implementation 'com.android.support:recyclerview-v7:") }
  }

  fun testUnresolvedSymbolInAndroidX() {
    // Like testUnresolvedSymbolInKotlin, but in an AndroidX project (so the artifact name
    // must be mapped both in the display name and in the dependency inserted into the build.gradle file)
    loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle { !it.contains("androidx.recyclerview:recyclerview:") }
    val file = loadNewFile("/app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val view = RecyclerView() // Here RecyclerView is an unresolvable symbol
      """.trimIndent())


    val action = createAction()
    val element = moveCaret(file, "RecyclerView|")
    val available = action.isAvailable(project, myFixture.editor, element)
    assertTrue(available)
    assertEquals("Add dependency on androidx.recyclerview:recyclerview", action.text)
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    perform(action, element)

    assertBuildGradle { it.contains("implementation 'androidx.recyclerview:recyclerview:") }
  }

  fun testKtx() {
    // Make sure that if we import a symbol from Kotlin and a ktx library is available, we pick it
    loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle { !it.contains("androidx.palette:palette:") }
    val file = loadNewFile("/app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val palette = Palette() // Here "Palette" is an unresolvable symbol
      """.trimIndent())


    val action = createAction()
    val element = moveCaret(file, "Palette|")
    val available = action.isAvailable(project, myFixture.editor, element)
    assertTrue(available)
    assertEquals("Add dependency on androidx.palette:palette-ktx", action.text)
    // Note: We do perform, not performAndSync here, since the androidx libraries aren't available
    // in the test prebuilts right now
    perform(action, element)

    assertBuildGradle { it.contains("implementation 'androidx.palette:palette-ktx:") }
  }

  fun testNotKtx() {
    // Make sure that if we import a symbol from Java and a ktx library is available, we don't pick the ktx version
    loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // this project uses AndroidX
    assertBuildGradle { !it.contains("androidx.palette:palette:") }
    val file = loadNewFile("/app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private Palette palette;
      }
      """.trimIndent())

    val action = createAction()
    val element = moveCaret(file, "Palette|")
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
    val file = loadNewFile("/app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private RoomDatabase database;
      }
      """.trimIndent())

    val action = createAction()
    val element = moveCaret(file, "Room|Database")
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

  private fun moveCaret(file: PsiFile, window: String): PsiElement {
    val text = file.text
    val delta = window.indexOf("|")
    assertTrue("Not caret marker found in the window", delta != -1)
    val target = window.substring(0, delta) + window.substring(delta + 1)
    val start = text.indexOf(target)
    assertTrue("Didn't find the string $target in the source $text", start != -1)
    val offset = start + delta
    myFixture.editor.caretModel.moveToOffset(offset)
    // myFixture.elementAtCaret seems to do something else
    return file.findElementAt(offset)!!
  }

  private fun loadNewFile(path: String, contents: String): PsiFile {
    val file = myFixture.addFileToProject(path, contents)
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    return file
  }

  private fun perform(action: AndroidMavenImportIntentionAction, element: PsiElement) {
    action.perform(project, element, myFixture.editor.caretModel.offset, false)
  }

  private fun performAndSync(action: AndroidMavenImportIntentionAction, element: PsiElement) {
    val syncFuture = action.perform(project, element, myFixture.editor.caretModel.offset, true)
    syncFuture?.get()
  }

  private fun assertBuildGradle(check: (String) -> Unit) {
    val buildGradle = project.baseDir.findFileByRelativePath("/app/build.gradle")
    val buildGradlePsi = PsiManager.getInstance(project).findFile(buildGradle!!)
    check(buildGradlePsi!!.text)
  }

  private fun createAction(): AndroidMavenImportIntentionAction {
    val module = myModules.appModule
    return object : AndroidMavenImportIntentionAction() {
      override fun getModule(element: PsiElement): Module? {
        // because ModuleUtil.findModuleForPsiElement isn't working in AndroidGradleTest
        return module
      }
    }
  }
}