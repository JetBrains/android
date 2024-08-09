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
package com.android.tools.idea.ui.resourcemanager.importer

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.HighDynamicRangeQualifier
import com.android.ide.common.resources.configuration.NightModeQualifier
import com.android.resources.Density
import com.android.resources.HighDynamicRange
import com.android.resources.NightMode
import com.android.resources.ResourceType
import com.android.tools.idea.project.DefaultToken
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.plugin.DesignAssetRendererManager
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ExtensionTestUtil.maskExtensions
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@RunsInEdt
class SummaryScreenViewModelTest {

  @get:Rule
  val rule = AndroidProjectRule.onDisk()

  @get:Rule
  val edtRule = EdtRule()

  private val facet: AndroidFacet
    get() = rule.module.androidFacet!!
  private val viewModel: SummaryScreenViewModel by lazy {
    SummaryScreenViewModel(DesignAssetImporter(), DesignAssetRendererManager.getInstance(), facet, getSourceSetsResDirs(facet))
  }

  @Before
  fun setUp() {
    val token = object : CreateDefaultResDirectoryToken<AndroidProjectSystem>, DefaultToken {
      override fun createDefaultResDirectory(projectSystem: AndroidProjectSystem, facet: AndroidFacet): File? =
        File(VfsUtil.virtualToIoFile(facet.module.rootManager.contentRoots.first()), "res").also { it.mkdirs() }
    }
    maskExtensions(CreateDefaultResDirectoryToken.EP_NAME, listOf(token), rule.testRootDisposable)
  }

  @Test
  fun initialState() {
    assertThat(getOrCreateDefaultResDirectory(facet).isDirectory).isTrue()
    assertThat(viewModel.assetSetsToImport).isEmpty()
    val fileTreeModel = viewModel.fileTreeModel
    assertThat(fileTreeModel.getChildCount(fileTreeModel.root)).isEqualTo(0)
  }

  @Test
  fun treeCorrectlyPopulated() {
    val projectResDir = getOrCreateDefaultResDirectory(facet)
    val resDir = rule.fixture.tempDirFixture.findOrCreateDir("res")

    val trueFile = getTestFiles("entertainment/icon_category_entertainment.png").first()
    viewModel.assetSetsToImport =
      setOf(
        ResourceAssetSet("asset1", listOf(
          DesignAsset(FakeVirtualFile(resDir, "image1.png"), listOf(NightModeQualifier(NightMode.NIGHT)), ResourceType.DRAWABLE),
          DesignAsset(FakeVirtualFile(resDir, "image2.png"), listOf(DensityQualifier(Density.MEDIUM)), ResourceType.DRAWABLE)
        )),
        ResourceAssetSet("asset2", listOf(
          DesignAsset(FakeVirtualFile(resDir, "image3.png"), listOf(
            HighDynamicRangeQualifier(HighDynamicRange.HIGHDR),
            DensityQualifier(Density.MEDIUM)),
                      ResourceType.DRAWABLE),
          DesignAsset(trueFile, listOf(), ResourceType.DRAWABLE)
        )))

    assertThat(viewModel.assetSetsToImport).hasSize(2)
    val fileTreeModel = viewModel.fileTreeModel

    val firstLevelDirs = (0..3)
      .map { fileTreeModel.root.getChild(it) }
      .map { FileUtil.toSystemIndependentName(it.file.relativeTo (projectResDir.parentFile).path) }

    val files = (0..3)
      .map { fileTreeModel.root.getChild(it).getChild(0) }
      .map { it.file.name }

    assertThat(firstLevelDirs).containsExactly("res/drawable", "res/drawable-highdr-mdpi", "res/drawable-mdpi",
                                               "res/drawable-night").inOrder()
    assertThat(files).containsExactly("asset2.png", "asset2.png", "asset1.png", "asset1.png").inOrder()

    viewModel.selectedFile = fileTreeModel.root.getChild(0).getChild(0).file
    assertThat(viewModel.metadata).containsExactly("File name", "asset2.png",
                                                   "File type", "PNG",
                                                   "File size", "4.75 kB",
                                                   "Dimensions (px)", "181x119")

    assertThat(viewModel.getPreview().join()).isNotNull()
  }

  @Test
  fun callbackCalled() {
    var callBackCalled = false
    val updateCallback = { callBackCalled = true }
    viewModel.updateCallback = updateCallback
    val resDir = rule.fixture.tempDirFixture.findOrCreateDir("res")

    viewModel.assetSetsToImport =
      setOf(ResourceAssetSet("asset1", listOf(
        DesignAsset(FakeVirtualFile(resDir, "image1.png"), listOf(NightModeQualifier(NightMode.NIGHT)), ResourceType.DRAWABLE))))
    viewModel.selectedFile = viewModel.fileTreeModel.root.getChild(0).getChild(0).file
    assertThat(callBackCalled).isTrue()
  }

  @Test
  fun sourceSetSelection() {
    val modulePath = ModuleUtil.getModuleDirPath(facet.module)
    val viewModel2 = SummaryScreenViewModel(DesignAssetImporter(), DesignAssetRendererManager.getInstance(), facet,
                                            arrayOf(SourceSetResDir(File(modulePath, "src/main/res"), "main"),
                                               SourceSetResDir(File(modulePath, "src/full/res1"), "full"),
                                               SourceSetResDir(File(modulePath, "src/demo/res2"), "demo")))
    assertThat(viewModel2.selectedResDir).isEqualTo(SourceSetResDir(File(modulePath, "src/main/res"), "main"))
    assertThat(viewModel2.availableResDirs).asList().containsExactly(SourceSetResDir(File(modulePath, "src/main/res"), "main"),
                                                                     SourceSetResDir(File(modulePath, "src/full/res1"), "full"),
                                                                     SourceSetResDir(File(modulePath, "src/demo/res2"), "demo"))
    viewModel2.selectedResDir = viewModel2.availableResDirs[1]
    assertThat(viewModel2.fileTreeModel.root.file).isEqualTo(File(modulePath, "src/full/res1"))
    val trueFile = getTestFiles("entertainment/icon_category_entertainment.png").first()
    viewModel2.assetSetsToImport = setOf(ResourceAssetSet("testResource", listOf(DesignAsset(trueFile, listOf(), ResourceType.DRAWABLE))))

    viewModel2.doImport()
    assertThat(File(facet.module.project.basePath, "src/full/res1/drawable/testResource.png").exists()).isTrue()
  }

  private fun getTestFiles(vararg path: String): List<VirtualFile> {
    val dataDirectory = getTestDataDirectory() + "/resource-test-icons"
    return path.map { VirtualFileWrapper(File("$dataDirectory/$it")).virtualFile!! }
  }
}