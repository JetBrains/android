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
package com.android.tools.idea.databinding.index

import com.android.tools.idea.res.BindingLayoutType
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.io.DataExternalizer
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class BindingXmlIndexTest {
  private val projectRule = AndroidProjectRule.onDisk()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val fixture: CodeInsightTestFixture by lazy {
    projectRule.fixture
  }

  private lateinit var psiFile: PsiFile

  private val project: Project
    get() = projectRule.project

  @Test
  fun indexDataBindingLayout() {
    val file = fixture.configureByText("layout.xml", """
      <layout>
        <data class="a.b.c.CustomBinding">
          <import type="C"/>
          <import type="Map&lt;D&gt;" alias="Dee" />
          <variable type="A" name="ex1"/>
          <variable type="B" name="ex2"/>
          <variable type="List&lt;E>" name="ex3"/>
        </data>
      </layout>
    """.trimIndent()).virtualFile
    val bindingXmlIndex = BindingXmlIndex()
    val map = bindingXmlIndex.indexer.map(FileContentImpl.createByFile(file))

    assertThat(map).hasSize(1)

    val layout = map.values.first()
    assertThat(layout.layoutType).isEqualTo(BindingLayoutType.DATA_BINDING_LAYOUT)
    assertThat(layout.customBindingName).isEqualTo("a.b.c.CustomBinding")
    assertThat(layout.imports).containsExactly(ImportInfo("C", null), ImportInfo("Map<D>", "Dee"))
    assertThat(layout.variables).containsExactly(
      VariableInfo("ex1", "A"),
      VariableInfo("ex2", "B"),
      VariableInfo("ex3", "List<E>"))
    assertThat(layout.viewIds).isEmpty()

    verifySerializationLogic(bindingXmlIndex.valueExternalizer, layout)
  }

  @Test
  fun indexViewBindingLayout() {
    val file = fixture.configureByText("layout.xml", """
      <constraint_layout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView android:id="@+id/testId2"/>
      </constraint_layout>
    """.trimIndent()).virtualFile
    val bindingXmlIndex = BindingXmlIndex()
    val map = bindingXmlIndex.indexer.map(FileContentImpl.createByFile(file))

    val layout = map.values.first()
    assertThat(layout.layoutType).isEqualTo(BindingLayoutType.VIEW_BINDING_LAYOUT)
    assertThat(layout.customBindingName).isNull()
    assertThat(layout.imports).isEmpty()
    assertThat(layout.variables).isEmpty()
    assertThat(layout.viewIds).containsExactly(ViewIdInfo("testId2", "TextView", null))

    verifySerializationLogic(bindingXmlIndex.valueExternalizer, layout)
  }

  @Test
  fun indexLayoutIds() {
    val file = fixture.configureByText("layout.xml", """
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView android:id="@+id/testId2"/>
        <TextView android:id="@id/testId3"/>
        <TextView android:id="@+id/testId1"/>
        <view android:id="@+id/testId4" android:class="com.example.class"/>
        <include android:id="@+id/testId5" android:layout="this_other_layout"/>
        <merge android:id="@+id/testId6" android:layout="this_other_layout"/>
      </layout>
    """.trimIndent()).virtualFile
    val bindingXmlIndex = BindingXmlIndex()
    val map = bindingXmlIndex.indexer.map(FileContentImpl.createByFile(file))

    val layout = map.values.first()
    assertThat(layout.viewIds.toList()).containsExactly(
      ViewIdInfo("testId2", "TextView", null),
      ViewIdInfo("testId3", "TextView", null),
      ViewIdInfo("testId1", "TextView", null),
      ViewIdInfo("testId4", "com.example.class", null),
      ViewIdInfo("testId5", "include", "this_other_layout"),
      ViewIdInfo("testId6", "merge", "this_other_layout")
    ).inOrder()

    verifySerializationLogic(bindingXmlIndex.valueExternalizer, layout)
  }

  @Test
  @RunsInEdt
  fun testAddIdToView() {
    setupWithLayoutFile(
      """
        <layout xmlns:android="http://schemas.android.com/apk/res/android">
          <LinearLayout
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
          </LinearLayout>
        </layout>
      """.trimIndent()
    )
    assertIndexedIds()
    val orientation = findChild {
      (it is XmlAttribute) && it.localName == "orientation"
    }!!
    insertXml(
      offset = orientation.textOffset,
      xml = """android:id="@+id/root_view" """
    )
    assertIndexedIds(
      ViewIdInfo("root_view", "LinearLayout", null)
    )
  }

  @Test
  @RunsInEdt
  fun testRemoveIdFromView() {
    setupWithLayoutFile(
      """
        <layout xmlns:android="http://schemas.android.com/apk/res/android">
          <LinearLayout
            android:id="@+id/root_view"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
          </LinearLayout>
        </layout>
      """.trimIndent()
    )
    assertIndexedIds(
      ViewIdInfo("root_view", "LinearLayout", null)
    )
    val idAttr = findChild {
      (it is XmlAttribute) && it.localName == "id"
    }!!
    deleteXml(idAttr.textRange)
    assertIndexedIds()
  }

  @Test
  @RunsInEdt
  fun testChangeId() {
    setupWithLayoutFile(
      """
        <layout xmlns:android="http://schemas.android.com/apk/res/android">
          <LinearLayout
            android:id="@+id/root_view"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
          </LinearLayout>
        </layout>
      """.trimIndent()
    )
    assertIndexedIds(
      ViewIdInfo("root_view", "LinearLayout", null)
    )
    val idAttr = findChild {
      (it is XmlAttribute) && it.localName == "id"
    }!!
    updateXml(
      range = (idAttr as XmlAttribute).valueElement!!.valueTextRange,
      xml = "@+id/new_id"
    )
    assertIndexedIds(
      ViewIdInfo("new_id", "LinearLayout", null)
    )
  }

  @Test
  @RunsInEdt
  fun testChangeViewType() {
    setupWithLayoutFile(
      """
        <layout xmlns:android="http://schemas.android.com/apk/res/android">
          <LinearLayout
            android:id="@+id/root_view"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent"/>
        </layout>
      """.trimIndent()
    )
    val linearLayout = findChild {
      (it is XmlTag) && it.localName == "LinearLayout"
    }!! as XmlTag

    updateXml(
      range = TextRange(linearLayout.startOffsetInParent, linearLayout.startOffsetInParent + "<LinearLayout".length),
      xml = "<TextView"
    )
    assertIndexedIds(
      ViewIdInfo("root_view", "TextView", null)
    )
  }

  @Test
  @RunsInEdt
  fun testAddViewWithId() {
    setupWithLayoutFile(
      """
        <layout xmlns:android="http://schemas.android.com/apk/res/android">
          <LinearLayout
            android:id="@+id/root_view"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
          </LinearLayout>
        </layout>
      """.trimIndent()
    )
    assertIndexedIds(
      ViewIdInfo("root_view", "LinearLayout", null)
    )
    val linearLayout = findChild {
      (it is XmlTag) && it.localName == "LinearLayout"
    }!! as XmlTag

    insertXml(
      offset = linearLayout.textRange.endOffset,
      xml = """
        <TextView
            android:id="@+id/view2"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent"
        />
        """.trimIndent()
    )
    assertIndexedIds(
      ViewIdInfo("root_view", "LinearLayout", null),
      ViewIdInfo("view2", "TextView", null)
    )
  }

  @Test
  @RunsInEdt
  fun testUnrelatedChange_attr() {
    setupWithLayoutFile(
      """
        <layout xmlns:android="http://schemas.android.com/apk/res/android">
          <LinearLayout
            android:id="@+id/root_view"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
          </LinearLayout>
        </layout>
      """.trimIndent()
    )
    assertIndexedIds(
      ViewIdInfo("root_view", "LinearLayout", null)
    )
    val orientationAttr = findChild {
      (it is XmlAttribute) && it.localName == "orientation"
    }!!
    updateXml(
      range = (orientationAttr as XmlAttribute).valueElement!!.valueTextRange,
      xml = "horizontal"
    )
    assertIndexedIds(
      ViewIdInfo("root_view", "LinearLayout", null)
    )
  }

  @Test
  @RunsInEdt
  fun testUnrelatedChange_addView() {
    setupWithLayoutFile(
      """
        <layout xmlns:android="http://schemas.android.com/apk/res/android">
          <LinearLayout
            android:id="@+id/root_view"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
          </LinearLayout>
        </layout>
      """.trimIndent()
    )
    assertIndexedIds(
      ViewIdInfo("root_view", "LinearLayout", null)
    )
    val linearLayout = findChild {
      (it is XmlTag) && it.localName == "LinearLayout"
    }!! as XmlTag

    insertXml(
      offset = linearLayout.textRange.endOffset,
      xml = """
        <TextView
            android:layout_width="fill_parent"
            android:layout_height="fill_parent"
        />
        """.trimIndent()
    )
    assertIndexedIds(
      ViewIdInfo("root_view", "LinearLayout", null)
    )
  }

  /**
   * asserts all views with viewIds.
   * Pairs are variable name to view type
   */
  private fun assertIndexedIds(vararg expected: ViewIdInfo) {
    val indexedIds = FileBasedIndex.getInstance()
      .getValues(BindingXmlIndex.NAME, BindingXmlIndex.getKeyForFile(psiFile.virtualFile),
                 GlobalSearchScope.fileScope(psiFile))
      .flatMap(IndexedLayoutInfo::viewIds)
      .toSet()

    assertThat(indexedIds).isEqualTo(expected.toSet())
  }

  private fun setupWithLayoutFile(layoutContents: String) {
    psiFile = fixture.addFileToProject("res/layout/test_layout.xml", layoutContents)
  }

  private fun findChild(predicate: (PsiElement) -> Boolean): PsiElement? {
    val processor = PsiElementProcessor.FindFilteredElement<PsiElement> {
      predicate(it)
    }
    PsiTreeUtil.processElements(psiFile, processor)
    return processor.foundElement
  }

  private fun insertXml(offset: Int, xml: String) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(psiFile)!!
    WriteCommandAction.runWriteCommandAction(project) {
      document.insertString(offset, xml)
      documentManager.commitDocument(document)
    }
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun updateXml(range: TextRange, xml: String) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(psiFile)!!
    WriteCommandAction.runWriteCommandAction(project) {
      document.replaceString(range.startOffset, range.endOffset, xml)
      documentManager.commitDocument(document)
    }
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun deleteXml(range: TextRange) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(psiFile)!!
    WriteCommandAction.runWriteCommandAction(project) {
      document.deleteString(range.startOffset, range.endOffset)
      documentManager.commitDocument(document)
    }
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun verifySerializationLogic(valueExternalizer: DataExternalizer<IndexedLayoutInfo>, layout: IndexedLayoutInfo) {
    val bytesOut = ByteArrayOutputStream()
    DataOutputStream(bytesOut).use { valueExternalizer.save(it, layout) }

    val bytesIn = ByteArrayInputStream(bytesOut.toByteArray())
    val layoutCopy = DataInputStream(bytesIn).use { valueExternalizer.read(it) }
    assertThat(layoutCopy).isEqualTo(layout)
  }
}