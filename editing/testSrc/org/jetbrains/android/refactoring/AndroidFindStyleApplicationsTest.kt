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

import com.android.testutils.TestUtils
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertFailsWith

private const val BASE_PATH = "refactoring/findStyleApplication/"

@RunWith(JUnit4::class)
class AndroidFindStyleApplicationsTest {
  @get:Rule
  var androidProjectRule: AndroidProjectRule = AndroidProjectRule.onDisk()

  private val myFixture by lazy { androidProjectRule.fixture }

  @Before
  fun setUp() {
    myFixture.testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/editing/testData").toString()
  }

  @Test
  fun test1() {
    doTest("1")
  }

  @Test
  fun testGranular1() {
    myFixture.copyFileToProject(BASE_PATH + "1_layout.xml", "res/layout/layout.xml")
    val styleVirtualFile = myFixture.copyFileToProject(BASE_PATH + "1.xml", "res/values/styles.xml")
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
  fun test2() {
    doTest("2")
  }

  @Test
  fun test3() {
    doTest("3")
  }

  @Test
  fun test4() {
    doTest("4")
  }

  @Test
  fun test5() {
    doTest("5")
  }

  @Test
  fun test6() {
    myFixture.copyFileToProject(BASE_PATH + "6_layout.xml", "res/layout/layout1.xml")
    myFixture.copyFileToProject(BASE_PATH + "6_layout.xml", "res/layout/layout2.xml")
    doTest1("6")
    myFixture.checkResultByFile("res/layout/layout1.xml", BASE_PATH + "6_layout_after.xml", true)
    myFixture.checkResultByFile("res/layout/layout2.xml", BASE_PATH + "6_layout_after.xml", true)
  }

  @Test
  fun test7() {
    assertFailsWith<RuntimeException>("IDEA has not found any possible applications of style 'style1'") { doTest("7") }
  }

  private fun doTest(testName: String) {
    myFixture.copyFileToProject("$BASE_PATH${testName}_layout.xml", "res/layout/layout.xml")
    doTest1(testName)
    myFixture.checkResultByFile("res/layout/layout.xml", "$BASE_PATH${testName}_layout_after.xml", true)
  }

  private fun doTest1(testName: String) {
    val f = myFixture.copyFileToProject("$BASE_PATH$testName.xml", "res/values/styles.xml")
    myFixture.configureFromExistingVirtualFile(f)

    val myTestConfig = AndroidFindStyleApplicationsAction.MyTestConfig(AndroidFindStyleApplicationsProcessor.MyScope.PROJECT)
    val action = AndroidFindStyleApplicationsAction(myTestConfig)
    ApplicationManager.getApplication().invokeAndWait { myFixture.testAction (action) }

    myFixture.checkResultByFile("$BASE_PATH$testName.xml")
  }
}
