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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val BASE_PATH = "actions/renameWidget/"

@RunWith(JUnit4::class)
class AndroidRenameWidgetTest {
  @get:Rule
  var androidProjectRule: AndroidProjectRule = AndroidProjectRule.withSdk()

  private val myFixture by lazy {
    androidProjectRule.fixture.apply {
      testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/android/editing/testData").toString()
    } as JavaCodeInsightTestFixture
  }

  private val myProject by lazy { androidProjectRule.project }

  @Before
  fun setUp() {
    myFixture.addFileToProject(
      SdkConstants.FN_ANDROID_MANIFEST_XML,
      //language=xml
      """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="p1.p2">
          <application android:icon="@drawable/icon">
          </application>
      </manifest>
      """.trimIndent())

    myFixture.copyFileToProject(BASE_PATH + "MyWidget.java", "src/p1/p2/MyWidget.java")
  }

  @Test
  fun renameWidget() {
    val file: VirtualFile = myFixture.copyFileToProject(BASE_PATH + "layout_widget.xml", "res/layout/layout_widget.xml")
    myFixture.configureFromExistingVirtualFile(file)

    checkAndRename("MyWidget1")

    myFixture.checkResultByFile(BASE_PATH + "layout_widget_after.xml")
  }

  @Test
  fun renameWidget1() {
    val file: VirtualFile = myFixture.copyFileToProject(BASE_PATH + "layout_widget.xml", "res/layout/layout_widget.xml")
    myFixture.configureFromExistingVirtualFile(file)

    checkAndRename("MyWidget1")

    myFixture.checkResultByFile(BASE_PATH + "layout_widget_after.xml")
  }

  @Test
  fun renameWidgetPackage1() {
    val file: VirtualFile = myFixture.copyFileToProject(BASE_PATH + "layout_widget1.xml", "res/layout/layout_widget1.xml")
    myFixture.configureFromExistingVirtualFile(file)

    checkAndRename("newPackage")

    myFixture.checkResultByFile(BASE_PATH + "layout_widget1_after.xml")
  }

  @Test
  fun moveWidgetPackage1() {
    myFixture.addFileToProject(
      "src/p1/newp/Foo.java",
      """
      package p1.newp;
      class Foo {}
      """.trimIndent())
    myFixture.copyFileToProject(BASE_PATH + "MyPreference.java", "src/p1/p2/MyPreference.java")
    val f: VirtualFile = myFixture.copyFileToProject(BASE_PATH + "layout_widget2.xml",
                                                     "res/layout/layout_widget2.xml")
    myFixture.configureFromExistingVirtualFile(f)
    myFixture.copyFileToProject(BASE_PATH + "custom_pref.xml", "res/xml/custom_pref.xml")

    doMovePackage("p1.p2", "p1.newp")

    myFixture.checkResultByFile("res/layout/layout_widget2.xml", BASE_PATH + "layout_widget2_after.xml", false)
    myFixture.checkResultByFile("res/xml/custom_pref.xml", BASE_PATH + "custom_pref_after.xml", false)
  }

  private fun doMovePackage(packageName: String, newPackageName: String) {
    val packageToRename = runReadAction { myFixture.javaFacade.findPackage(packageName) }
    val newParentPackage = runReadAction { myFixture.javaFacade.findPackage(newPackageName) }
    assertThat(newParentPackage).isNotNull()

    val dirs = newParentPackage!!.directories
    assertThat(dirs).hasLength(1)

    val processor = runReadAction {
      MoveClassesOrPackagesProcessor(myProject,
                                     arrayOf(packageToRename),
                                     SingleSourceRootMoveDestination(
                                       PackageWrapper.create(newParentPackage), dirs.single()),
                                     true,
                                     false,
                                     null)
    }

    ApplicationManager.getApplication().invokeAndWait { processor.run() }
  }

  private fun checkAndRename(newName: String) {
    val action = RenameElementAction()
    val e = TestActionEvent.createTestEvent(
      action, DataManager.getInstance().getDataContext(myFixture.editor.component))

    runReadAction { action.update(e) }

    assertThat(e.presentation.isEnabled).isTrue()
    assertThat(e.presentation.isVisible).isTrue()

    ApplicationManager.getApplication().invokeAndWait { myFixture.renameElementAtCaret(newName) }
  }
}
