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
package com.android.tools.idea.ui.resourcemanager

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.resources.ResourceType
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.adtui.swing.laf.HeadlessListUI
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.ui.resourcemanager.explorer.AssetListView
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceDetailView
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerView
import com.android.tools.idea.ui.resourcemanager.widget.OverflowingTabbedPaneWrapper
import com.google.common.truth.Truth.assertThat
import com.intellij.configurationStore.runInAllowSaveMode
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.WaitFor
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

private const val WAIT_TIMEOUT = 3000

@RunsInEdt
class ResourcePickerDialogTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  @get:Rule
  val edtRule = EdtRule()

  private lateinit var pickerDialog: ResourcePickerDialog

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = getTestDataDirectory()
    projectRule.fixture.copyFileToProject(FN_ANDROID_MANIFEST_XML, FN_ANDROID_MANIFEST_XML)
    projectRule.fixture.copyDirectoryToProject("res/", "res/")
    projectRule.fixture.loadNewFile("res/values/strings.xml", """<resources><string name="app_name">App</string></resources>""")
    projectRule.waitForResourceRepositoryUpdates()
    enableHeadlessDialogs(projectRule.testRootDisposable)
    pickerDialog = createResourcePickerDialog(false)
    Disposer.register(projectRule.project, pickerDialog.disposable)
  }

  @Test
  fun updateSelectedResource() {
    // Save project to guarantee project.getProjectFile() is non-null.
    runInAllowSaveMode { projectRule.project.save() }
    pickerDialog = createResourcePickerDialog(false)
    Disposer.register(projectRule.project, pickerDialog.disposable)
    val explorerView = UIUtil.findComponentOfType(pickerDialog.resourceExplorerPanel, ResourceExplorerView::class.java)!!

    waitAndAssert<AssetListView>(explorerView) {
      it != null && it.model.size > 0
    }

    val list = UIUtil.findComponentOfType(explorerView, AssetListView::class.java)!!
    list.setUI(HeadlessListUI())

    var point = list.indexToLocation(0)
    simulateMouseClick(list, point, 1)
    assertThat(pickerDialog.resourceName).isEqualTo("@drawable/png")

    point = list.indexToLocation(1)
    simulateMouseClick(list, point, 1)
    assertThat(pickerDialog.resourceName).isEqualTo("@drawable/vector_drawable")
  }

  @Test
  fun selectResource() {
    val explorerView = UIUtil.findComponentOfType(pickerDialog.resourceExplorerPanel, ResourceExplorerView::class.java)!!
    val list = UIUtil.findComponentOfType(explorerView, AssetListView::class.java)!!
    list.setUI(HeadlessListUI())
    val point = list.indexToLocation(0)
    // Simulate double clicking on an asset.
    simulateMouseClick(list, point, 2)
    assertThat(pickerDialog.resourceName).isEqualTo("@drawable/png")
  }

  @Test
  fun selectSampleDataResource() {
    pickerDialog = createResourcePickerDialog(true)
    Disposer.register(projectRule.project, pickerDialog.disposable)
    val explorerView = UIUtil.findComponentOfType(pickerDialog.resourceExplorerPanel, ResourceExplorerView::class.java)!!

    var sampleDataList: AssetListView? = null
    val waitForSampleDataList = object : WaitFor(WAIT_TIMEOUT) {
      public override fun condition(): Boolean {
        val listViews = UIUtil.findComponentsOfType(explorerView, AssetListView::class.java)
        listViews.forEach { listView ->
          if (listView.model.getElementAt(0).assets.first().resourceItem.type == ResourceType.SAMPLE_DATA) {
            // Make sure there are actually sample data resources being displayed.
            sampleDataList = listView
            return true
          }
        }
        return false
      }
    }
    assertThat(waitForSampleDataList.isConditionRealized).isTrue()

    sampleDataList!!.setUI(HeadlessListUI())
    val point = sampleDataList!!.indexToLocation(0)
    simulateMouseClick(sampleDataList!!, point, 2)
    // We don't know for a fact what resource will come first, so just check that the format is correct.
    assertThat(pickerDialog.resourceName).startsWith("@tools:sample/")
  }

  @Test
  fun selectMultipleConfigurationResource() {
    val resDir = projectRule.fixture.copyDirectoryToProject("res/", "res/")
    runInEdtAndWait {
      runWriteAction {
        // Add a second configuration to the "png.png" resource.
        val hdpiDir = resDir.createChildDirectory(this, "drawable-hdpi")
        resDir.findFileByRelativePath("drawable/png.png")!!.copy(this, hdpiDir, "png.png")
      }
    }
    setUp()
    val explorerView = UIUtil.findComponentOfType(pickerDialog.resourceExplorerPanel, ResourceExplorerView::class.java)!!
    val list = UIUtil.findComponentOfType(explorerView, AssetListView::class.java)!!
    list.setUI(HeadlessListUI())
    val point = list.indexToLocation(0)
    // First resource should now have 2 versions.
    assertThat(list.model.getElementAt(0).assets).hasSize(2)
    // Simulate double clicking on the first resource.
    simulateMouseClick(list, point, 2)
    // Should properly select the resource (instead of showing the detailed view).
    assertThat(pickerDialog.resourceName).isEqualTo("@drawable/png")
    assertNull(UIUtil.findComponentOfType(explorerView, ResourceDetailView::class.java))
  }

  @Test
  fun openWithInitialResource() {
    pickerDialog = createResourcePickerDialog(
      false, "@drawable/png", setOf(ResourceType.STRING, ResourceType.DRAWABLE), ResourceType.STRING)
    Disposer.register(projectRule.project, pickerDialog.disposable)
    val explorerView = UIUtil.findComponentOfType(pickerDialog.resourceExplorerPanel, ResourceExplorerView::class.java)!!

    // Selected tab should be Drawable, from the type of the given resource.
    waitAndAssert<OverflowingTabbedPaneWrapper>(explorerView) {
      it != null && (it.tabbedPane.selectedIndex == it.tabbedPane.indexOfTab(ResourceType.DRAWABLE.displayName))
    }
    // First tab should be Drawable instead of String, to match the Resource Manager tab order.
    waitAndAssert<OverflowingTabbedPaneWrapper>(explorerView) {
      it != null && it.tabbedPane.indexOfTab(ResourceType.STRING.displayName) == 1
    }

    waitAndAssert<AssetListView>(explorerView) { it != null && pickerDialog.resourceName == "@drawable/png" }
  }

  @Test
  fun openWithPreferredResourceType() {
    pickerDialog = createResourcePickerDialog(false, null, setOf(ResourceType.STRING, ResourceType.DRAWABLE), ResourceType.STRING)
    Disposer.register(projectRule.project, pickerDialog.disposable)
    val explorerView = UIUtil.findComponentOfType(pickerDialog.resourceExplorerPanel, ResourceExplorerView::class.java)!!

    // Selected tab should be String from the given preferred ResourceType.
    waitAndAssert<OverflowingTabbedPaneWrapper>(explorerView) {
      it != null && (it.tabbedPane.selectedIndex == it.tabbedPane.indexOfTab(ResourceType.STRING.displayName))
    }
    // First tab should be Drawable instead of String, to match the Resource Manager tab order.
    waitAndAssert<OverflowingTabbedPaneWrapper>(explorerView) {
      it != null && it.tabbedPane.indexOfTab(ResourceType.STRING.displayName) == 1
    }

    waitAndAssert<AssetListView>(explorerView) { it != null && pickerDialog.resourceName == null }
  }

  @Test
  fun manualRefreshRequiredOnExternalResourceChange() {
    runInEdtAndWait {
      createModalDialogAndInteractWithIt(
        {
          // Add a new resource, outside the dialog modal
          projectRule.fixture.copyFileToProject("designAssets/add_dark.png", "res/drawable/add.png")
          pickerDialog.show()
        }
      ) { dialogWrapper ->
        val explorerView = UIUtil.findComponentOfType(dialogWrapper.rootPane, ResourceExplorerView::class.java)!!
        waitAndAssert<AssetListView>(explorerView) {
          UIUtil.dispatchAllInvocationEvents()
          it?.model?.size == 2 // The panel starts with 2 drawables
        }

        // Attempt to select
        explorerView.selectAsset("add", true)
        assertNull(pickerDialog.resourceName)

        // Need to wait for resource repository
        projectRule.waitForResourceRepositoryUpdates()
        UIUtil.dispatchAllInvocationEvents() // Pending listeners would normally propagate to refresh the panel

        // Explorer will not self-update
        assertFalse {
          // Wait just a short time, we are testing for failure
          object : WaitFor(100) {
            override fun condition(): Boolean {
              UIUtil.dispatchAllInvocationEvents()
              return UIUtil.findComponentOfType(explorerView, AssetListView::class.java)?.model?.size == 3
            }
          }.isConditionRealized
        }
        assertNull(pickerDialog.resourceName) // Still nothing, need to manually update

        val explorer = UIUtil.findComponentOfType(dialogWrapper.rootPane, ResourceExplorer::class.java)!!
        explorer.refreshIfOutdated()

        waitAndAssert<AssetListView>(explorerView) {
          UIUtil.dispatchAllInvocationEvents()
          it?.model?.size == 3 // Wait for the resource to appear in panel
        }

        // Should now be auto selected
        assertEquals("@drawable/add", pickerDialog.resourceName)
      }
    }
  }

  private fun createResourcePickerDialog(
    showSampleData: Boolean,
    initialResourceUrl: String? = null,
    supportedTypes: Set<ResourceType> = setOf(ResourceType.DRAWABLE),
    preferredType: ResourceType = ResourceType.DRAWABLE
  ): ResourcePickerDialog {
    var explorerDialog: ResourcePickerDialog? = null
    runInEdtAndWait {
      explorerDialog = ResourcePickerDialog(
        facet = AndroidFacet.getInstance(projectRule.module)!!,
        initialResourceUrl = initialResourceUrl,
        supportedTypes = supportedTypes,
        preferredType = preferredType,
        showSampleData = showSampleData,
        showThemeAttributes = true,
        currentFile = null
      )
    }
    assertThat(explorerDialog).isNotNull()
    explorerDialog?.let { view ->
      val explorerView = UIUtil.findComponentOfType(view.resourceExplorerPanel, ResourceExplorerView::class.java)!!
      waitAndAssert<AssetListView>(explorerView) { it != null && it.model.size > 0 }
    }
    return explorerDialog!!
  }
}