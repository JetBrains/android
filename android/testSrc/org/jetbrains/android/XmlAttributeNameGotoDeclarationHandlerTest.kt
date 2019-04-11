/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android

import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlTag

class XmlAttributeNameGotoDeclarationHandlerTest : AndroidTestCase() {

  fun testAppNamespaceXmlAttribute() {
    myFixture.addFileToProject(
      "res/values/attrs.xml",
      //language=XML
      """<?xml version="1.0" encoding="utf-8"?>
      <resources>
          <declare-styleable name="MyView">
              <attr name="answer">
                  <enum name="yes" value="0" />
                  <enum name="no" value="1" />
              </attr>
              <attr name="android:maxHeight" />
          </declare-styleable>
      </resources>
      """)
    val psiFile = myFixture.addFileToProject(
      "res/layout/example.xml",
      //language=XML
      """<?xml version="1.0" encoding="utf-8"?>
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    android:orientation="vertical"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

          <MyView android:layout_width="match_parent" android:layout_height="match_parent"
                                                  app:answer="yes"/>
      </LinearLayout>
      """)

    myFixture.openFileInEditor(psiFile.virtualFile)
    myFixture.moveCaret("app:a|nswer=\"yes\"")

    val elements = GotoDeclarationAction.findAllTargetElements(myFixture.project, myFixture.editor, myFixture.caretOffset)
    val listOfDescriptions = elements.map {
      DeclarationDescription.createDeclarationDescription(it.navigationElement)
    }
    assertThat(listOfDescriptions).containsExactly(
      DeclarationDescription("values/attrs.xml",
                              //language=XML
                             """<attr name="answer">
                  <enum name="yes" value="0" />
                  <enum name="no" value="1" />
              </attr>"""))
  }

  fun testFrameworkNamespaceXmlAttribute() {
    val psiFile = myFixture.addFileToProject(
      "res/layout/example.xml",
      //language=XML
      """<?xml version="1.0" encoding="utf-8"?>
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:orientation="vertical"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

      </LinearLayout>
      """)

    myFixture.openFileInEditor(psiFile.virtualFile)
    myFixture.moveCaret("android:layout|_width=\"match_parent\"")
    val elements = GotoDeclarationAction.findAllTargetElements(myFixture.project, myFixture.editor, myFixture.caretOffset)
    val listOfDescriptions = elements.map {
      DeclarationDescription.createDeclarationDescription(it.navigationElement)
    }
    assertThat(listOfDescriptions).containsExactly(
      DeclarationDescription("values/attrs.xml",
                             //language=XML
                             """<attr name="layout_width" format="dimension">
            <!-- The view should be as big as its parent (minus padding).
                 This constant is deprecated starting from API Level 8 and
                 is replaced by {@code match_parent}. -->
            <enum name="fill_parent" value="-1" />
            <!-- The view should be as big as its parent (minus padding).
                 Introduced in API Level 8. -->
            <enum name="match_parent" value="-1" />
            <!-- The view should be only big enough to enclose its content (plus padding). -->
            <enum name="wrap_content" value="-2" />
        </attr>"""))
  }

  data class DeclarationDescription(val directoryName: String, val surroundingTagText: String) {
    companion object {
      fun createDeclarationDescription(element: PsiElement): DeclarationDescription {
        return DeclarationDescription(
          element.containingFile.parent!!.name + "/" + element.containingFile.name,
          element.parentOfType(XmlTag::class)?.text ?: "")
      }
    }
  }
}