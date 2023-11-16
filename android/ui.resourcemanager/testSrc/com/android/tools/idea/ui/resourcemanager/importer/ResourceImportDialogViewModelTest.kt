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

import com.android.SdkConstants
import com.android.resources.ResourceType
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.designAssets
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.util.toVirtualFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.Consumer
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test
import java.awt.Component
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunsInEdt
class ResourceImportDialogViewModelTest {

  @get:Rule
  val rule = AndroidProjectRule.withAndroidModel()

  @get:Rule
  val edtRule = EdtRule()

  @Test
  fun importMoreAssets() {
    val viewModel = ResourceImportDialogViewModel(rule.module.androidFacet!!, emptySequence())
    val fileChooser = createStubFileChooser()
    fileChooser.files = getTestFiles("entertainment/icon_category_entertainment.png")
    viewModel.importMoreAssets { designAssetSet, _ ->
      assertThat(designAssetSet.assets[0].name).isEqualTo("icon_category_entertainment")
    }
    assertThat(viewModel.assetSets).hasSize(1)
    assertThat(viewModel.assetSets.elementAt(0).assets).hasSize(1)

    fileChooser.files = getTestFiles("entertainment/icon_category_entertainment.svg")
    viewModel.importMoreAssets { designAssetSet, newDesignAssets ->
      assertThat(newDesignAssets.map { it.file.name }).containsExactly("icon_category_entertainment.xml")
      assertThat(designAssetSet.designAssets.map { it.file.name })
        .containsExactly("icon_category_entertainment.png",
                         "icon_category_entertainment.xml")
    }
    assertThat(viewModel.assetSets).hasSize(1)
    assertThat(viewModel.assetSets.elementAt(0).assets).hasSize(2)

    fileChooser.files = getTestFiles("entertainment/icon_category_entertainment.svg")
    viewModel.importMoreAssets { designAssetSet, newDesignAssets ->
      assertThat(newDesignAssets).isEmpty()
      assertThat(designAssetSet.designAssets.map { it.file.name })
        .containsExactly("icon_category_entertainment.png",
                         "icon_category_entertainment.xml")
    }
    assertThat(viewModel.assetSets).hasSize(1)
    assertThat(viewModel.assetSets.elementAt(0).assets).hasSize(2)
  }

  @Test
  fun renameAsset() {
    val mainIdeaSourceProvider = SourceProviderManager.getInstance(AndroidFacet.getInstance(rule.module)!!).mainIdeaSourceProvider
    val testFile = getTestFiles("entertainment/icon_category_entertainment.png").first()
    val designAsset = DesignAsset(testFile, emptyList(), ResourceType.DRAWABLE)
    val viewModel = ResourceImportDialogViewModel(rule.module.androidFacet!!, sequenceOf(designAsset))
    val designAssetSet = viewModel.assetSets.first()
    assertThat(designAssetSet.name).isEqualTo("icon_category_entertainment")
    viewModel.rename(designAssetSet, "newName") { newAsset ->
      assertThat(newAsset).isNotEqualTo(designAssetSet)
      assertThat(newAsset).isNotSameAs(designAssetSet)
      assertThat(newAsset.name).isEqualTo("newName")
    }
    viewModel.commit()
    viewModel.summaryScreenViewModel.doImport()
    val first = mainIdeaSourceProvider.resDirectories.first().toIoFile()
    assertThat(File(first, "drawable/newName.png").exists()).isTrue()
  }

  @Test
  fun nameValidation() {
    val invalidName = "inv@lid"
    val expected = "'@' is not a valid file-based resource name character: File-based resource names must contain only lowercase a-z, 0-9, or underscore"
    val newName = "name2"

    val testFile = getTestFiles("entertainment/icon_category_entertainment.png").first()
    val designAsset = DesignAsset(testFile, emptyList(), ResourceType.DRAWABLE)
    val designAsset2 = DesignAsset(testFile, emptyList(), ResourceType.DRAWABLE, newName)
    val viewModel = ResourceImportDialogViewModel(rule.module.androidFacet!!, sequenceOf(designAsset, designAsset2))
    var designAssetSet = viewModel.assetSets.first { it.name == "icon_category_entertainment" }

    // Check invalidName
    viewModel.rename(designAssetSet, invalidName) { designAssetSet = it }
    assertEquals(expected, viewModel.validateName(invalidName)!!.message)
    assertEquals("$invalidName: $expected", viewModel.getValidationInfo()!!.message)

    // Check valid name duplicated
    assertNull(viewModel.validateName(newName))
    viewModel.rename(designAssetSet, newName) {}
    val validationInfo = viewModel.validateName(newName)!!
    assertEquals("A resource with the same name is also being imported.", validationInfo.message)
    assertTrue(validationInfo.warning)
    assertNull(viewModel.getValidationInfo())
  }

  @Test
  fun fixNameWhenImporting() {
    var validFile: VirtualFile? = null
    var validFileWithPrefix: VirtualFile? = null
    var invalidFile: VirtualFile? = null
    runInEdtAndWait {
      val dir = FileUtil.createTempDirectory(getTestDataDirectory(), "_images")
      validFile = FileUtil.createTempFile(dir, "baCKground image-1", SdkConstants.DOT_XML).toVirtualFile(true)
      validFileWithPrefix = FileUtil.createTempFile(dir, "ic_baCKground image-1", SdkConstants.DOT_XML).toVirtualFile(true)
      invalidFile = FileUtil.createTempFile(dir, "baCKground imag@e-1", SdkConstants.DOT_XML).toVirtualFile(true)
    }
    var asset = DesignAsset(validFile!!, emptyList(), ResourceType.DRAWABLE)
    var viewModel = ResourceImportDialogViewModel(rule.module.androidFacet!!, sequenceOf(asset))
    assertThat(viewModel.assetSets.first().name).isEqualTo("background_image_1")
    assertNull(viewModel.getValidationInfo())

    asset  = DesignAsset(validFileWithPrefix!!, emptyList(), ResourceType.DRAWABLE)
    viewModel = ResourceImportDialogViewModel(rule.module.androidFacet!!, sequenceOf(asset))
    assertThat(viewModel.assetSets.first().name).isEqualTo("ic_background_image_1")
    assertNull(viewModel.getValidationInfo())

    asset  = DesignAsset(invalidFile!!, emptyList(), ResourceType.DRAWABLE)
    viewModel = ResourceImportDialogViewModel(rule.module.androidFacet!!, sequenceOf(asset))
    assertThat(viewModel.assetSets.first().name).isEqualTo("background_imag_e_1")
    assertNull(viewModel.getValidationInfo())
  }

  private fun getTestFiles(vararg path: String): List<VirtualFile> {
    val dataDirectory = getTestDataDirectory() + "/resource-test-icons"
    return path.map { VirtualFileWrapper(File("$dataDirectory/$it")).virtualFile!! }
  }

  private fun createStubFileChooser(): StubPathChooser {
    val stubPathChooser = StubPathChooser()
    rule.replaceService(FileChooserFactory::class.java, object : FileChooserFactoryImpl() {
      override fun createPathChooser(descriptor: FileChooserDescriptor, project: Project?, parent: Component?) =
        stubPathChooser
    })
    return stubPathChooser
  }

  class StubPathChooser : PathChooserDialog {
    var files = emptyList<VirtualFile>()
    override fun choose(toSelect: VirtualFile?, callback: Consumer<in List<VirtualFile>>) {
      callback.consume(files)
    }
  }
}

