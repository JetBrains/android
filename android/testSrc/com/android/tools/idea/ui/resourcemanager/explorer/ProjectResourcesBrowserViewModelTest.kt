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
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.addAarDependency
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.getPNGFile
import com.android.tools.idea.ui.resourcemanager.getPNGResourceItem
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.google.common.truth.Truth
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiManager
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndGet
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.ImageIcon

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
  fun getDrawablePreview() {
    val latch = CountDownLatch(1)
    val pngDrawable = projectRule.getPNGResourceItem()
    val viewModel = createViewModel(projectRule.module)
    val asset = DesignAsset.fromResourceItem(pngDrawable)!!
    val iconSize = 32 // To compensate the 10% margin around the icon
    viewModel.assetPreviewManager
      .getPreviewProvider(ResourceType.DRAWABLE)
      .getIcon(asset, iconSize, iconSize, { latch.countDown() })
    Truth.assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue()

    val icon = viewModel.assetPreviewManager
      .getPreviewProvider(ResourceType.DRAWABLE)
      .getIcon(asset, iconSize, iconSize, { println("CALLBACK") }) as ImageIcon
    val image = icon.image as BufferedImage
    ImageDiffUtil.assertImageSimilar(getPNGFile(), image, 0.05)
  }

  @Test
  fun getLibrariesResources() {
    addAarDependency(projectRule.module,
                     "myLibrary", "com.resources.test") { file -> FileUtil.copyDir(File(
      getTestDataDirectory() + "/res"), file) }

    val viewModel = createViewModel(projectRule.module)
    Truth.assertThat(ResourceRepositoryManager.getModuleResources(projectRule.module)!!.allResources).isEmpty()
    viewModel.resourceTypeIndex = viewModel.resourceTypes.indexOf(ResourceType.COLOR)
    viewModel.filterOptions.isShowLibraries = true
    val colorSection = viewModel.getResourcesLists().get()
    Truth.assertThat(colorSection).hasSize(2)
    Truth.assertThat(colorSection[0].assets).isEmpty()
    Truth.assertThat(colorSection[1].assets).isNotEmpty()
    Truth.assertThat(colorSection[1].assets).isNotEmpty()
    Truth.assertThat(colorSection[1].assets[0].designAssets[0].type).isEqualTo(ResourceType.COLOR)

    viewModel.resourceTypeIndex = viewModel.resourceTypes.indexOf(ResourceType.DRAWABLE)
    val drawableSection = viewModel.getResourcesLists().get()
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
    val values = viewModel.getResourcesLists().get()[0].assets
    Truth.assertThat(values).isNotNull()
    Truth.assertThat(values.flatMap { it.designAssets }
                       .map { it.resourceItem.resourceValue?.value })
      .containsExactly("#3F51B5", "#303F9F", "#9dff00")
  }

  @Test
  fun updateOnFileNameChanged() {
    projectRule.fixture.copyDirectoryToProject("res/", "res/")
    val viewModel = createViewModel(projectRule.module)
    val resourceChangedLatch = CountDownLatch(1)
    viewModel.resourceTypeIndex = viewModel.resourceTypes.indexOf(ResourceType.DRAWABLE)
    val values = viewModel.getResourcesLists().get()[0].assets
    Truth.assertThat(values).isNotNull()
    Truth.assertThat(values
                       .flatMap { it.designAssets }
                       .mapNotNull { it.resourceItem.resourceValue?.value }
                       .map {
                         FileUtil.getRelativePath(projectRule.fixture.tempDirPath, it, '/')
                       })
      .containsExactly("res/drawable/png.png", "res/drawable/vector_drawable.xml")

    viewModel.resourceChangedCallback = {
      resourceChangedLatch.countDown()
    }

    val file = projectRule.fixture.findFileInTempDir("res/drawable/vector_drawable.xml")!!
    val psiFile = runReadAction { PsiManager.getInstance(projectRule.project).findFile(file)!! }
    runInEdtAndGet { RenameDialog(projectRule.project, psiFile, null, null).performRename("new_name.xml") }
    Truth.assertWithMessage("resourceChangedCallback was called").that(resourceChangedLatch.await(1, TimeUnit.SECONDS)).isTrue()

    val newValues = viewModel.getResourcesLists().get()[0].assets
    Truth.assertThat(newValues
                       .flatMap { it.designAssets }
                       .mapNotNull { it.resourceItem.resourceValue?.value }
                       .map {
                         FileUtil.getRelativePath(projectRule.fixture.tempDirPath, it, '/')
                       })
      .containsExactly("res/drawable/png.png", "res/drawable/new_name.xml")
  }

  private fun createViewModel(module: Module): ProjectResourcesBrowserViewModel {
    val facet = AndroidFacet.getInstance(module)!!
    val viewModel = ProjectResourcesBrowserViewModel(facet)
    Disposer.register(disposable, viewModel)
    return viewModel
  }
}