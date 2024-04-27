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

import com.android.SdkConstants
import com.android.test.testutils.TestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val BASE_PATH = "actions/componentRenaming/"

@RunWith(JUnit4::class)
class AndroidComponentRenamingTest {
  @get:Rule
  var androidProjectRule: AndroidProjectRule = AndroidProjectRule.inMemory()

  @get:Rule
  var name: TestName = TestName()

  private val myFixture by lazy {
    androidProjectRule.fixture.apply {
      testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/android/editing/testData").toString()
    }
  }

  @Test
  fun renameActivityFromManifest() {
    createActivity("MyActivity")
    createAndOpenManifest()

    checkAndRenameElementAtCursor("MyActivity1")
  }

  @Test
  fun renameActivityWithChildFromManifest() {
    createActivity("ChildActivity")
    createActivity("MyActivity")
    createAndOpenManifest()

    checkAndRenameElementAtCursor("MyActivity1")
  }

  private fun createActivity(activityName: String) {
    myFixture.addFileToProject(
      "src/p1/p2/$activityName.java",
      //language=Java
      """
      package p1.p2;
      public class $activityName extends android.app.Activity {}
      """.trimIndent()
    )
  }

  private fun createAndOpenManifest() {
    val manifestFile = myFixture.copyFileToProject(BASE_PATH + name.methodName + ".xml", SdkConstants.FN_ANDROID_MANIFEST_XML)
    myFixture.configureFromExistingVirtualFile(manifestFile)
  }

  private fun checkAndRenameElementAtCursor(newName: String) {
    // Ensure the rename action is available to the user
    val action = RenameElementAction()
    val actionEvent = TestActionEvent.createTestEvent(action, DataManager.getInstance().getDataContext(myFixture.editor.component))
    runReadAction { action.update(actionEvent) }
    assertThat(actionEvent.presentation.isEnabled).isTrue()
    assertThat(actionEvent.presentation.isVisible).isTrue()

    // Now do the rename
    ApplicationManager.getApplication().invokeAndWait { myFixture.renameElementAtCaret(newName) }

    // Verify the result of renaming in the manifest.
    myFixture.checkResultByFile(BASE_PATH + name.methodName + "_after.xml")
  }
}
