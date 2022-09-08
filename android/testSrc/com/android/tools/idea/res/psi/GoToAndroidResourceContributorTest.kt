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

import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.ide.util.ModuleRendererFactory
import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlToken
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [GoToAndroidResourceContributor].
 */
@RunsInEdt
class GoToAndroidResourceContributorTest {
  val projectRule = AndroidProjectRule.withAndroidModel(AndroidProjectBuilder()).named(this::class.simpleName)

  @ClassRule
  val disposableRule = DisposableRule()

  @get:Rule
  val chain = RuleChain(projectRule, EdtRule())

  @Before
  fun setUp() {
    projectRule.fixture.addFileToProject("src/main/res/values/strings.xml", """
        <resources>
          <string name="my_string">My string</string>
        </resources>
        """.trimIndent())
    projectRule.fixture.addFileToProject("src/main/res/layout/my_layout.xml", """
        <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
          android:id="@+id/activity_main"
          android:layout_width="match_parent"
          android:layout_height="match_parent">
          <View
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_below="@+id/my_view"/>
          <View
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_below="@+id/my_view"/>
          <View
            android:id="@+id/my_view"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"/>
        </RelativeLayout>
        """.trimIndent())
  }

  private fun navigate(name: String, pattern: String, expectedNumberOfResults: Int = 1, selectResult: Int = 0): PsiElement {
    val model = GotoSymbolModel2(projectRule.project, disposableRule.disposable)
    val searchResults = model.getElementsByName(name, false, pattern)
    assertThat(searchResults).hasLength(expectedNumberOfResults)
    val result = searchResults[selectResult]
    assertThat(result).isInstanceOf(NavigationItem::class.java)
    val textWithIcon = ModuleRendererFactory.findInstance(result).getModuleTextWithIcon(result)
    assertThat(textWithIcon!!.text).isEqualTo(projectRule.module.getMainModule().name)
    assertThat(textWithIcon.icon).isEqualTo(AllIcons.Nodes.Module)
    assertThat((result as NavigationItem).presentation!!.getIcon(false)).isNotNull()
    UIUtil.dispatchAllInvocationEvents()
    result.navigate(true)
    val editorManager = FileEditorManager.getInstance(projectRule.project)
    val editor = editorManager.selectedTextEditor
    EditorTestUtil.waitForLoading(editor)
    val offset = editor!!.caretModel.offset
    val document = editor.document
    val file = PsiDocumentManager.getInstance(projectRule.project).getPsiFile(document)
    assertThat(file).isNotNull()
    var element = file!!.findElementAt(offset)
    if (element is XmlToken) {
      element = element.getParent()
    }
    assertThat(element).isNotNull()
    return element!!
  }

  @Test
  fun testGoToString() {
    val element = navigate("my_string", "my_s")
    assertThat(element.text).isEqualTo("\"my_string\"")
    assertThat(element.parent.parent.text).isEqualTo("<string name=\"my_string\">My string</string>")
  }

  @Test
  fun testGoToStringDefinedInTwoPlaces() {
    projectRule.fixture.addFileToProject("src/debug/res/values/strings.xml", """
        <resources>
          <string name="my_string">My debug string</string>
        </resources>
        """.trimIndent())
    val element = navigate("my_string", "my_s", expectedNumberOfResults = 2, selectResult = 0)
    assertThat(element.text).isEqualTo("\"my_string\"")
    assertThat(element.parent.parent.text).isEqualTo("<string name=\"my_string\">My debug string</string>")
  }

  @Test
  fun testGoToId() {
    val element = navigate("my_view", "my_v")
    assertThat(element.text).isEqualTo("\"@+id/my_view\"")
    assertThat((element.parent as XmlAttribute).name).isEqualTo("android:id")
  }

  @Test
  fun testGoToLayout() {
    val element = navigate("my_layout", "my_l")
    assertThat(element).isInstanceOf(XmlTag::class.java)
    assertThat((element as XmlTag).name).isEqualTo("RelativeLayout")
  }

  /**
   * Tries to emulate what [com.intellij.ide.actions.searcheverywhere.TrivialElementsEqualityProvider] is doing to
   * deduplicate the result list. Unfortunately some of the types involved are not public, so we cannot do exactly the same.
   */
  @Test
  fun testEquality() {
    val contributor = GoToAndroidResourceContributor()
    val result: List<NavigationItem> = mutableListOf()
    contributor.addItems(projectRule.module, "my_layout", result)
    contributor.addItems(projectRule.module, "my_layout", result)
    assertThat(result).hasSize(2)
    assertThat(result.toSet()).hasSize(1)
  }
}