/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.resources.ResourceFolderType
import com.android.tools.idea.res.ModuleResourceRepository
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.resourceExplorer.densityMapper
import com.android.tools.idea.resourceExplorer.getTestDataDirectory
import com.android.tools.idea.resourceExplorer.importer.ImportersProvider
import com.android.tools.idea.resourceExplorer.importer.QualifierMatcher
import com.android.tools.idea.resourceExplorer.model.getAssetSets
import com.android.tools.idea.resourceExplorer.nightModeMapper
import com.android.tools.idea.resourceExplorer.importer.SynchronizationManager
import com.android.tools.idea.resourceExplorer.importer.SynchronizationStatus
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.collect.testing.Helpers
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidResourceUtil
import org.junit.*

class ExternalBrowserViewModelTest {

  private val projectRule = AndroidProjectRule.onDisk()

  private val chain = RuleChain(projectRule, EdtRule())
  @Rule
  fun getChain() = chain

  private val disposable = Disposer.newDisposable("ExternalBrowserViewModelTest")

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = getTestDataDirectory()
  }

  @After
  fun tearDown() {
    Disposer.dispose(disposable)
  }

  @Test
  fun setDirectory() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val resourceBrowserViewModel = createViewModel(facet)
    resourceBrowserViewModel.consumeMatcher(QualifierMatcher(densityMapper, nightModeMapper))
    resourceBrowserViewModel.setDirectory(pathToVirtualFile(getAssetDir()))
    Assert.assertEquals(1, resourceBrowserViewModel.designAssetListModel.size)
  }

  @RunsInEdt
  @Test
  fun importDesignAssetSet() {
    val module = projectRule.module
    val facet = AndroidFacet.getInstance(module)!!
    // Init the res folder with a dummy file
    val res = projectRule.fixture.copyFileToProject(getTestDataDirectory() + "/res/values.xml", "res/values/values.xml").parent.parent
    ModuleResourceRepository.createForTest(facet, listOf(res))
    val repository = ModuleResourceRepository.getOrCreateInstance(facet)
    ResourceRepositoryManager.getOrCreateInstance(facet).resetAllCaches()
    assertFalse(repository is ModuleResourceRepository.EmptyRepository)

    val resourceBrowserViewModel = createViewModel(facet)
    val virtualFile = pathToVirtualFile(getAssetDir())
    val designAssetSet = getAssetSets(
      virtualFile,
      setOf("png", "jpg"),
      QualifierMatcher(densityMapper, nightModeMapper)
    )
    resourceBrowserViewModel.importDesignAssetSet(designAssetSet[0])
    val resourceSubdirs = AndroidResourceUtil.getResourceSubdirs(ResourceFolderType.DRAWABLE, repository.resourceDirs)
    Helpers.assertContentsAnyOrder(resourceSubdirs.map { it.name }, "drawable-night-mdpi", "drawable-xhdpi", "drawable-night-xhdpi")
    assertEquals("add.png", resourceSubdirs[0].children[0].name)
    assertEquals("add.png", resourceSubdirs[1].children[0].name)
    assertEquals("add.png", resourceSubdirs[2].children[0].name)
  }


  @RunsInEdt
  @Test
  fun testSingleSynchronization() {
    val module = projectRule.module
    val facet = AndroidFacet.getInstance(module)!!
    // Init the res folder with a dummy file
    val res = projectRule.fixture.copyFileToProject(getTestDataDirectory() + "/res/values.xml", "res/values/values.xml").parent.parent
    projectRule.fixture.copyFileToProject(getAssetDir() + "/add@2x.png", "res/drawable-xhdpi/add.png").parent.parent
    projectRule.fixture.copyFileToProject(getAssetDir() + "/add@2x_dark.png", "res/drawable-night-xhdpi/add.png").parent.parent
    projectRule.fixture.copyFileToProject(getAssetDir() + "/add_dark.png", "res/drawable-night-mdpi/add.png").parent.parent
    ModuleResourceRepository.createForTest(facet, listOf(res))
    ResourceRepositoryManager.getOrCreateInstance(facet).resetAllCaches()

    val resourceBrowserViewModel = createViewModel(facet)
    val virtualFile = pathToVirtualFile(getAssetDir())
    val designAssetSet = getAssetSets(
      virtualFile,
      setOf("png", "jpg"),
      QualifierMatcher(densityMapper, nightModeMapper)
    )[0]
    assertEquals(SynchronizationStatus.SYNCED, resourceBrowserViewModel.getSynchronizationStatus(designAssetSet))

    val otherFile = pathToVirtualFile(getAlternateAssetDir())
    val otherAssertSet = getAssetSets(
      otherFile,
      setOf("png", "jpg"),
      QualifierMatcher(densityMapper, nightModeMapper)
    )[0]
    assertEquals(SynchronizationStatus.NOT_SYNCED, resourceBrowserViewModel.getSynchronizationStatus(otherAssertSet))
  }

  private fun createViewModel(facet: AndroidFacet): ExternalBrowserViewModel {
    val synchronizationManager = SynchronizationManager(facet)
    Disposer.register(disposable, synchronizationManager)
    return ExternalBrowserViewModel(facet, ResourceFileHelper.ResourceFileHelperImpl(), ImportersProvider(), synchronizationManager)
  }

  private fun pathToVirtualFile(path: String) = BrowserUtil.getURL(path)!!.let(VfsUtil::findFileByURL)!!

  private fun getAssetDir() = getTestDataDirectory() + "/designAssets"

  private fun getAlternateAssetDir() = getTestDataDirectory() + "/success-icons"
}
