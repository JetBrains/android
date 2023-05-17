/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.android.tools.idea.editing.actions

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertFailsWith

@RunWith(JUnit4::class)
class AndroidSafeDeleteTest {
  @get:Rule
  var androidProjectRule: AndroidProjectRule = AndroidProjectRule.onDisk()

  private val myFixture by lazy { androidProjectRule.fixture as JavaCodeInsightTestFixture }
  private val myProject by lazy { androidProjectRule.project }

  @Test
  fun deleteComponent() {
    myFixture.addFileToProject(
      "AndroidManifest.xml",
      //language=xml
      """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="p1.p2">
          <application android:icon="@drawable/icon">
              <activity android:name="MyActivity"/>
          </application>
      </manifest>
      """.trimIndent()
    )

    val activityPsiFile = myFixture.addFileToProject(
      "src/p1/p2/MyActivity.java",
      //language=Java
      """
      package p1.p2;
      public class MyActivity extends android.app.Activity {}
      """.trimIndent()
    )

    val activityClass = runReadAction {
      myFixture.javaFacade.findClass("p1.p2.MyActivity", GlobalSearchScope.fileScope(activityPsiFile))
    }
    assertThat(activityClass).isNotNull()

    ApplicationManager.getApplication().invokeAndWait {
      assertFailsWith<ConflictsInTestsException>(
        message = "class <b><code>p1.p2.MyActivity</code></b> has 1 usage that is not safe to delete.") {
        SafeDeleteHandler.invoke(myProject, arrayOf(activityClass), myFixture.module, true, null)
      }
    }
  }

  @Test
  fun deleteResourceFile() {
    myFixture.addFileToProject(
      "AndroidManifest.xml",
      //language=xml
      """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="p1.p2">
          <application android:icon="@drawable/icon">
          </application>
      </manifest>
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "src/p1/p2/DeleteResourceFile.java",
      //language=Java
      """
      package p1.p2;
      public class DeleteResourceFile {
        public void f() {
          int n = R.drawable.my_resource_file;
        }
      }
      """.trimIndent()
    )

    val resFile = myFixture.addFileToProject("res/drawable/my_resource_file.xml", "<root></root>")

    ApplicationManager.getApplication().invokeAndWait {
      assertFailsWith<ConflictsInTestsException>(
        message = "field <b><code>drawable.my_resource_file</code></b> has 1 usage that is not safe to delete.") {
        SafeDeleteHandler.invoke(myProject, arrayOf(resFile), myFixture.module, true, null)
      }
    }
  }
}
