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

import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.importer.ImportersProvider
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.widget.AssetView
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.importer.ResourceImportDragTarget
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.WaitFor
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.assertEquals

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
  fun searchIntegration() {
    projectRule.fixture.copyDirectoryToProject("res/", "res/")
    val viewModel = createViewModel(projectRule.module)
    val view = createResourceExplorerView(viewModel)
    assertThat(UIUtil.findComponentOfType(view, AssetListView::class.java)!!.model.size).isEqualTo(2)

    viewModel.filterOptions.searchString = "png"
    val list = UIUtil.findComponentOfType(view, AssetListView::class.java)!!
    val waitForElementToBeFiltered = object : WaitFor(1000) {
      public override fun condition() = list.model.size == 1
    }
    assertThat(waitForElementToBeFiltered.isConditionRealized).isTrue()
    assertThat(list.model.getElementAt(0).designAssets[0].file.name).isEqualTo("png.png")
  }

  private fun createViewModel(module: Module): ProjectResourcesBrowserViewModel {
    val facet = AndroidFacet.getInstance(module)!!
    val viewModel = ProjectResourcesBrowserViewModel(facet)

    Disposer.register(disposable, viewModel)
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
      override fun openFile(asset: DesignAsset) {
        openedFile = FileUtil.getRelativePath(projectRule.fixture.tempDirPath, asset.file.path, '/').orEmpty()
      }
    }

    // A parent used to swap the ResourceExplorerView and the DetailView
    val view = createResourceExplorerView(viewModel)
    val parent = JPanel()
    parent.add(view)
    val list = UIUtil.findComponentOfType(view, AssetListView::class.java)!!
    // End of the setup

    list.selectedIndex = 0
    simulatePressEnter(list)
    val detailView = UIUtil.findComponentOfType(parent, ResourceDetailView::class.java)!!
    val assetView = UIUtil.findComponentsOfType(detailView, AssetView::class.java)[0]
    simulatePressEnter(assetView)
    assertEquals("res/drawable/png.png", openedFile)
  }

  private fun createResourceExplorerView(viewModel: ResourceExplorerViewModel): ResourceExplorerView {
    val view = ResourceExplorerView(viewModel,
                                    ResourceImportDragTarget(
                                      projectRule.module.androidFacet!!,
                                      ImportersProvider()))
    Disposer.register(disposable, view)

    val waitForAssetListToBeCreated = object : WaitFor(1000) {
      public override fun condition() = UIUtil.findComponentOfType(view, AssetListView::class.java) != null
    }
    assertThat(waitForAssetListToBeCreated.isConditionRealized).isEqualTo(true)
    return view
  }

  private fun simulatePressEnter(component: JComponent) {
    val keyEvent = KeyEvent(component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED)
    component.keyListeners.forEach {
      it.keyPressed(keyEvent)
    }
  }
}