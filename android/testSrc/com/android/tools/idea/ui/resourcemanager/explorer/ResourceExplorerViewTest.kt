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
package com.android.tools.idea.ui.resourcemanager.explorer

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.resources.ResourceType
import com.android.tools.adtui.swing.laf.HeadlessListUI
import com.android.tools.idea.res.addAndroidModule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.importer.ImportersProvider
import com.android.tools.idea.ui.resourcemanager.importer.ResourceImportDragTarget
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.simulateMouseClick
import com.android.tools.idea.ui.resourcemanager.waitAndAssert
import com.android.tools.idea.ui.resourcemanager.widget.AssetView
import com.android.tools.idea.ui.resourcemanager.widget.DetailedPreview
import com.android.tools.idea.ui.resourcemanager.widget.LinkLabelSearchView
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTabbedPane
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ResourceExplorerViewTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private val disposable = Disposer.newDisposable("ResourceExplorerViewTest")

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = getTestDataDirectory()
    projectRule.fixture.copyFileToProject(FN_ANDROID_MANIFEST_XML, FN_ANDROID_MANIFEST_XML)
  }

  @After
  fun tearDown() {
    Disposer.dispose(disposable)
  }

  @Test
  fun selectAsset() {
    projectRule.fixture.copyDirectoryToProject("res/", "res/")
    val viewModel = createViewModel(projectRule.module)
    val view = createResourceExplorerView(viewModel)
    // 'Drawable' tab should be selected by default.
    assertThat(viewModel.supportedResourceTypes[viewModel.resourceTypeIndex]).isEqualTo(ResourceType.DRAWABLE)
    waitAndAssert<AssetListView>(view) { list -> list != null && list.model.size > 0 }
    // Select a Drawable.
    selectAndAssertAsset(view, "png")
    // Change to COLOR resources.
    runInEdtAndWait { viewModel.resourceTypeIndex = viewModel.supportedResourceTypes.indexOf(ResourceType.COLOR) }
    waitAndAssert<AssetListView>(view) { listView ->
      if (listView != null && listView.model.size > 0) {
        // Best is to make sure the resources in the list are now of the desired type.
        return@waitAndAssert listView.model.getElementAt(0).assets.any { it.type == ResourceType.COLOR }
      }
      return@waitAndAssert false
    }
    // Select a Color.
    selectAndAssertAsset(view, "colorPrimary")
    // Call a selection for a resource not listed in Color.
    runInEdtAndWait { view.selectAsset("png", false) }
    val list = UIUtil.findComponentOfType(view, AssetListView::class.java)!!
    // Selection should not change.
    assertThat(list.selectedValue).isNotNull()
    assertThat(list.selectedValue.name).isEqualTo("colorPrimary")
  }

  @Test
  fun searchIntegration() {
    projectRule.fixture.copyDirectoryToProject("res/", "res/")
    val viewModel = createViewModel(projectRule.module)
    val view = createResourceExplorerView(viewModel)
    waitAndAssert<AssetListView>(view) { it?.model?.size == 2 }

    viewModel.filterOptions.searchString = "png"
    // Elements should be filtered.
    waitAndAssert<AssetListView>(view) { it?.model?.size == 1 }
    val list = UIUtil.findComponentOfType(view, AssetListView::class.java)!!
    val firstAsset = list.model.getElementAt(0).assets[0] as DesignAsset
    assertThat(firstAsset.file.name).isEqualTo("png.png")
  }

  @Test
  fun showSearchLinkLabels() {
    val module2Name = "app2"

    // Setup
    runInEdtAndWait {
      addAndroidModule(module2Name, projectRule.project, "com.example.app2") { resourceDir ->
        FileUtil.copy(File(getTestDataDirectory() + "/res/values/colors.xml"),
                      resourceDir.resolve("values/colors.xml"))
      }
    }
    val viewModel = createViewModel(projectRule.module)
    val view = createResourceExplorerView(viewModel)
    val tabbedPane = UIUtil.findComponentOfType(view, JTabbedPane::class.java)
    assertNotNull(tabbedPane, "TabbedPane should not be null")
    runInEdtAndWait {
      // Change to Color resources tab.
      tabbedPane.model.selectedIndex = tabbedPane.indexOfTab(ResourceType.COLOR.displayName)
    }

    // Resource changed triggered, wait for the (empty) list to be available again.
    waitAndAssert<AssetListView>(view) { it?.model?.size == 0 }

    // This test assumes at least one resource in "res/values/colors.xml" includes the word "color".
    runInEdtAndWait {
      // Speed search changes should happen on the event dispatch thread.
      viewModel.filterOptions.searchString = "color"
    }

    // Wait while other modules are searched and the link label is displayed.
    waitAndAssert<LinkLabelSearchView>(view) { searchView ->
      return@waitAndAssert searchView?.let {
        val moduleLabel = UIUtil.findComponentOfType(searchView.viewport.view as JComponent, LinkLabel::class.java)
        moduleLabel != null && moduleLabel.text.contains(module2Name)
      } ?: false
    }
  }

  @Test
  fun openOnEnter() {
    // Setup the test with an image for two configuration
    // so the detail view can be shown.
    val resDir = projectRule.fixture.copyDirectoryToProject("res/", "res/")
    runInEdtAndWait {
      runWriteAction {
        val hdpiDir = resDir.createChildDirectory(this, "drawable-hdpi")
        resDir.findFileByRelativePath("drawable/png.png")!!.copy(this, hdpiDir, "png.png")
      }
    }

    var openedFile = "" // Variable used to check the opened file name

    // Dummy implementation of a ViewModel to record the opened file
    val viewModel = ResourceExplorerViewModel.createResPickerViewModel(
      projectRule.module.androidFacet!!,
      null,
      ResourceType.DRAWABLE,
      arrayOf(ResourceType.DRAWABLE, ResourceType.COLOR),
      false,
      true,
      { asset ->
        assertThat(asset).isInstanceOf(DesignAsset::class.java)
        openedFile = FileUtil.getRelativePath(
          FileUtil.toSystemIndependentName(projectRule.fixture.tempDirPath),
          FileUtil.toSystemIndependentName((asset as DesignAsset).file.path), '/').orEmpty()
      },
      {})
    Disposer.register(disposable, viewModel)

    // A parent used to swap the ResourceExplorerView and the DetailView
    val view = createResourceExplorerView(viewModel)
    val parent = JPanel()
    parent.add(view)
    // The ResourceAssetSet should have more than one asset, to guarantee that it'll open the ResourceDetailView.
    waitAndAssert<AssetListView>(view) { it != null && it.model.size > 0 && it.model.getElementAt(0).assets.size > 1 }
    val list = UIUtil.findComponentOfType(view, AssetListView::class.java)!!
    // End of the setup

    list.selectedIndex = 0
    simulatePressEnter(list)
    waitAndAssert<ResourceDetailView>(view) { it != null }
    val detailView = UIUtil.findComponentOfType(parent, ResourceDetailView::class.java)!!
    val assetView = UIUtil.findComponentsOfType(detailView, AssetView::class.java)[0]
    simulatePressEnter(assetView)
    assertEquals("res/drawable/png.png", openedFile)

    // Change to COLOR resources, ResourceDetailView should no longer be visible.
    runInEdtAndWait { viewModel.resourceTypeIndex = viewModel.supportedResourceTypes.indexOf(ResourceType.COLOR) }
    waitAndAssert<ResourceDetailView>(view) { it == null }
  }

  @Test
  fun summaryViewTest() {
    projectRule.fixture.copyDirectoryToProject("res/", "res/")
    val viewModel = createViewModel(projectRule.module)
    val view = createResourceExplorerView(viewModel, withSummaryView = true)

    waitAndAssert<DetailedPreview>(view) { it != null }

    waitAndAssert<AssetListView>(view) { it != null && it.model.size > 0 && it.model.getElementAt(0).name == "png" }
    val list = UIUtil.findComponentOfType(view, AssetListView::class.java)!!
    list.setUI(HeadlessListUI())
    val pointOfFirstResource = list.indexToLocation(0)
    // Click a resource.
    simulateMouseClick(list, pointOfFirstResource, clickCount= 1)

    waitAndAssert<DetailedPreview>(view) { summaryView ->
      if (summaryView == null) return@waitAndAssert false
      // Wait for and confirm some basic data on the summary panel.
      if (!summaryView.data.containsKey("Name") || summaryView.data["Name"] != "png") return@waitAndAssert false
      if (!summaryView.data.containsKey("Reference") || summaryView.data["Reference"] != "@drawable/png") return@waitAndAssert false
      return@waitAndAssert true
    }
  }

  private fun createViewModel(module: Module): ResourceExplorerViewModel {
    val facet = AndroidFacet.getInstance(module)!!
    val viewModel = ResourceExplorerViewModel.createResManagerViewModel(facet)
    Disposer.register(disposable, viewModel)
    return viewModel
  }

  private fun createResourceExplorerView(viewModel: ResourceExplorerViewModel, withSummaryView: Boolean = false): ResourceExplorerView {
    val view = ResourceExplorerView(viewModel,
                                    null,
                                    ResourceImportDragTarget(
                                      projectRule.module.androidFacet!!,
                                      ImportersProvider()),
                                    withMultiModuleSearch = true,
                                    withSummaryView = withSummaryView)
    Disposer.register(disposable, view)

    waitAndAssert<AssetListView>(view) { list -> list != null }
    return view
  }
}

private fun simulatePressEnter(component: JComponent) {
  val keyEvent = KeyEvent(component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED)
  component.keyListeners.forEach {
    it.keyPressed(keyEvent)
  }
}

private fun selectAndAssertAsset(view: ResourceExplorerView, assetName: String) {
  val list = UIUtil.findComponentOfType(view, AssetListView::class.java)!!
  runInEdtAndWait { list.clearSelection() }
  // Make sure selection is cleared since selection is carried over when possible.
  assertThat(list.selectedValue).isNull()
  runInEdtAndWait { view.selectAsset(assetName, false) }
  val selected = list.selectedValue
  assertThat(selected).isNotNull()
  // The correct asset should now be selected.
  assertThat(selected.name).isEqualTo(assetName)
}