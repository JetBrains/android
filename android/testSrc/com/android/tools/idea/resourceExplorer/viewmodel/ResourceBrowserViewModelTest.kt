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
import com.android.tools.idea.resourceExplorer.getTestDataDirectory
import com.android.tools.idea.resourceExplorer.importer.getAssetSets
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidResourceUtil
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ResourceBrowserViewModelTest {

  private val projectRule = AndroidProjectRule.onDisk()

  private val chain = RuleChain(projectRule, EdtRule())
  @Rule
  fun getChain() = chain

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = getTestDataDirectory()
  }

  @Test
  fun setDirectory() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val resourceBrowserViewModel = ResourceBrowserViewModel(facet)
    resourceBrowserViewModel.setDirectory(pathToVirtualFile(getAssetDir()))
    Assert.assertEquals(2, resourceBrowserViewModel.designAssetListModel.size)
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
    facet.refreshResources()
    assertFalse(repository is ModuleResourceRepository.EmptyRepository)

    val resourceBrowserViewModel = ResourceBrowserViewModel(facet)
    val virtualFile = pathToVirtualFile(getAssetDir())
    val designAssetSet = getAssetSets(virtualFile, setOf("png", "jpg"))
    resourceBrowserViewModel.importDesignAssetSet(designAssetSet[0])
    val resourceSubdirs = AndroidResourceUtil.getResourceSubdirs(ResourceFolderType.DRAWABLE, repository.resourceDirs)
    assertEquals(2, resourceSubdirs.size)
    assertEquals("drawable-mdpi", resourceSubdirs[0].name)
    assertEquals("drawable-xhdpi", resourceSubdirs[1].name)
    assertEquals("add.png", resourceSubdirs[0].children[0].name)
    assertEquals("add.png", resourceSubdirs[1].children[0].name)
  }

  private fun pathToVirtualFile(path: String) = BrowserUtil.getURL(path)!!.let(VfsUtil::findFileByURL)!!

  private fun getAssetDir() = getTestDataDirectory() + "/designAssets"
}