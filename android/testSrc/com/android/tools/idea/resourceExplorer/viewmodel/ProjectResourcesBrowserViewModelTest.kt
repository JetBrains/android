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
package com.android.tools.idea.resourceExplorer.viewmodel

import com.android.resources.ResourceType
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.addAarDependency
import com.android.tools.idea.resourceExplorer.getPNGFile
import com.android.tools.idea.resourceExplorer.getPNGResourceItem
import com.android.tools.idea.resourceExplorer.getTestDataDirectory
import com.android.tools.idea.resourceExplorer.importer.ImportersProvider
import com.android.tools.idea.resourceExplorer.importer.SynchronizationManager
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.TimeUnit

class ProjectResourcesBrowserViewModelTest {
  private val projectRule = AndroidProjectRule.onDisk()

  private val chain = RuleChain(projectRule, EdtRule())
  @Rule
  fun getChain() = chain

  private val disposable = Disposer.newDisposable("ModuleResourcesBrowserViewModelTest")

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = getTestDataDirectory()
  }

  @After
  fun tearDown() {
    Disposer.dispose(disposable)
  }

  @Test
  fun getResourceResolver() {
    val viewModel = createViewModel(projectRule.module)
    Truth.assertThat(viewModel.resourceResolver).isNotNull()
  }

  @Test
  fun getDrawablePreview() {
    val pngDrawable = projectRule.getPNGResourceItem()
    val viewModel = createViewModel(projectRule.module)
    val drawableFuture = viewModel.getDrawablePreview(Dimension(32, 32),
                                                      DesignAssetSet("name", listOf(DesignAsset(pngDrawable))))
    val image = drawableFuture.get(1, TimeUnit.SECONDS) as BufferedImage
    ImageDiffUtil.assertImageSimilar(getPNGFile(), image, 0.05)
  }

  @Test
  fun getLibrariesResources() {
    addAarDependency(projectRule.module,
                     "myLibrary", "com.resources.test") { file -> FileUtil.copyDir(File(getTestDataDirectory() + "/res"), file) }

    val viewModel = createViewModel(projectRule.module)
    Truth.assertThat(ResourceRepositoryManager.getModuleResources(projectRule.module)!!.allResourceItems).isEmpty()
    viewModel.resourceTypeIndex = viewModel.resourceTypes.indexOf(ResourceType.COLOR)
    val colorSection = viewModel.getResourcesLists()
    Truth.assertThat(colorSection).hasSize(2)
    Truth.assertThat(colorSection[0].assets).isEmpty()
    Truth.assertThat(colorSection[1].assets).isNotEmpty()
    Truth.assertThat(colorSection[1].assets).isNotEmpty()
    Truth.assertThat(colorSection[1].assets[0].designAssets[0].type).isEqualTo(ResourceType.COLOR)

    viewModel.resourceTypeIndex = viewModel.resourceTypes.indexOf(ResourceType.DRAWABLE)
    val drawableSection = viewModel.getResourcesLists()
    Truth.assertThat(drawableSection).hasSize(2)
    Truth.assertThat(drawableSection[0].assets).isEmpty()
    Truth.assertThat(drawableSection[1].assets).isNotEmpty()
    Truth.assertThat(drawableSection[1].assets[0].designAssets[0].type).isEqualTo(ResourceType.DRAWABLE)
  }

  @RunsInEdt
  @Test
  fun getResourceValues() {
    projectRule.fixture.copyFileToProject("res/values/colors.xml", "res/values/colors.xml")
    val viewModel = createViewModel(projectRule.module)

    viewModel.resourceTypeIndex = viewModel.resourceTypes.indexOf(ResourceType.COLOR)
    val values = viewModel.getResourcesLists()[0].assets
    Truth.assertThat(values).isNotNull()
    Truth.assertThat(values.flatMap { it.designAssets }
                       .map { it.resourceItem.resourceValue?.value })
      .containsExactly("#3F51B5", "#303F9F", "#9dff00")
  }

  private fun createViewModel(module: Module): ProjectResourcesBrowserViewModel {
    val facet = AndroidFacet.getInstance(module)!!
    val synchronizationManager = SynchronizationManager(facet)
    Disposer.register(disposable, synchronizationManager)
    return ProjectResourcesBrowserViewModel(facet, synchronizationManager, ImportersProvider())
  }
}