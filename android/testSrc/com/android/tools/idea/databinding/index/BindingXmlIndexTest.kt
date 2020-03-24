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

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
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

    val data = map.values.first()
    assertThat(data.layoutType).isEqualTo(BindingLayoutType.DATA_BINDING_LAYOUT)
    assertThat(data.rootTag).isEqualTo("layout")
    assertThat(data.customBindingName).isEqualTo("a.b.c.CustomBinding")
    assertThat(data.imports).containsExactly(ImportData("C", null), ImportData("Map<D>", "Dee"))
    assertThat(data.variables).containsExactly(
      VariableData("ex1", "A"),
      VariableData("ex2", "B"),
      VariableData("ex3", "List<E>"))
    assertThat(data.viewIds).isEmpty()

    verifySerializationLogic(bindingXmlIndex.valueExternalizer, data)
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

    val data = map.values.first()
    assertThat(data.layoutType).isEqualTo(BindingLayoutType.PLAIN_LAYOUT)
    assertThat(data.rootTag).isEqualTo("constraint_layout")
    assertThat(data.customBindingName).isNull()
    assertThat(data.viewBindingIgnore).isFalse()
    assertThat(data.imports).isEmpty()
    assertThat(data.variables).isEmpty()
    assertThat(data.viewIds).containsExactly(ViewIdData("testId2", "TextView", null))

    verifySerializationLogic(bindingXmlIndex.valueExternalizer, data)
  }

  @Test
  fun indexBindingLayoutSkipsComments() {
    val file = fixture.configureByText(
      "layout.xml",
      // language=XML
      """
      <!-- Leading comment -->
      <constraint_layout xmlns:android="http://schemas.android.com/apk/res/android">
        <!-- Normally & unescaped isn't allowed but should be OK in a comment -->
        <TextView android:id="@+id/testId1"/>
        <!-- Comment in the middle -->
        <Button android:id="@+id/testId2"/> <!-- Same line comment -->
      </constraint_layout>
      <!-- Trailing comment that we "forgot" to close
    """.trimIndent()).virtualFile
    val bindingXmlIndex = BindingXmlIndex()
    val map = bindingXmlIndex.indexer.map(FileContentImpl.createByFile(file))

    val data = map.values.first()
    assertThat(data.layoutType).isEqualTo(BindingLayoutType.PLAIN_LAYOUT)
    assertThat(data.rootTag).isEqualTo("constraint_layout")
    assertThat(data.customBindingName).isNull()
    assertThat(data.viewBindingIgnore).isFalse()
    assertThat(data.imports).isEmpty()
    assertThat(data.variables).isEmpty()
    assertThat(data.viewIds).containsExactly(ViewIdData("testId1", "TextView", null), ViewIdData("testId2", "Button", null))

    verifySerializationLogic(bindingXmlIndex.valueExternalizer, data)
  }

  @Test
  fun indexViewBindingIgnoreLayout() {
    val file = fixture.configureByText("layout.xml", """
      <constraint_layout xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:tools="http://schemas.android.com/tools"
        tools:viewBindingIgnore="true">
        <TextView android:id="@+id/testId2"/>
      </constraint_layout>
    """.trimIndent()).virtualFile
    val bindingXmlIndex = BindingXmlIndex()
    val map = bindingXmlIndex.indexer.map(FileContentImpl.createByFile(file))

    val data = map.values.first()
    assertThat(data.layoutType).isEqualTo(BindingLayoutType.PLAIN_LAYOUT)
    assertThat(data.rootTag).isEqualTo("constraint_layout")
    assertThat(data.customBindingName).isNull()
    assertThat(data.viewBindingIgnore).isTrue()
    assertThat(data.imports).isEmpty()
    assertThat(data.variables).isEmpty()
    assertThat(data.viewIds).containsExactly(ViewIdData("testId2", "TextView", null))

    verifySerializationLogic(bindingXmlIndex.valueExternalizer, data)
  }

  @Test
  fun indexLayoutIds() {
    val file = fixture.configureByText("layout.xml", """
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView android:id="@+id/testId2"/>
        <TextView android:id="@id/testId3"/>
        <TextView android:id="@+id/testId1"/>
        <Button android:id="@id/android:testId7"/>
        <CheckBox android:id="@android:id/testId8"/>
        <DatePicker android:id="@+id/android:testId9"/>
        <ProgressBar android:id="@android:id/android:testId10"/>
        <NumberPicker android:id="invalid"/>
        <view android:id="@+id/testId4" android:class="com.example.class"/>
        <include android:id="@+id/testId5" layout="this_other_layout"/>
        <merge android:id="@+id/testId6" layout="this_other_layout"/>
      </layout>
    """.trimIndent()).virtualFile
    val bindingXmlIndex = BindingXmlIndex()
    val map = bindingXmlIndex.indexer.map(FileContentImpl.createByFile(file))

    val data = map.values.first()
    assertThat(data.viewIds.toList()).containsExactly(
      ViewIdData("testId2", "TextView", null),
      ViewIdData("testId3", "TextView", null),
      ViewIdData("testId1", "TextView", null),
      ViewIdData("testId7", "Button", null),
      ViewIdData("testId8", "CheckBox", null),
      ViewIdData("testId9", "DatePicker", null),
      // TODO(b/141013448): This should just be "testId10", update after ResourceUrl.parse is fixed
      ViewIdData("android:testId10", "ProgressBar", null),
      ViewIdData("testId4", "com.example.class", null),
      ViewIdData("testId5", "include", "this_other_layout"),
      ViewIdData("testId6", "merge", "this_other_layout")
    ).inOrder()

    verifySerializationLogic(bindingXmlIndex.valueExternalizer, data)
  }

  @Test
  fun canIndexVeryLargeXmlFiles() {
    // XML parsing happens in chunks of ~8K, so make sure we feed enough text to stress the system
    // that patches the chunks together.
    val layoutXml = buildString {
      append("""<layout xmlns:android="http://schemas.android.com/apk/res/android">""")
      for (i in 1..20000) {
        append("""<TextView android:id="@+id/testId$i"/>""")
      }
      append("</layout>")
    }
    val file = fixture.configureByText("layout.xml", layoutXml).virtualFile

    val bindingXmlIndex = BindingXmlIndex()
    val map = bindingXmlIndex.indexer.map(FileContentImpl.createByFile(file))

    val data = map.values.first()
    // Make sure we didn't drop a single item
    assertThat(data.viewIds.size).isEqualTo(20000)
    assertThat(data.viewIds.first().id).isEqualTo("testId1")
    assertThat(data.viewIds.last().id).isEqualTo("testId20000")

    verifySerializationLogic(bindingXmlIndex.valueExternalizer, data)
  }

  @Test
  fun indexDoesNotThrowExceptionIfEncounteringUnrelatedXml() {
    val file = fixture.configureByText(
      "not-really-a-layout.xml",
      // language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              package="com.example.layout.binding">
            <application android:label="BindingXmlIndex Test" />
        </manifest>
    """.trimIndent()).virtualFile

    val bindingXmlIndex = BindingXmlIndex()
    bindingXmlIndex.indexer.map(FileContentImpl.createByFile(file))
    // If we got here, no exception was thrown
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
      ViewIdData("root_view", "LinearLayout", null)
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
      ViewIdData("root_view", "LinearLayout", null)
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
      ViewIdData("root_view", "LinearLayout", null)
    )
    val idAttr = findChild {
      (it is XmlAttribute) && it.localName == "id"
    }!!
    updateXml(
      range = (idAttr as XmlAttribute).valueElement!!.valueTextRange,
      xml = "@+id/new_id"
    )
    assertIndexedIds(
      ViewIdData("new_id", "LinearLayout", null)
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
      ViewIdData("root_view", "TextView", null)
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
      ViewIdData("root_view", "LinearLayout", null)
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
      ViewIdData("root_view", "LinearLayout", null),
      ViewIdData("view2", "TextView", null)
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
      ViewIdData("root_view", "LinearLayout", null)
    )
    val orientationAttr = findChild {
      (it is XmlAttribute) && it.localName == "orientation"
    }!!
    updateXml(
      range = (orientationAttr as XmlAttribute).valueElement!!.valueTextRange,
      xml = "horizontal"
    )
    assertIndexedIds(
      ViewIdData("root_view", "LinearLayout", null)
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
      ViewIdData("root_view", "LinearLayout", null)
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
      ViewIdData("root_view", "LinearLayout", null)
    )
  }

  /**
   * asserts all views with viewIds.
   * Pairs are variable name to view type
   */
  private fun assertIndexedIds(vararg expected: ViewIdData) {
    val indexedIds = BindingXmlIndex.getDataForFile(psiFile)!!.viewIds.toSet()
    assertThat(indexedIds).isEqualTo(expected.toSet())
  }

  private fun setupWithLayoutFile(layoutContents: String) {
    psiFile = fixture.addFileToProject("res/layout/test_layout.xml", layoutContents)
  }

  private fun findChild(predicate: (PsiElement) -> Boolean): PsiElement? {
    return SyntaxTraverser.psiTraverser(psiFile).traverse().filter(predicate).firstOrNull()
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

  private fun verifySerializationLogic(valueExternalizer: DataExternalizer<BindingXmlData>, data: BindingXmlData) {
    val bytesOut = ByteArrayOutputStream()
    DataOutputStream(bytesOut).use { valueExternalizer.save(it, data) }

    val bytesIn = ByteArrayInputStream(bytesOut.toByteArray())
    val dataCopy = DataInputStream(bytesIn).use { valueExternalizer.read(it) }
    assertThat(dataCopy).isEqualTo(data)
  }
}