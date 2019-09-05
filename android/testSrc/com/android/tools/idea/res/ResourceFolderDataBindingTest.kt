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
package com.android.tools.idea.res

import com.android.SdkConstants
import com.android.ide.common.resources.DataBindingResourceType
import com.android.tools.idea.databinding.DataBindingUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import java.io.File

class ResourceFolderDataBindingTest : AndroidTestCase() {
  private lateinit var myRegistry: ResourceFolderRegistry
  private lateinit var psiFile: PsiFile
  private lateinit var resources: ResourceFolderRepository
  private lateinit var facet: AndroidFacet

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    // Use a file cache that has per-test root directories instead of sharing the system directory.
    val cache = ResourceFolderRepositoryFileCacheImpl(File(myFixture.tempDirPath))
    ResourceFolderRepositoryTest.overrideCacheService(cache, testRootDisposable)
  }

  private fun setupTestWithDataBinding() {
    myRegistry = ResourceFolderRegistry.getInstance(project)
    val file = myFixture.copyFileToProject(LAYOUT_WITH_DATA_BINDING, "res/layout/layout_with_data_binding.xml")
    psiFile = PsiManager.getInstance(project).findFile(file)!!
    resources = createRepository()
    facet = resources.facet
    assertEquals(1, resources.dataBindingResourceFiles.size)
  }

  private fun setupWithLayoutFile(layoutContents: String) {
    myRegistry = ResourceFolderRegistry.getInstance(project)
    psiFile = myFixture.addFileToProject("res/layout/layout_with_data_binding.xml", layoutContents)
    resources = createRepository()
    facet = resources.facet
    UIUtil.dispatchAllInvocationEvents()
  }

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

  fun testAddVariable_snakeCase() {
    setupTestWithDataBinding()
    insertXml(
      offset = getVariableTag("variable1").textOffset,
      xml = """
        <variable
          name="added_variable"
          type="Integer"
        />
        """.trimIndent())
    assertVariables(
      "variable1" to "String",
      "addedVariable" to "Integer"
    )
  }

  fun testRenameVariable() {
    setupTestWithDataBinding()
    updateXml(
      range = getVariableTag("variable1").getAttribute("name")!!.valueElement!!.valueTextRange,
      xml = "newName")
    assertVariables(
      "newName" to "String"
    )
  }

  fun testRenameVariable_snakeCase() {
    setupTestWithDataBinding()
    updateXml(
      range = getVariableTag("variable1").getAttribute("name")!!.valueElement!!.valueTextRange,
      xml = "variable_1")
    assertVariables(
      "variable1" to "String"
    )

    updateXml(
      range = getVariableTag("variable1").getAttribute("name")!!.valueElement!!.valueTextRange,
      xml = "variable_2")
    assertVariables(
      "variable2" to "String"
    )
  }

  fun testRenameVariable_prefix() {
    setupTestWithDataBinding()
    insertXml(
      offset = getVariableTag("variable1").getAttribute("name")!!.valueElement!!.textOffset,
      xml = "prefix_")
    assertVariables(
      "prefixVariable1" to "String"
    )
  }

  fun testRenameVariable_suffix() {
    setupTestWithDataBinding()
    insertXml(
      offset = getVariableTag("variable1").getAttribute("name")!!.valueElement!!.valueTextRange.endOffset,
      xml = "_suffix")
    assertVariables(
      "variable1Suffix" to "String"
    )
  }

  fun testRemoveVariable() {
    setupTestWithDataBinding()
    val variableTag = getVariableTag("variable1")
    deleteXml(variableTag.textRange)
    assertVariables()
  }

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

  fun testRemoveVariable_afterAddingAnother_snakeCase() {
    setupTestWithDataBinding()
    val variableTag = getVariableTag("variable1")
    insertXml(
      offset = variableTag.textRange.endOffset,
      xml = """
        <variable
          name="added_variable"
          type="Integer"
        />
        """.trimIndent()
    )
    deleteXml(variableTag.textRange)
    assertVariables(
      "addedVariable" to "Integer"
    )
  }

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

  fun testRemoveVariable_afterAddingIt_snakeCase() {
    setupTestWithDataBinding()
    val variableTag = getVariableTag("variable1")
    insertXml(
      offset = variableTag.textRange.endOffset,
      xml = """
        <variable
          name="added_variable"
          type="Integer"
        />
        """.trimIndent()
    )
    deleteXml(getVariableTag("addedVariable").textRange)
    assertVariables(
      "variable1" to "String"
    )
  }

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
    assertViewsWithIds()
    val orientation = findChild {
      (it is XmlAttribute) && it.localName == "orientation"
    }
    assertNotNull(orientation)
    insertXml(
      offset = orientation!!.textOffset,
      xml = """android:id="@+id/root_view" """
    )
    assertViewsWithIds(
      "rootView" to "android.widget.LinearLayout"
    )
  }

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
    assertViewsWithIds(
      "rootView" to "android.widget.LinearLayout"
    )
    val idAttr = findChild {
      (it is XmlAttribute) && it.localName == "id"
    }
    assertNotNull(idAttr)
    deleteXml(idAttr!!.textRange)
    assertViewsWithIds()
  }

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
    assertViewsWithIds(
      "rootView" to "android.widget.LinearLayout"
    )
    val idAttr = findChild {
      (it is XmlAttribute) && it.localName == "id"
    }
    assertNotNull(idAttr)
    updateXml(
      range = (idAttr as XmlAttribute).valueElement!!.valueTextRange,
      xml = "new_id"
    )
    assertViewsWithIds(
      "newId" to "android.widget.LinearLayout"
    )
  }

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
    } as? XmlTag
    assertNotNull(linearLayout)
    updateXml(
      range = TextRange(linearLayout!!.startOffsetInParent, linearLayout.startOffsetInParent + "<LinearLayout".length),
      xml = "<TextView"
    )
    assertViewsWithIds(
      "rootView" to "android.widget.TextView"
    )
  }

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
    assertViewsWithIds(
      "rootView" to "android.widget.LinearLayout"
    )
    val linearLayout = findChild {
      (it is XmlTag) && it.localName == "LinearLayout"
    } as? XmlTag
    assertNotNull(linearLayout)

    insertXml(
      offset = linearLayout!!.textRange.endOffset,
      xml = """
        <TextView
            android:id="@+id/view2"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent"
        />
        """.trimIndent()
    )
    assertViewsWithIds(
      "rootView" to "android.widget.LinearLayout",
      "view2" to "android.widget.TextView"
    )
  }

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
    assertViewsWithIds(
      "rootView" to "android.widget.LinearLayout"
    )
    val startModificationCnt = getInfo().modificationCount
    val orientationAttr = findChild {
      (it is XmlAttribute) && it.localName == "orientation"
    }
    assertNotNull(orientationAttr)
    updateXml(
      range = (orientationAttr as XmlAttribute).valueElement!!.valueTextRange,
      xml = "horizontal"
    )
    val endModificationCnt = getInfo().modificationCount
    assertViewsWithIds(
      "rootView" to "android.widget.LinearLayout"
    )
    assertEquals(startModificationCnt, endModificationCnt)
  }

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
    assertViewsWithIds(
      "rootView" to "android.widget.LinearLayout"
    )
    val startModificationCnt = getInfo().modificationCount
    val linearLayout = findChild {
      (it is XmlTag) && it.localName == "LinearLayout"
    } as? XmlTag
    assertNotNull(linearLayout)

    insertXml(
      offset = linearLayout!!.textRange.endOffset,
      xml = """
        <TextView
            android:layout_width="fill_parent"
            android:layout_height="fill_parent"
        />
        """.trimIndent()
    )
    val endModificationCnt = getInfo().modificationCount
    assertViewsWithIds(
      "rootView" to "android.widget.LinearLayout"
    )
    assertEquals(startModificationCnt, endModificationCnt)
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

  fun testInitialParsing() {
    setupTestWithDataBinding()
    assertVariables(
      "variable1" to "String"
    )
    assertImports(
      "p1.p2.import1" to null,
      "p1.p2.import2" to "i2"
    )
    assertViewsWithIds(
      "magicView" to "foo.bar.Magic",
      "normalViewTag" to "android.view.View",
      "surfaceView1" to "android.view.SurfaceView",
      "textView1" to "android.widget.TextView",
      "viewTag" to "android.view.ViewGroup",
      "webView1" to "android.webkit.WebView"
    )
  }

  private fun createRepository(): ResourceFolderRepository {
    val dir = getResourceDirectory()
    return myRegistry.get(myFacet, dir)
  }

  private fun getResourceDirectory(): VirtualFile {
    val resourceDirectories = ResourceFolderManager.getInstance(myFacet).folders
    assertNotNull(resourceDirectories)
    assertSize(1, resourceDirectories)
    return resourceDirectories[0]
  }

  /**
   * asserts all variables in the xml.
   * Pairs are name to type
   */
  private fun assertVariables(vararg expected: Pair<String, String>) {
    val variablesInInfo = getInfo()
      .getItems(DataBindingResourceType.VARIABLE)
      .values
      .map {
        Pair(it.name, it.getExtra(SdkConstants.ATTR_TYPE))
      }.toSet()
    assertEquals(expected.toSet(), variablesInInfo)
  }

  /**
   * asserts all imports in the xml.
   * Pairs are type to alias
   */
  private fun assertImports(vararg expected: Pair<String, String?>) {
    val importsInInfo = getInfo()
      .getItems(DataBindingResourceType.IMPORT)
      .values
      .map {
        Pair(it.getExtra(SdkConstants.ATTR_TYPE), it.getExtra(SdkConstants.ATTR_ALIAS))
      }
      .toSet()
    assertEquals(expected.toSet(), importsInInfo)
  }

  /**
   * asserts all views with ids.
   * Pairs are variable name to view type
   */
  private fun assertViewsWithIds(vararg expected: Pair<String, String>) {
    val viewsInInfo = getInfo()
      .viewsWithIds
      .map {
        Pair(it.name, DataBindingUtil.resolveViewPsiType(it, facet)!!.canonicalText)
      }
      .toSet()
    assertEquals(expected.toSet(), viewsInInfo)
  }

  private fun getInfo(): DataBindingInfo {
    val appPackage = DataBindingUtil.getGeneratedPackageName(facet)
    return resources.dataBindingResourceFiles["$appPackage.databinding.LayoutWithDataBindingBinding"]!!
  }

  private fun getVariableTag(name: String): XmlTag {
    val variable = getInfo()
      .getItems(DataBindingResourceType.VARIABLE)
      .values
      .firstOrNull {
        it.name == name
      }
    assertNotNull("cannot find variable with name $name", variable)
    return variable!!.xmlTag
  }

  private fun findChild(predicate: (PsiElement) -> Boolean): PsiElement? {
    val processor = PsiElementProcessor.FindFilteredElement<PsiElement> {
      predicate(it)
    }
    PsiTreeUtil.processElements(psiFile, processor)
    return processor.foundElement
  }

  companion object {
    private const val LAYOUT_WITH_DATA_BINDING = "resourceRepository/layout_with_data_binding.xml"
  }
}