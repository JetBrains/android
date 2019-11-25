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
package com.android.tools.idea.res.psi

import com.google.common.truth.Truth
import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlToken
import com.intellij.testFramework.EditorTestUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.AndroidTestCase

/**
 * Tests for [GoToAndroidResourceContributor].
 */
class GoToAndroidResourceContributorTest : AndroidTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject("res/values/strings.xml", """
        <resources>
          <string name="my_string">My string</string>
        </resources>
        """.trimIndent())
    myFixture.addFileToProject("res/layout/my_layout.xml", """
        <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:tools="http://schemas.android.com/tools"
            android:id="@+id/item_detail_container"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            tools:context=".ItemDetailActivity"
            tools:ignore="MergeRootFrame">
          <Button
              android:id="@+id/my_button"
              android:layout_width="match_parent"
              android:layout_height="wrap_content"
              android:text="Sign in"/>
        </FrameLayout>
        """.trimIndent())
  }

  private fun navigate(name: String, pattern: String): PsiElement {
    val model = GotoSymbolModel2(myFixture.project)
    val searchResults = model.getElementsByName(name, false, pattern)
    Truth.assertThat(searchResults).hasLength(1)
    val result = searchResults[0]
    Truth.assertThat(result).isInstanceOf(NavigationItem::class.java)
    Truth.assertThat((result as NavigationItem).presentation!!.getIcon(false)).isNotNull()
    UIUtil.dispatchAllInvocationEvents()
    result.navigate(true)
    val editorManager = FileEditorManager.getInstance(myFixture.project)
    val editor = editorManager.selectedTextEditor
    EditorTestUtil.waitForLoading(editor)
    val offset = editor!!.caretModel.offset
    val document = editor.document
    val file = PsiDocumentManager.getInstance(myFixture.project).getPsiFile(document)
    Truth.assertThat(file).isNotNull()
    var element = file!!.findElementAt(offset)
    if (element is XmlToken) {
      element = element.getParent()
    }
    Truth.assertThat(element).isNotNull()
    return element!!
  }

  fun testGoToString() {
    val element = navigate("my_string", "my_s")
    Truth.assertThat(element.text).isEqualTo("\"my_string\"")
    Truth.assertThat(element.parent.parent.text).isEqualTo("<string name=\"my_string\">My string</string>")
  }

  fun testGoToId() {
    val element = navigate("my_button", "my_b")
    Truth.assertThat(element.text).isEqualTo("\"@+id/my_button\"")
  }

  fun testGoToLayout() {
    val element = navigate("my_layout", "my_l")
    Truth.assertThat(element).isInstanceOf(XmlTag::class.java)
    Truth.assertThat((element as XmlTag).name).isEqualTo("FrameLayout")
  }

  /**
   * Tries to emulate what [com.intellij.ide.actions.searcheverywhere.TrivialElementsEqualityProvider] is doing to
   * deduplicate the result list. Unfortunately some of the types involved are not public, so we cannot do exactly the same.
   */
  fun testEquality() {
    val contributor = GoToAndroidResourceContributor()
    val result: List<NavigationItem> = mutableListOf()
    contributor.addItems(myModule, "my_layout", result)
    contributor.addItems(myModule, "my_layout", result)
    Truth.assertThat(result).hasSize(2)
    Truth.assertThat(result.toSet()).hasSize(1)
  }
}