/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.res.psi

import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.navigation.GotoImplementationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.android.AndroidTestCase

/**
 * Tests for [ResourceDefinitionSearch] that is used for *Navigate > Implementation(s)*.
 */
class ResourceDefinitionSearchTest : AndroidTestCase() {

  fun testResourceReferenceInJava() {
    myFixture.addFileToProject("res/values/strings.xml",
      //language=XML
      """
      <resources>
        <string name="app_name">My Application</string>
      </resources>
      """.trimIndent())

    val file = myFixture.addFileToProject(
      "/src/p1/p2/Foo.java",
      //language=JAVA
      """
      package p1.p2;
      class Foo {
        public static void foo() {
          int n = R.string.app_<caret>name;
        }
      }
      """.trimIndent()).virtualFile

    StudioResourceRepositoryManager.getAppResources(myFacet)
    myFixture.configureFromExistingVirtualFile(file)

    val gotoData = TestGotoImplementationHandler().createDataForSource(myFixture.editor, myFixture.caretOffset, myFixture.elementAtCaret)
    val psiElements = gotoData.targets
    assertThat(psiElements).hasLength(1)
    assertThat(psiElements.first().parent.parent.text).isEqualTo("<string name=\"app_name\">My Application</string>")
  }

  fun testResourceReferenceInXml() {
    myFixture.addFileToProject("res/values/strings.xml",
      //language=XML
      """
      <resources>
        <string name="app_name">My Application</string>
      </resources>
      """.trimIndent())

    val file = myFixture.addFileToProject(
      "res/values/test.xml",
      //language=XML
      """
      <resources>
        <string name="test">@string/app_<caret>name</string>
      </resources>
      """.trimIndent()).virtualFile

    StudioResourceRepositoryManager.getAppResources(myFacet)
    myFixture.configureFromExistingVirtualFile(file)

    val gotoData = TestGotoImplementationHandler().createDataForSource(myFixture.editor, myFixture.caretOffset, myFixture.elementAtCaret)
    val psiElements = gotoData.targets
    assertThat(psiElements).hasLength(1)
    assertThat(psiElements.first().parent.parent.text).isEqualTo("<string name=\"app_name\">My Application</string>")
  }

  private class TestGotoImplementationHandler : GotoImplementationHandler() {
    public override fun createDataForSource(editor: Editor, offset: Int, source: PsiElement): GotoData {
      return super.createDataForSource(editor, offset, source)
    }
  }
}
