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

import com.android.resources.ResourceFolderType
import com.android.tools.idea.res.IdeResourceNameValidator
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.model.designAssets
import com.android.tools.idea.ui.resourcemanager.plugin.DesignAssetRendererManager
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.js.inline.util.toIdentitySet
import java.awt.Image
import java.util.concurrent.CompletableFuture
import javax.swing.JTextField

/**
 * Maximum number of files to import at a time.
 */
const val MAX_IMPORT_FILES = 400

/**
 * ViewModel for [ResourceImportDialogViewModel]
 */
class ResourceImportDialogViewModel(
  val facet: AndroidFacet,
  assets: Sequence<DesignAsset>,
  designAssetImporter: DesignAssetImporter = DesignAssetImporter(),
  private val importersProvider: ImportersProvider = ImportersProvider()
) {

  /**
   *  The [ResourceAssetSet]s to be imported.
   *
   *  They are stored in an IdentitySet because there might
   *  be conflicts when a [ResourceAssetSet] is being renamed with a name similar
   *  to another [ResourceAssetSet] being imported.
   */
  private val assetSetsToImport = assets
    .take(MAX_IMPORT_FILES)
    .groupIntoDesignAssetSet()
    .toIdentitySet()

  val assetSets get() = assetSetsToImport

  private val rendererManager = DesignAssetRendererManager.getInstance()

  /**
   * The [SummaryScreenViewModel] that will be updated [DesignAsset] are added and [commit] is called.
   *
   * This is supposed to be passed to [SummaryStep].
   */
  val summaryScreenViewModel = SummaryScreenViewModel(designAssetImporter, rendererManager, facet, getSourceSetsResDirs(facet))

  val fileCount: Int get() = assetSets.sumOf { it.assets.size }

  var updateCallback: () -> Unit = {}

  private val fileViewModels = mutableMapOf<DesignAsset, FileImportRowViewModel>()

  /**
   * We use a a separate validator for duplicate because if a duplicate is found, we just
   * want to show a warning - a user can override an existing resource.
   */
  private val resourceDuplicateValidator = IdeResourceNameValidator.forFilename(
    ResourceFolderType.DRAWABLE,
    null,
    StudioResourceRepositoryManager.getAppResources(facet))

  /**
   * This validator only check for the name
   */
  private val resourceNameValidator = IdeResourceNameValidator.forFilename(ResourceFolderType.DRAWABLE, null)

  fun getAssetPreview(asset: DesignAsset): CompletableFuture<out Image?> {
    return rendererManager
      .getViewer(asset.file)
      .getImage(asset.file, facet.module, JBUI.size(50))
  }

  fun getItemNumberString(assetSet: ResourceAssetSet) =
    "(${assetSet.assets.size} ${StringUtil.pluralize("item", assetSet.assets.size)})"

  /**
   * Remove the [asset] from the list of [DesignAsset]s to import.
   * @return the [ResourceAssetSet] that was containing the [asset]
   */
  fun removeAsset(asset: DesignAsset): ResourceAssetSet {
    val designAssetSet = assetSetsToImport.first { it.assets.contains(asset) }
    designAssetSet.assets -= asset
    if (designAssetSet.assets.isEmpty()) {
      assetSetsToImport.remove(designAssetSet)
    }
    updateCallback()
    return designAssetSet
  }

  /**
   * Invoke a path chooser and add new files to the list of assets to import.
   * If a file is already present, it won't be added and new [DesignAsset] will be merged
   * with [ResourceAssetSet] of the same name.
   *
   * [assetAddedCallback] will be called with a new or existing [ResourceAssetSet] and
   * a list of newly added [DesignAsset]s, which means that it's a subset of [ResourceAssetSet.assets].
   * This allows the view to merge the new [DesignAsset] within a potential existing view of a [ResourceAssetSet].
   * The callback won't be called if there is no new file.
   */
  fun importMoreAssets(assetAddedCallback: (ResourceAssetSet, List<DesignAsset>) -> Unit) {
    val assetByName = assetSetsToImport.associateBy { it.name }
    chooseDesignAssets(importersProvider) { newAssetSets ->
      newAssetSets
        .take(MAX_IMPORT_FILES)
        .groupIntoDesignAssetSet()
        .forEach {
          addAssetSet(assetByName, it, assetAddedCallback)
        }
    }
  }

  /**
   * Same as [importMoreAssets] but only if the list of asset to be imported is currently empty.
   */
  fun importMoreAssetIfEmpty(assetAddedCallback: (ResourceAssetSet, List<DesignAsset>) -> Unit) {
    if (assetSets.isEmpty()) {
      importMoreAssets(assetAddedCallback)
    }
  }

  /**
   * Add the [assetSet] to the list of asset to be imported.
   * If [existingAssets] contains a [ResourceAssetSet] with the same name as [assetSet],
   * the [assetSet] will be added the existing [ResourceAssetSet], otherwise a new one
   * will be created.
   * @param existingAssets A map from a [ResourceAssetSet]'s name to the [ResourceAssetSet].
   * This is used to avoid iterating through the whole [assetSetsToImport] set to try find
   * a [ResourceAssetSet] with the same name.
   */
  private fun addAssetSet(
    existingAssets: Map<String, ResourceAssetSet>,
    assetSet: ResourceAssetSet,
    assetAddedCallback: (ResourceAssetSet, List<DesignAsset>) -> Unit
  ) {
    val existingAssetSet = existingAssets[assetSet.name]
    if (existingAssetSet != null) {
      val existingPaths = existingAssetSet.designAssets.map { designAsset -> designAsset.file.path }.toSet()
      val onlyNewFiles = assetSet.designAssets.filter { designAsset -> designAsset.file.path !in existingPaths }
      if (onlyNewFiles.isNotEmpty()) {
        existingAssetSet.assets += onlyNewFiles
        assetAddedCallback(existingAssetSet, onlyNewFiles)
        updateCallback()
      }
    }
    else {
      assetSetsToImport.add(assetSet)
      assetAddedCallback(assetSet, assetSet.designAssets)
      updateCallback()
    }
  }


  /**
   * Creates a copy of [assetSet] with [newName] set as the [ResourceAssetSet]'s name.
   * This method does not modify the underlying [DesignAsset], which is just passed to the newly
   * created [ResourceAssetSet].
   *
   * [assetRenamedCallback] is a callback with the old [assetSet] name and the newly created [ResourceAssetSet].
   * This meant to be used by the view to update itself when it is holding a map from view to [ResourceAssetSet].
   */
  fun rename(assetSet: ResourceAssetSet,
             newName: String,
             assetRenamedCallback: (newAssetSet: ResourceAssetSet) -> Unit
  ) {
    require(assetSetsToImport.contains(assetSet)) { "The assetSet \"${assetSet.name}\" should already exist" }
    val renamedAssetSet = ResourceAssetSet(newName, assetSet.assets)
    assetSetsToImport.remove(assetSet)
    assetSetsToImport.add(renamedAssetSet)
    assetRenamedCallback(renamedAssetSet)
  }

  /**
   * Creates a [FileImportRowViewModel] for the provided [asset].
   *
   * To let the [FileImportRowViewModel] delete itself, a callback needs to
   * be provided to notify its owner that it has been deleted, and the view needs to be
   * updated.
   */
  fun createFileViewModel(asset: DesignAsset,
                          removeCallback: (DesignAsset) -> Unit
  ): FileImportRowViewModel {
    val viewModelRemoveCallback: (DesignAsset) -> Unit = {
      removeCallback(asset)
      fileViewModels.remove(asset)
    }
    val fileImportRowViewModel = FileImportRowViewModel(asset, ResourceFolderType.DRAWABLE, removeCallback = viewModelRemoveCallback)
    fileViewModels[asset] = fileImportRowViewModel
    return fileImportRowViewModel
  }

  fun validateName(newName: String, field: JTextField? = null): ValidationInfo? {
    val errorText = resourceNameValidator.getErrorText(newName)
    when {
      errorText != null -> return ValidationInfo(errorText, field)
      hasDuplicate(newName) -> return createDuplicateValidationInfo(field)
      checkIfNameUnique(newName) -> return getSameNameIsImportedValidationInfo(field)
      else -> return null
    }
  }

  private fun hasDuplicate(newName: String) = resourceDuplicateValidator.doesResourceExist(newName)

  private fun createDuplicateValidationInfo(field: JTextField?) =
    ValidationInfo("A resource with this name already exists and might be overridden if the qualifiers are the same.",
                   field).asWarning()

  private fun getSameNameIsImportedValidationInfo(field: JTextField?) =
    ValidationInfo("A resource with the same name is also being imported.", field)
      .asWarning()

  private fun checkIfNameUnique(newName: String?): Boolean {
    var nameSeen = false
    return assetSetsToImport
      .any {
        if (it.name == newName) {
          if (nameSeen) return@any true
          nameSeen = true
        }
        false
      }
  }

  fun getValidationInfo(): ValidationInfo? = assetSetsToImport.asSequence()
    .mapNotNull { asset -> validateName(asset.name)?.let { validationInfo -> asset to validationInfo } }
    .filter { (_, info) -> !info.warning }
    .map {
      val (asset, error) = it
      ValidationInfo("${asset.name}: ${error.message}")
    }
    .firstOrNull()

  /**
   * Passes the [assetSetsToImport] to the [SummaryScreenViewModel].
   * @see summaryScreenViewModel
   */
  fun commit() {
    summaryScreenViewModel.assetSetsToImport = assetSetsToImport
  }
}
