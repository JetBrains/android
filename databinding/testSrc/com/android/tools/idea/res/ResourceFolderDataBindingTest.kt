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

import com.android.SdkConstants
import com.android.ide.common.resources.DataBindingResourceType
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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
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
import java.io.File

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
    val cache = ResourceFolderRepositoryFileCacheImpl(File(fixture.tempDirPath))
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

  private fun setupWithLayoutFile(layoutContents: String) {
    registry = ResourceFolderRegistry.getInstance(project)
    psiFile = fixture.addFileToProject("res/layout/layout_with_data_binding.xml", layoutContents)
    resources = createRepository()
    UIUtil.dispatchAllInvocationEvents()
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

  @Test
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

  @Test
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

  @Test
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
      xml = "@+id/new_id"
    )
    assertViewsWithIds(
      "newId" to "android.widget.LinearLayout"
    )
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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
    return registry.get(facet, dir)
  }

  private fun getResourceDirectory(): VirtualFile {
    val resourceDirectories = ResourceFolderManager.getInstance(facet).folders
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

  private fun getInfo(): BindingLayoutInfo {
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