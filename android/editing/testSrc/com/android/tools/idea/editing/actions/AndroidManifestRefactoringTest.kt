/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.editing.actions

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AndroidManifestRefactoringTest {
  @get:Rule var androidProjectRule: AndroidProjectRule = AndroidProjectRule.onDisk()

  private val myFixture by lazy { androidProjectRule.fixture }
  private val myProject by lazy { androidProjectRule.project }

  @Test
  fun moveApplicationClass() {
    // Arrange: add manifest and custom Application class.
    val applicationPsiFile =
      myFixture.addFileToProject(
        "src/p1/p2/MyApplication.java",
        // language=Java
        """
      package p1.p2;
      public class MyApplication extends android.app.Application {}
      """
          .trimIndent(),
      )

    myFixture.addFileToProject(
      "AndroidManifest.xml",
      // language=xml
      """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="p1.p2">
          <application android:name=".MyApplication">
          </application>
      </manifest>
      """
        .trimIndent(),
    )

    // Act: Move (rename) the Application class.
    val javaPsiFacade = JavaPsiFacade.getInstance(myProject)
    val applicationPsiClass = runReadAction {
      javaPsiFacade.findClass(
        "p1.p2.MyApplication",
        GlobalSearchScope.fileScope(applicationPsiFile),
      )
    }
    assertThat(applicationPsiClass).isNotNull()

    val p1PsiPackage = runReadAction { javaPsiFacade.findPackage("p1") }
    assertThat(p1PsiPackage).isNotNull()

    val packageDirs = p1PsiPackage!!.directories
    assertThat(packageDirs).hasLength(1)

    val processor = runReadAction {
      val destination =
        SingleSourceRootMoveDestination(PackageWrapper.create(p1PsiPackage), packageDirs.single())
      MoveClassesOrPackagesProcessor(
        myProject,
        arrayOf(applicationPsiClass),
        destination,
        true,
        true,
        null,
      )
    }

    ApplicationManager.getApplication().invokeAndWait { processor.run() }

    // Assert: Manifest should be updated to point to the new Application.
    myFixture.checkResult(
      "AndroidManifest.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="p1.p2">
          <application android:name="p1.MyApplication">
          </application>
      </manifest>
      """
        .trimIndent(),
      false,
    )
  }

  @Test
  fun renameCustomApplicationFromManifest_classNameStartsWithDot() {
    createCustomApplication(className = "MyClass", classNameInManifest = ".MyClass")
    renameClass(newName = "MyClass2")
    verifyRenamedApplication(newClassName = "MyClass2", classNameInManifest = ".MyClass2")
  }

  @Test
  fun renameCustomApplicationFromManifest_classShortNameOnly() {
    createCustomApplication(className = "MyClass", classNameInManifest = "MyClass")
    renameClass(newName = "MyClass2")
    verifyRenamedApplication(newClassName = "MyClass2", classNameInManifest = ".MyClass2")
  }

  @Test
  fun renameCustomApplicationFromManifest_classQualifiedName() {
    createCustomApplication(className = "MyClass", classNameInManifest = "p1.p2.MyClass")
    renameClass(newName = "MyClass2")
    verifyRenamedApplication(newClassName = "MyClass2", classNameInManifest = "p1.p2.MyClass2")
  }

  private fun createCustomApplication(className: String, classNameInManifest: String) {
    val myClassPsiFile =
      myFixture.addFileToProject(
        "src/p1/p2/$className.java",
        // language=Java
        """
        package p1.p2;
        public class $className extends android.app.Application {}
        """
          .trimIndent(),
      )

    myFixture.addFileToProject(
      "AndroidManifest.xml",
      // language=xml
      """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="p1.p2">
          <application android:name="$classNameInManifest">
          </application>
        </manifest>
        """
        .trimIndent(),
    )

    ApplicationManager.getApplication().invokeAndWait {
      myFixture.openFileInEditor(myClassPsiFile.virtualFile)
      myFixture.moveCaret("public class $className| ")
    }
  }

  private fun renameClass(newName: String) {
    // Ensure the rename action is available to the user
    val action = RenameElementAction()
    val actionEvent =
      TestActionEvent.createTestEvent(
        action,
        DataManager.getInstance().getDataContext(myFixture.editor.component),
      )
    runReadAction { action.update(actionEvent) }
    assertThat(actionEvent.presentation.isEnabled).isTrue()
    assertThat(actionEvent.presentation.isVisible).isTrue()

    // Now do the rename
    ApplicationManager.getApplication().invokeAndWait { myFixture.renameElementAtCaret(newName) }
  }

  private fun verifyRenamedApplication(newClassName: String, classNameInManifest: String) {
    myFixture.checkResult(
      "src/p1/p2/$newClassName.java",
      // language=Java
      """
      package p1.p2;
      public class $newClassName extends android.app.Application {}
      """
        .trimIndent(),
      false,
    )

    myFixture.checkResult(
      "AndroidManifest.xml",
      // language=xml
      """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="p1.p2">
        <application android:name="$classNameInManifest">
        </application>
      </manifest>
      """
        .trimIndent(),
      false,
    )
  }
}
