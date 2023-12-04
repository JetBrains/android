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

import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.databinding.util.findVariableTag
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private const val LAYOUT_WITH_DATA_BINDING = "res/layout_with_data_binding.xml"

/**
 * Tests that verify that the entry contents of a binding index are kept up-to-date as their
 * associated layout files are added, updated, and deleted.
 */
@RunsInEdt
class BindingXmlIndexEntriesTest {
  private var projectRule = AndroidProjectRule.withSdk().initAndroid(true)

  // ProjectRule initialization must not happen on the EDT thread
  @get:Rule var chainRule: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  private lateinit var psiFile: PsiFile

  @Before
  fun setUp() {
    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT

    val file =
      fixture.copyFileToProject(LAYOUT_WITH_DATA_BINDING, "res/layout/layout_with_data_binding.xml")
    psiFile = PsiManager.getInstance(project).findFile(file)!!
  }

  @Test
  fun testAddVariable() {
    insertXml(
      offset = getVariableTag("variable1").textOffset,
      xml =
        """
        <variable
          name="added"
          type="Integer"
        />
        """
          .trimIndent()
    )
    assertVariables("variable1" to "String", "added" to "Integer")
  }

  @Test
  fun testRenameVariable() {
    updateXml(
      range = getVariableTag("variable1").getAttribute("name")!!.valueElement!!.valueTextRange,
      xml = "newName"
    )
    assertVariables("newName" to "String")
  }

  @Test
  fun testRenameVariable_prefix() {
    insertXml(
      offset = getVariableTag("variable1").getAttribute("name")!!.valueElement!!.textOffset,
      xml = "prefix_"
    )
    assertVariables("prefix_variable1" to "String")
  }

  @Test
  fun testRenameVariable_suffix() {
    insertXml(
      offset =
        getVariableTag("variable1").getAttribute("name")!!.valueElement!!.valueTextRange.endOffset,
      xml = "_suffix"
    )
    assertVariables("variable1_suffix" to "String")
  }

  @Test
  fun testRemoveVariable() {
    val variableTag = getVariableTag("variable1")
    deleteXml(variableTag.textRange)
    assertVariables()
  }

  @Test
  fun testRemoveVariable_afterAddingAnother() {
    val variableTag = getVariableTag("variable1")
    insertXml(
      offset = variableTag.textRange.endOffset,
      xml =
        """
        <variable
          name="added"
          type="Integer"
        />
        """
          .trimIndent()
    )
    deleteXml(variableTag.textRange)
    assertVariables("added" to "Integer")
  }

  @Test
  fun testRemoveVariable_afterAddingIt() {
    val variableTag = getVariableTag("variable1")
    insertXml(
      offset = variableTag.textRange.endOffset,
      xml =
        """
        <variable
          name="added"
          type="Integer"
        />
        """
          .trimIndent()
    )
    deleteXml(getVariableTag("added").textRange)
    assertVariables("variable1" to "String")
  }

  @Test
  fun testUpdateType() {
    updateXml(
      range = getVariableTag("variable1").getAttribute("type")!!.valueElement!!.valueTextRange,
      xml = "Float"
    )
    assertVariables("variable1" to "Float")
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

  @Test
  fun testInitialParsing() {
    assertVariables("variable1" to "String")
    assertImports("p1.p2.import1" to null, "p1.p2.import2" to "i2")
  }

  /**
   * Asserts all variables declared in the xml are found up-to-date in the [BindingXmlIndex]. See
   * also: [getIndexEntry]
   *
   * Note: Pairs are name to type.
   */
  private fun assertVariables(vararg expected: Pair<String, String>) {
    val variables =
      getIndexEntry().data.variables.map { variable -> variable.name to variable.type }.toSet()
    assertEquals(expected.toSet(), variables)
  }

  /**
   * Asserts all imports declared in the xml are found up-to-date in the current
   * [BindingXmlIndex.Entry] See also: [getIndexEntry]
   *
   * Note: Pairs are type to alias.
   */
  private fun assertImports(vararg expected: Pair<String, String?>) {
    val imports = getIndexEntry().data.imports.map { import -> import.type to import.alias }.toSet()
    assertEquals(expected.toSet(), imports)
  }

  /** Returns the index entry that corresponds to the single layout declared for this test. */
  private fun getIndexEntry(): BindingXmlIndex.Entry {
    return BindingXmlIndex.getEntriesForLayout(project, "layout_with_data_binding").first()
  }

  private fun getVariableTag(name: String): XmlTag {
    val indexEntry = getIndexEntry()
    val xmlFile = DataBindingUtil.findXmlFile(project, indexEntry.file)!!
    val variable = indexEntry.data.findVariable(name)
    assertNotNull("cannot find variable with name $name", variable)
    val variableTag = xmlFile.findVariableTag(variable!!.name)
    assertNotNull("Cannot find XML tag for variable with name $name", variableTag)
    return variableTag!!
  }
}
