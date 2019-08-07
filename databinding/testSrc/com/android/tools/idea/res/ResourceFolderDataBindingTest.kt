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
package com.android.tools.idea.res

import com.android.tools.idea.databinding.DataBindingUtil
import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.res.binding.BindingLayoutInfo
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.facet.ResourceFolderManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.picocontainer.MutablePicoContainer
import java.nio.file.Paths

@RunsInEdt
class ResourceFolderDataBindingTest {
  private var projectRule = AndroidProjectRule.withSdk().initAndroid(true)

  // ProjectRule initialization must not happen on the EDT thread
  @get:Rule
  var chainRule: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  private val project
    get() = projectRule.project
  private val fixture
    get() = projectRule.fixture
  private val facet
    get() = projectRule.module.androidFacet!!

  private lateinit var oldFileCacheService: ResourceFolderRepositoryFileCache
  private lateinit var registry: ResourceFolderRegistry
  private lateinit var psiFile: PsiFile
  private lateinit var resources: ResourceFolderRepository

  @Before
  fun setUp() {
    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT

    // Use a file cache that has per-test root directories instead of sharing the system directory.
    val cache = ResourceFolderRepositoryFileCacheImpl(Paths.get(fixture.tempDirPath))
    oldFileCacheService = overrideCacheService(cache)
  }

  @After
  fun tearDown() {
    overrideCacheService(oldFileCacheService)
  }

  private fun setupTestWithDataBinding() {
    registry = ResourceFolderRegistry.getInstance(project)
    val file = fixture.copyFileToProject(LAYOUT_WITH_DATA_BINDING, "res/layout/layout_with_data_binding.xml")
    psiFile = PsiManager.getInstance(project).findFile(file)!!
    resources = createRepository()
    assertEquals(1, resources.dataBindingResourceFiles.size)
  }

  @Test
  fun testAddVariable() {
    setupTestWithDataBinding()
    insertXml(
      offset = getVariableTag("variable1").textOffset,
      xml = """
        <variable
          name="added"
          type="Integer"
        />
        """.trimIndent())
    assertVariables(
      "variable1" to "String",
      "added" to "Integer"
    )
  }

  @Test
  fun testRenameVariable() {
    setupTestWithDataBinding()
    updateXml(
      range = getVariableTag("variable1").getAttribute("name")!!.valueElement!!.valueTextRange,
      xml = "newName")
    assertVariables(
      "newName" to "String"
    )
  }

  @Test
  fun testRenameVariable_prefix() {
    setupTestWithDataBinding()
    insertXml(
      offset = getVariableTag("variable1").getAttribute("name")!!.valueElement!!.textOffset,
      xml = "prefix_")
    assertVariables(
      "prefix_variable1" to "String"
    )
  }

  @Test
  fun testRenameVariable_suffix() {
    setupTestWithDataBinding()
    insertXml(
      offset = getVariableTag("variable1").getAttribute("name")!!.valueElement!!.valueTextRange.endOffset,
      xml = "_suffix")
    assertVariables(
      "variable1_suffix" to "String"
    )
  }

  @Test
  fun testRemoveVariable() {
    setupTestWithDataBinding()
    val variableTag = getVariableTag("variable1")
    deleteXml(variableTag.textRange)
    assertVariables()
  }

  @Test
  fun testRemoveVariable_afterAddingAnother() {
    setupTestWithDataBinding()
    val variableTag = getVariableTag("variable1")
    insertXml(
      offset = variableTag.textRange.endOffset,
      xml = """
        <variable
          name="added"
          type="Integer"
        />
        """.trimIndent()
    )
    deleteXml(variableTag.textRange)
    assertVariables(
      "added" to "Integer"
    )
  }

  @Test
  fun testRemoveVariable_afterAddingIt() {
    setupTestWithDataBinding()
    val variableTag = getVariableTag("variable1")
    insertXml(
      offset = variableTag.textRange.endOffset,
      xml = """
        <variable
          name="added"
          type="Integer"
        />
        """.trimIndent()
    )
    deleteXml(getVariableTag("added").textRange)
    assertVariables(
      "variable1" to "String"
    )
  }

  @Test
  fun testUpdateType() {
    setupTestWithDataBinding()
    updateXml(
      range = getVariableTag("variable1").getAttribute("type")!!.valueElement!!.valueTextRange,
      xml = "Float"
    )
    assertVariables(
      "variable1" to "Float"
    )
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
    setupTestWithDataBinding()
    assertVariables(
      "variable1" to "String"
    )
    assertImports(
      "p1.p2.import1" to null,
      "p1.p2.import2" to "i2"
    )
  }

  private fun createRepository(): ResourceFolderRepository {
    val dir = getResourceDirectory()
    return registry.get(facet, dir)
  }

  private fun getResourceDirectory(): VirtualFile {
    val resourceDirectories = ResourceFolderManager.getInstance(facet).folders
    assertNotNull(resourceDirectories)
    assertSize(1, resourceDirectories)
    return resourceDirectories[0]
  }

  /**
   * Asserts all variables declared in the xml are found up-to-date in the current [BindingLayoutInfo].
   * See also: [getInfo]
   *
   * Note: Pairs are name to type.
   */
  private fun assertVariables(vararg expected: Pair<String, String>) {
    val variables = getInfo()
      .xml
      .variables
      .map { variable -> variable.name to variable.type }
      .toSet()
    assertEquals(expected.toSet(), variables)
  }

  /**
   * Asserts all imports declared in the xml are found up-to-date in the current [BindingLayoutInfo]
   * See also: [getInfo]
   *
   * Note: Pairs are type to alias.
   */
  private fun assertImports(vararg expected: Pair<String, String?>) {
    val imports = getInfo()
      .xml
      .imports
      .map { import -> import.type to import.alias }
      .toSet()
    assertEquals(expected.toSet(), imports)
  }

  private fun getInfo(): BindingLayoutInfo {
    val appPackage = DataBindingUtil.getGeneratedPackageName(facet)
    return resources.dataBindingResourceFiles
      .flatMap { group -> group.layouts }
      .first { layout -> layout.qualifiedName == "$appPackage.databinding.LayoutWithDataBindingBinding" }
  }

  private fun getVariableTag(name: String): XmlTag {
    val info = getInfo()
    val variable = info.xml.variables.firstOrNull { it.name == name }
    assertNotNull("cannot find variable with name $name", variable)
    val variableTag = info.psi.findVariableTag(variable!!)
    assertNotNull("cannot find XML tag for variable with name $name", variableTag)
    return variableTag!!
  }

  companion object {
    private const val LAYOUT_WITH_DATA_BINDING = "res/layout_with_data_binding.xml"

    private fun overrideCacheService(newCache: ResourceFolderRepositoryFileCache): ResourceFolderRepositoryFileCache {
      val applicationContainer = ApplicationManager.getApplication().picoContainer as MutablePicoContainer

      // Use a file cache that has per-test root directories instead of sharing the system directory.
      // Swap out cache services. We have to be careful. All tests share the same Application and PicoContainer.
      val oldCache = applicationContainer.getComponentInstance(
        ResourceFolderRepositoryFileCache::class.java.name) as ResourceFolderRepositoryFileCache
      applicationContainer.unregisterComponent(ResourceFolderRepositoryFileCache::class.java.name)
      applicationContainer.registerComponentInstance(ResourceFolderRepositoryFileCache::class.java.name, newCache)
      return oldCache
    }
  }
}