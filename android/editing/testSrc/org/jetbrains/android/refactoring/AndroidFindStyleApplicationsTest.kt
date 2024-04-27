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
package org.jetbrains.android.refactoring

import com.android.test.testutils.TestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.usageView.UsageInfo
import com.intellij.util.xml.DomManager
import org.jetbrains.android.dom.layout.LayoutViewElementDomFileDescription
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertFailsWith

private const val BASE_PATH = "refactoring/findStyleApplication/"

@RunWith(JUnit4::class)
class AndroidFindStyleApplicationsTest {
  @get:Rule
  var androidProjectRule: AndroidProjectRule = AndroidProjectRule.onDisk()

  @get:Rule
  var testName = TestName()

  private val myFixture by lazy {
    androidProjectRule.fixture.apply {
      testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/android/editing/testData").toString()
    }
  }

  @Test
  fun basicStyleInlining() {
    doInlineAndroidStyleTest()
  }

  @Test
  fun basicStyleInlining_granular() {
    myFixture.copyFileToProject(BASE_PATH + "basicStyleInlining_layout.xml", "res/layout/layout.xml")
    val styleVirtualFile = myFixture.copyFileToProject(BASE_PATH + "basicStyleInlining_styles.xml", "res/values/styles.xml")
    myFixture.configureFromExistingVirtualFile(styleVirtualFile)

    val tag = runReadAction { PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), XmlTag::class.java) }
    assertThat(tag).isNotNull()

    val styleData = runReadAction { AndroidFindStyleApplicationsAction.getStyleData(tag!!) }
    assertThat(styleData).isNotNull()

    val processor = runReadAction { AndroidFindStyleApplicationsAction.createFindStyleApplicationsProcessor(tag, styleData, null) }
    processor.configureScope(AndroidFindStyleApplicationsProcessor.MyScope.PROJECT, null)

    val files = runReadAction { processor.collectFilesToProcess() }
    assertThat(files).hasSize(1)

    val layoutFile = files.single() as XmlFile
    val domFileDescription = runReadAction { DomManager.getDomManager(myFixture.project).getDomFileDescription(layoutFile) }
    assertThat(domFileDescription)
      .isInstanceOf(LayoutViewElementDomFileDescription::class.java)

    val usages: List<UsageInfo> = ArrayList()
    runReadAction { processor.collectPossibleStyleApplications(layoutFile, usages) }
    assertThat(usages).hasSize(2)
  }

  @Test
  fun styleWithDotSeparatedName() {
    doInlineAndroidStyleTest()
  }

  @Test
  fun caretOnStyleTag() {
    doInlineAndroidStyleTest()
  }

  @Test
  fun styleHasParentWithDot() {
    doInlineAndroidStyleTest()
  }

  @Test
  fun styleHasParentWithParent() {
    doInlineAndroidStyleTest()
  }

  @Test
  fun multipleLayouts() {
    doInlineAndroidStyleTest(listOf("layout1", "layout2"))
  }

  @Test
  fun noStylesToInline() {
    assertFailsWith<RuntimeException>("IDEA has not found any possible applications of style 'style1'") { doInlineAndroidStyleTest() }
  }

  private fun doInlineAndroidStyleTest(layoutFileNames: List<String> = listOf("layout")) {
    val testMethodName = testName.methodName

    for (layoutFileName in layoutFileNames) {
      myFixture.copyFileToProject("$BASE_PATH${testMethodName}_layout.xml", "res/layout/$layoutFileName.xml")
    }

    val stylesXmlVirtualFile = myFixture.copyFileToProject("$BASE_PATH${testMethodName}_styles.xml", "res/values/styles.xml")
    myFixture.configureFromExistingVirtualFile(stylesXmlVirtualFile)

    runFindStyleApplicationAction()

    myFixture.checkResultByFile("$BASE_PATH${testMethodName}_styles.xml")
    for (layoutFileName in layoutFileNames) {
      myFixture.checkResultByFile("res/layout/$layoutFileName.xml", "$BASE_PATH${testMethodName}_layout_after.xml", true)
    }
  }

  private fun runFindStyleApplicationAction() {
    val myTestConfig = AndroidFindStyleApplicationsAction.MyTestConfig(AndroidFindStyleApplicationsProcessor.MyScope.PROJECT)
    val action = AndroidFindStyleApplicationsAction(myTestConfig)
    ApplicationManager.getApplication().invokeAndWait { myFixture.testAction (action) }
  }
}
