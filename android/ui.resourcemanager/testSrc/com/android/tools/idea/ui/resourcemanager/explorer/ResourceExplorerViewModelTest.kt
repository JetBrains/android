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
import com.android.tools.idea.res.addAndroidModule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerListViewModel.UpdateUiReason
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiManager
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ResourceExplorerViewModelTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withSdk()

  private val disposable = Disposer.newDisposable("ResourceExplorerViewModelTest")

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
  fun testChangeResourceType() {
    val latch = CountDownLatch(2)
    val viewModel = createViewModel(projectRule.module)
    val listViewModel = viewModel.createResourceListViewModel().get()
    viewModel.updateResourceTabCallback = { latch.countDown() }
    viewModel.resourceTypeUpdaterCallback = {
      latch.countDown()
      Truth.assertThat(it).isEqualTo(ResourceType.STRING)
    }

    // Trigger a change in the Resource Type.
    viewModel.resourceTypeIndex = viewModel.supportedResourceTypes.indexOf(ResourceType.STRING)
    Truth.assertThat(listViewModel.currentResourceType).isEqualTo(ResourceType.STRING)
    Truth.assertThat(latch.await(15L, TimeUnit.SECONDS)).isTrue()
  }

  @Test
  fun testChangeFacet() {
    runInEdtAndWait {
      addAndroidModule("app2", projectRule.project, "com.example.app2") {}
    }

    val modules = ModuleManager.getInstance(projectRule.project).modules
    Truth.assertThat(modules).hasLength(2)
    val module1 = modules[0]
    val module2 = modules[1]

    val latch = CountDownLatch(2)
    val viewModel = createViewModel(module1)
    val listViewModel = viewModel.createResourceListViewModel().get()

    viewModel.facetUpdaterCallback = { facet ->
      latch.countDown()
      Truth.assertThat(facet).isEqualTo(module2.androidFacet)
    }
    viewModel.populateResourcesCallback = { latch.countDown() }

    viewModel.facet = module2.androidFacet!!
    Truth.assertThat(listViewModel.facet).isNotEqualTo(module2.androidFacet)
    Truth.assertThat(viewModel.createResourceListViewModel().get().facet).isEqualTo(module2.androidFacet)
    Truth.assertThat(latch.await(15L, TimeUnit.SECONDS)).isTrue()
  }

  @Test
  fun testResManagerViewModelSavedState() {
    val viewModel = createViewModel(projectRule.module)
    val oldResourceTypeIndex = viewModel.resourceTypeIndex
    val newResourceTypeIndex = viewModel.resourceTypeIndex + 1
    Truth.assertThat(oldResourceTypeIndex).isEqualTo(0)
    Truth.assertThat(newResourceTypeIndex).isNotEqualTo(oldResourceTypeIndex)
    viewModel.resourceTypeIndex = newResourceTypeIndex
    Truth.assertThat(viewModel.resourceTypeIndex).isEqualTo(newResourceTypeIndex)

    val oldThemeAttributesValue = viewModel.filterOptions.isShowThemeAttributes
    val newThemeAttributesValue = !oldThemeAttributesValue
    viewModel.filterOptions.isShowThemeAttributes = newThemeAttributesValue
    Truth.assertThat(viewModel.filterOptions.isShowThemeAttributes).isEqualTo(newThemeAttributesValue)
    Truth.assertThat(newThemeAttributesValue).isNotEqualTo(oldThemeAttributesValue)

    val newViewModel = createViewModel(projectRule.module)
    Truth.assertThat(newViewModel.resourceTypeIndex).isEqualTo(newResourceTypeIndex)
    Truth.assertThat(newViewModel.filterOptions.isShowThemeAttributes).isEqualTo(newThemeAttributesValue)
  }

  @Test
  fun updateOnFileNameChanged() {
    projectRule.fixture.copyDirectoryToProject("res/", "res/")
    val viewModel = createViewModel(projectRule.module)
    val listViewModel = viewModel.createResourceListViewModel().get(10L, TimeUnit.SECONDS)
    val resourceChangedLatch = CountDownLatch(1)
    val values = listViewModel.getCurrentModuleResourceLists().get()[0].assetSets
    Truth.assertThat(values).isNotNull()
    Truth.assertThat(values
                       .flatMap { it.assets }
                       .mapNotNull { it.resourceItem.resourceValue?.value }
                       .map {
                         // ResourceValue.getValue() implementations can return strings with different separators on Windows
                         FileUtil.getRelativePath(FileUtil.toSystemIndependentName(projectRule.fixture.tempDirPath),
                                                  FileUtil.toSystemIndependentName(it), '/')
                       })
      .containsExactly("res/drawable/png.png", "res/drawable/vector_drawable.xml")

    listViewModel.updateUiCallback = {
      if (it == UpdateUiReason.RESOURCES_CHANGED) {
        resourceChangedLatch.countDown()
      }
    }

    val file = projectRule.fixture.findFileInTempDir("res/drawable/vector_drawable.xml")!!
    val psiFile = runReadAction { PsiManager.getInstance(projectRule.project).findFile(file)!! }
    runInEdtAndGet { RenameDialog(projectRule.project, psiFile, null, null).performRename("new_name") }
    Truth.assertWithMessage("resourceChangedCallback was called").that(resourceChangedLatch.await(1, TimeUnit.SECONDS)).isTrue()

    val newValues = listViewModel.getCurrentModuleResourceLists().get()[0].assetSets
    Truth.assertThat(newValues
                       .flatMap { it.assets }
                       .mapNotNull { it.resourceItem.resourceValue?.value }
                       .map {
                         // ResourceValue.getValue() implementations can return strings with different separators on Windows
                         FileUtil.getRelativePath(FileUtil.toSystemIndependentName(projectRule.fixture.tempDirPath),
                                                  FileUtil.toSystemIndependentName(it), '/')
                       })
      .containsExactly("res/drawable/png.png", "res/drawable/new_name.xml")
  }

  private fun createViewModel(module: Module): ResourceExplorerViewModel {
    val facet = AndroidFacet.getInstance(module)!!
    val viewModel = ResourceExplorerViewModel.createResManagerViewModel(facet)
    Disposer.register(disposable, viewModel)
    return viewModel
  }
}