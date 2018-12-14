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
package com.android.tools.idea.resourceExplorer.view

import com.android.tools.idea.resourceExplorer.getTestDataDirectory
import com.android.tools.idea.resourceExplorer.importer.ImportersProvider
import com.android.tools.idea.resourceExplorer.viewmodel.ProjectResourcesBrowserViewModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.util.WaitFor
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
    val view = ResourceExplorerView(viewModel,
                                    ResourceImportDragTarget(projectRule.module.androidFacet!!, ImportersProvider()))
    Disposer.register(disposable, view)

    val waitForAssetListToBeCreated = object : WaitFor(1000) {
      public override fun condition() = UIUtil.findComponentOfType(view, AssetListView::class.java) != null
    }
    assertThat(waitForAssetListToBeCreated.isConditionRealized).isEqualTo(true)
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
}