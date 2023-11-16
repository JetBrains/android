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
package org.jetbrains.android.dom.navigation

import com.android.tools.idea.testing.AndroidDomRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiClass
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

@RunsInEdt
class NavigationReferenceRenameTest {
  private val projectRule = AndroidProjectRule.withSdk().initAndroid(true)
  private val domRule = AndroidDomRule("res/navigation") { projectRule.fixture }

  @get:Rule
  val ruleChain: TestRule = RuleChain.outerRule(projectRule).around(domRule).around(EdtRule())

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = AndroidTestBase.getTestDataPath() + "/dom/navigation"
  }

  @Test
  fun testArgTypeRename() {
    projectRule.fixture.copyFileToProject("Data.kt", "src/p1/p2/Data.kt")
    val navFile = projectRule.fixture.copyFileToProject("nav_arg_type_rename.xml", "res/navigation/nav_arg_type_rename.xml")
    val classToRename: PsiClass = projectRule.fixture.findClass("p1.p2.Data")
    projectRule.fixture.renameElement(classToRename, "MyDataClass")
    val arg: XmlTag? = navFile.toPsiFile(projectRule.project)?.findDescendantOfType<XmlTag>()
      ?.findFirstSubTag("fragment")
      ?.findFirstSubTag("argument")
    assertThat(arg?.getAttribute("app:argType")?.value).isEqualTo("p1.p2.MyDataClass")
  }
}
