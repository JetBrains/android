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

import com.android.ide.common.resources.ResourceResolver
import com.android.SdkConstants
import com.android.resources.ResourceType
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.addAarDependency
import com.android.tools.idea.res.addAndroidModule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.getPNGFile
import com.android.tools.idea.ui.resourcemanager.getPNGResourceItem
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.FilterOptions
import com.google.common.truth.Truth
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiManager
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.ImageIcon

class ResourceExplorerListViewModelImplTest {
  private val projectRule = AndroidProjectRule.onDisk()

  private val chain = RuleChain(projectRule, EdtRule())
  @Rule
  fun getChain() = chain

  private lateinit var resourceResolver: ResourceResolver
  private val disposable = Disposer.newDisposable("ResourceExplorerListViewModelImplTest")

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = getTestDataDirectory()
    resourceResolver = Mockito.mock(ResourceResolver::class.java)
  }

  @After
  fun tearDown() {
    Disposer.dispose(disposable)
  }

  @Test
  fun getDrawablePreview() {
    val latch = CountDownLatch(1)
    val pngDrawable = projectRule.getPNGResourceItem()
    val viewModel = createViewModel(projectRule.module, ResourceType.DRAWABLE)
    val asset = Asset.fromResourceItem(pngDrawable)!! as DesignAsset
    val iconSize = 32 // To compensate the 10% margin around the icon
    Mockito.`when`(resourceResolver.resolveResValue(asset.resourceItem.resourceValue)).thenReturn(asset.resourceItem.resourceValue)
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
  fun getOtherModulesResources() {
    Truth.assertThat(ResourceRepositoryManager.getModuleResources(projectRule.module)!!.allResources).isEmpty()
    val module2Name = "app2"

    runInEdtAndWait {
      addAndroidModule(module2Name, projectRule.project) { resourceDir ->
        FileUtil.copy(File(getTestDataDirectory() + "/res/values/colors.xml"),
                      resourceDir.resolve("values/colors.xml"))
      }
    }

    // Use initial module in ViewModel
    val viewModel = createViewModel(projectRule.module, ResourceType.COLOR)

    val resourceSections = viewModel.getOtherModulesResourceLists().get()
    // Other modules resource lists should return resources from modules other than the current one.
    Truth.assertThat(resourceSections).hasSize(1)
    Truth.assertThat(resourceSections.first().libraryName).isEqualTo(module2Name)
    Truth.assertThat(resourceSections.first().assetSets).isNotEmpty()
  }

  @Test
  fun getLibrariesResources() {
    val libraryName = "myLibrary"
    addAarDependency(projectRule.module,
                     libraryName, "com.resources.test") { resDir ->
      FileUtil.copyDir(File(getTestDataDirectory() + "/res"), resDir)
      // Have only some of these resources to be public.
      resDir.parentFile.resolve(SdkConstants.FN_PUBLIC_TXT).writeText(
        """
          color colorPrimary
          color colorPrimaryDark
          drawable png
          """.trimIndent()
      )
    }

    var viewModel = createViewModel(projectRule.module, ResourceType.COLOR)
    Truth.assertThat(ResourceRepositoryManager.getModuleResources(projectRule.module)!!.allResources).isEmpty()
    viewModel.filterOptions.isShowLibraries = true
    val colorSection = viewModel.getCurrentModuleResourceLists().get()
    Truth.assertThat(colorSection).hasSize(2)
    Truth.assertThat(colorSection[0].assetSets).isEmpty()
    Truth.assertThat(colorSection[1].assetSets).isNotEmpty()
    Truth.assertThat(colorSection[1].assetSets).hasSize(2)
    Truth.assertThat(colorSection[1].assetSets[0].assets[0].type).isEqualTo(ResourceType.COLOR)
    Truth.assertThat(colorSection[1].libraryName).contains(libraryName)

    viewModel = createViewModel(projectRule.module, ResourceType.DRAWABLE)
    viewModel.filterOptions.isShowLibraries = true
    val drawableSection = viewModel.getCurrentModuleResourceLists().get()
    Truth.assertThat(drawableSection).hasSize(2)
    Truth.assertThat(drawableSection[0].assetSets).isEmpty()
    Truth.assertThat(drawableSection[1].assetSets).isNotEmpty()
    Truth.assertThat(drawableSection[1].assetSets).hasSize(1)
    Truth.assertThat(drawableSection[1].assetSets[0].assets[0].type).isEqualTo(ResourceType.DRAWABLE)
    Truth.assertThat(drawableSection[1].libraryName).contains(libraryName)
  }

  @Test
  fun getResourceValues() {
    projectRule.fixture.copyFileToProject("res/values/colors.xml", "res/values/colors.xml")
    val viewModel = createViewModel(projectRule.module, ResourceType.COLOR)

    val values = viewModel.getCurrentModuleResourceLists().get()[0].assetSets
    Truth.assertThat(values).isNotNull()
    Truth.assertThat(values.flatMap { it.assets }
                       .map { it.resourceItem.resourceValue?.value })
      .containsExactly("#3F51B5", "#303F9F", "#9dff00")
  }

  @Test
  fun updateOnFileNameChanged() {
    projectRule.fixture.copyDirectoryToProject("res/", "res/")
    val viewModel = createViewModel(projectRule.module, ResourceType.DRAWABLE)
    val resourceChangedLatch = CountDownLatch(1)
    val values = viewModel.getCurrentModuleResourceLists().get()[0].assetSets
    Truth.assertThat(values).isNotNull()
    Truth.assertThat(values
                       .flatMap { it.assets }
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

    val newValues = viewModel.getCurrentModuleResourceLists().get()[0].assetSets
    Truth.assertThat(newValues
                       .flatMap { it.assets }
                       .mapNotNull { it.resourceItem.resourceValue?.value }
                       .map {
                         FileUtil.getRelativePath(projectRule.fixture.tempDirPath, it, '/')
                       })
      .containsExactly("res/drawable/png.png", "res/drawable/new_name.xml")
  }

  private fun createViewModel(module: Module, resourceType: ResourceType): ResourceExplorerListViewModelImpl {
    val facet = AndroidFacet.getInstance(module)!!
    val viewModel = ResourceExplorerListViewModelImpl(
      facet,
      null,
      resourceResolver,
      FilterOptions.createDefault(),
      resourceType
    )
    Disposer.register(disposable, viewModel)
    return viewModel
  }
}