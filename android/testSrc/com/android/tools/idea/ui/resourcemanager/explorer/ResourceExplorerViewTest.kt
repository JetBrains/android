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

import com.android.resources.ResourceType
import com.android.tools.adtui.swing.laf.HeadlessListUI
import com.android.tools.idea.res.addAndroidModule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.importer.ImportersProvider
import com.android.tools.idea.ui.resourcemanager.importer.ResourceImportDragTarget
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.widget.AssetView
import com.android.tools.idea.ui.resourcemanager.widget.DetailedPreview
import com.android.tools.idea.ui.resourcemanager.widget.LinkLabelSearchView
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.WaitFor
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTabbedPane
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val WAIT_TIMEOUT = 3000

class ResourceExplorerViewTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private val disposable = Disposer.newDisposable("ResourceExplorerViewTest")

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = getTestDataDirectory()
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

    // 'Drawable' tab is selected by default.
    selectAndAssertAsset(view, "png")
    // Change to COLOR resources.
    runInEdtAndWait { viewModel.resourceTypeIndex = viewModel.resourceTypes.indexOf(ResourceType.COLOR) }
    waitAndAssert<AssetListView>(view) { listView ->
      if (listView != null && listView.model.size > 0) {
        // Best is to make sure the resources in the list are now of the desired type.
        return@waitAndAssert listView.model.getElementAt(0).assets.any { it.type == ResourceType.COLOR }
      }
      return@waitAndAssert false
    }
    selectAndAssertAsset(view, "colorPrimary")
    runInEdtAndWait { view.selectAsset("png", false) }
    val list = UIUtil.findComponentOfType(view, AssetListView::class.java)!!
    // Selection should not change if we try to select a resource not visible here.
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
      addAndroidModule(module2Name, projectRule.project) { resourceDir ->
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

  private fun createViewModel(module: Module): ResourceExplorerViewModel {
    val facet = AndroidFacet.getInstance(module)!!
    val viewModel = ResourceExplorerViewModel.createResManagerViewModel(facet)

    assert(viewModel is Disposable)
    Disposer.register(disposable, viewModel as Disposable)
    return viewModel
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
    val viewModel = object : ResourceExplorerViewModel by createViewModel(projectRule.module) {
      override val doSelectAssetAction: (asset: Asset) -> Unit =  { asset ->
        assertThat(asset).isInstanceOf(DesignAsset::class.java)
        openedFile = FileUtil.getRelativePath(projectRule.fixture.tempDirPath, (asset as DesignAsset).file.path, '/').orEmpty()
      }
    }

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
    val detailView = UIUtil.findComponentOfType(parent, ResourceDetailView::class.java)!!
    val assetView = UIUtil.findComponentsOfType(detailView, AssetView::class.java)[0]
    simulatePressEnter(assetView)
    assertEquals("res/drawable/png.png", openedFile)
  }

  @Test
  fun summaryViewTest() {
    projectRule.fixture.copyDirectoryToProject("res/", "res/")
    val viewModel = createViewModel(projectRule.module)
    val view = createResourceExplorerView(viewModel, withSummaryView = true)

    val summaryView = UIUtil.findComponentOfType(view, DetailedPreview::class.java)
    assertNotNull(summaryView, "Summary view should be present")

    waitAndAssert<AssetListView>(view) { it != null && it.model.size > 0 && it.model.getElementAt(0).name == "png" }
    val list = UIUtil.findComponentOfType(view, AssetListView::class.java)!!
    list.ui = HeadlessListUI()
    val pointOfFirstResource = list.indexToLocation(0)
    // Click a resource.
    simulateMouseClick(list, pointOfFirstResource, clickCount= 1)

    // Confirm some basic data on the summary panel.
    assertThat(summaryView.data).containsEntry("Name", "png")
    assertThat(summaryView.data).containsEntry("Reference", "@drawable/png")
  }

  private fun createResourceExplorerView(viewModel: ResourceExplorerViewModel, withSummaryView: Boolean = false): ResourceExplorerView {
    val view = ResourceExplorerView(viewModel,
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

private fun simulateMouseClick(component: JComponent, point: Point, clickCount: Int) {
  runInEdtAndWait {
    // A click is done through a mouse pressed & released event, followed by the actual mouse clicked event.
    component.dispatchEvent(MouseEvent(
      component, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, point.x, point.y, 0, false))
    component.dispatchEvent(MouseEvent(
      component, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, point.x, point.y, 0, false))
    component.dispatchEvent(MouseEvent(
      component, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, point.x, point.y, clickCount, false))
  }
}

private inline fun <reified T : JComponent> waitAndAssert(view: ResourceExplorerView, crossinline condition: (list: T?) -> Boolean) {
  val waitForComponentCondition = object : WaitFor(WAIT_TIMEOUT) {
    public override fun condition() = condition(UIUtil.findComponentOfType(view, T::class.java))
  }
  assertTrue(waitForComponentCondition.isConditionRealized)
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