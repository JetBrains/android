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

import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.res.getResourceSubdirs
import com.android.tools.idea.ui.resourcemanager.ResourceManagerTracking
import com.android.tools.idea.ui.resourcemanager.actions.AddFontAction
import com.android.tools.idea.ui.resourcemanager.actions.NewResourceFileAction
import com.android.tools.idea.ui.resourcemanager.actions.NewResourceValueAction
import com.android.tools.idea.ui.resourcemanager.findCompatibleFacets
import com.android.tools.idea.ui.resourcemanager.getFacetForModuleName
import com.android.tools.idea.ui.resourcemanager.importer.ImportersProvider
import com.android.tools.idea.ui.resourcemanager.importer.ResourceImportDialog
import com.android.tools.idea.ui.resourcemanager.importer.ResourceImportDialogViewModel
import com.android.tools.idea.ui.resourcemanager.model.FilterOptions
import com.android.tools.idea.ui.resourcemanager.model.TypeFiltersModel
import com.android.tools.idea.ui.resourcemanager.plugin.ResourceImporter
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeView
import com.intellij.ide.util.DirectoryChooserUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import org.jetbrains.android.actions.CreateResourceFileAction
import org.jetbrains.android.actions.CreateResourceFileActionGroup
import org.jetbrains.android.facet.AndroidFacet
import kotlin.properties.Delegates

/**
 * View model for the [com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerToolbar].
 */
class ResourceExplorerToolbarViewModel(
  facet: AndroidFacet,
  initialResourceType: ResourceType,
  private val importersProvider: ImportersProvider,
  private val filterOptions: FilterOptions)
  : DataProvider, IdeView {

  /**
   * Callback added by the view to be called when data of this
   * view model changes.
   */
  var updateUICallback = {}

  /** Called when a new facet is selected. */
  var facetUpdaterCallback: (AndroidFacet) -> Unit = {}

  /** Callback for when a new resource is created from a toolbar action. */
  var resourceUpdaterCallback: ((String, ResourceType) -> Unit)? = null

  /** Callback for when a request to refresh resources previews is made. */
  var refreshResourcesPreviewsCallback: () -> Unit = {}

  var resourceType: ResourceType by Delegates.observable(initialResourceType) { _, oldValue, newValue ->
    if (newValue != oldValue) {
      updateUICallback()
    }
  }

  var facet: AndroidFacet = facet
    set(newFacet) {
      if (field != newFacet) {
        field = newFacet
        updateUICallback()
      }
    }

  /**
   * Name of the module currently selected
   */
  val currentModuleName
    get() = facet.module.name

  val addActions
    get() = DefaultActionGroup().apply {
      val actionManager = ActionManager.getInstance()

      actionManager.createNewResourceFileAction()?.let { add(it) }

      when (resourceType) {
        ResourceType.MIPMAP,
        ResourceType.DRAWABLE -> {
          actionManager.getAction("NewAndroidImageAsset")?.let { add(it) }
          ?: thisLogger().warn("No action associated with id: \"NewAndroidImageAsset\".")

          actionManager.getAction("NewAndroidVectorAsset")?.let { add(it) }
          ?: thisLogger().warn("No action associated with id: \"NewAndroidVectorAsset\".")

          add(Separator())
          add(ImportResourceAction())
        }
        ResourceType.BOOL,
        ResourceType.COLOR,
        ResourceType.DIMEN,
        ResourceType.INTEGER,
        ResourceType.STRING -> add(NewResourceValueAction(resourceType, facet, this@ResourceExplorerToolbarViewModel::onCreatedResource))
        ResourceType.FONT -> add(AddFontAction(facet, this@ResourceExplorerToolbarViewModel::onCreatedResource))
      }
    }

  /**
   * Returns the [AnAction] to open the available [com.android.tools.idea.ui.resourcemanager.plugin.ResourceImporter]s.
   */
  fun getImportersActions(): List<AnAction> {
    return customImporters.map { importer ->
      object : DumbAwareAction(importer.presentableName) {
        override fun actionPerformed(e: AnActionEvent) {
          invokeImporter(importer)
        }
      }
    }
  }

  /**
   * Open the [com.android.tools.idea.ui.resourcemanager.plugin.ResourceImporter] at the provided index in
   * the [ImportersProvider.importers] list.
   */
  private fun invokeImporter(importer: ResourceImporter) {
    val files = chooseFile(importer.getSupportedFileTypes(), importer.supportsBatchImport)
    importer.invokeCustomImporter(facet, files)
  }

  /**
   * Prompts user to choose a file.
   *
   * @return filePath or null if user cancels the operation
   */
  private fun chooseFile(supportedFileTypes: Set<String>, supportsBatchImport: Boolean): Collection<String> {
    val fileChooserDescriptor = FileChooserDescriptor(true, true, false, false, false, supportsBatchImport)
      .withFileFilter { file ->
        supportedFileTypes.any { Comparing.equal(file.extension, it, file.isCaseSensitive) }
      }
    return FileChooser.chooseFiles(fileChooserDescriptor, facet.module.project, null)
      .map(VirtualFile::getPath)
      .map(FileUtil::toSystemDependentName)
  }

  private val customImporters get() = importersProvider.importers.filter { it.hasCustomImport }

  var isShowModuleDependencies: Boolean
    get() = filterOptions.isShowModuleDependencies
    set(value) {
      filterOptions.isShowModuleDependencies = value
    }

  var isShowLibraryDependencies: Boolean
    get() = filterOptions.isShowLibraries
    set(value) {
      filterOptions.isShowLibraries = value
    }

  var isShowFrameworkResources: Boolean
    get() = filterOptions.isShowFramework
    set(value) {
      filterOptions.isShowFramework = value
    }

  var isShowThemeAttributes: Boolean
    get() = filterOptions.isShowThemeAttributes
    set(value) {
      filterOptions.isShowThemeAttributes = value
    }

  var typeFiltersModel: TypeFiltersModel = filterOptions.typeFiltersModel

  var searchString: String by Delegates.observable("") { _, old, new ->
    if (new != old) {
      filterOptions.searchString = new
    }
  }

  /**
   * Implementation of [IdeView.getDirectories] that returns the main resource directories of the current facet.
   *
   * Needed for AssetStudio.
   */
  override fun getDirectories(): Array<PsiDirectory> =
    SourceProviderManager.getInstance(facet).mainIdeaSourceProvider.resDirectories.mapNotNull {
      runReadAction<PsiDirectory?> {
        PsiManager.getInstance(facet.module.project).findDirectory(it)
      }
    }.toTypedArray()

  override fun getOrChooseDirectory() = DirectoryChooserUtil.getOrChooseDirectory(this)

  /**
   * Implementation of [DataProvider] needed for [CreateResourceFileAction]
   */
  override fun getData(dataId: String): Any? = when (dataId) {
    CommonDataKeys.PROJECT.name -> facet.module.project
    PlatformCoreDataKeys.MODULE.name -> facet.module
    LangDataKeys.IDE_VIEW.name -> this
    PlatformCoreDataKeys.BGT_DATA_PROVIDER.name -> DataProvider { getDataInBackground(it) }
    else -> null
  }

  private fun getDataInBackground(dataId: String): Any? = when(dataId) {
    CommonDataKeys.PSI_ELEMENT.name -> getPsiDirForResourceType()
    else -> null
  }

  /**
   * Returns one of the existing directories used for the current [ResourceType], or the default 'res' directory.
   *
   * Needed for AssetStudio.
   */
  private fun getPsiDirForResourceType(): PsiDirectory? {
    val resDirs = SourceProviderManager.getInstance(facet).mainIdeaSourceProvider.resDirectories
    val subDir = FolderTypeRelationship.getRelatedFolders(resourceType).firstOrNull()?.let { resourceFolderType ->
      getResourceSubdirs(resourceFolderType, resDirs).firstOrNull()
    }
    return (subDir ?: resDirs.firstOrNull())?.let { PsiManager.getInstance(facet.module.project).findDirectory(it) }
  }

  private fun onCreatedResource(name: String, type: ResourceType) {
    resourceUpdaterCallback?.invoke(name, type)
  }

  /**
   * Return the [AnAction]s to switch to another module.
   * This method only returns Android modules.
   */
  fun getAvailableModules(): List<String> = findCompatibleFacets(facet.module.project).map { it.module.name }.sorted()

  /**
   * Calls [facetUpdaterCallback] when a new module is selected in the ComboBox.
   */
  fun onModuleSelected(moduleName: String?) {
    getFacetForModuleName(moduleName, facet.module.project)?.run(facetUpdaterCallback)
  }

  inner class ImportResourceAction : AnAction("Import Drawables", "Import drawable files from disk", AllIcons.Actions.Upload), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        ResourceManagerTracking.logAssetAddedViaButton(facet)
        ResourceImportDialog(
          ResourceImportDialogViewModel(facet, emptySequence(), importersProvider = importersProvider)).show()
    }
  }

  /** Returns a [NewResourceFileAction] for the current resource type as long as there's a [CreateResourceFileAction] that supports it. */
  private fun ActionManager.createNewResourceFileAction(): NewResourceFileAction? {
    val resourceFolderType = resourceType.getPreferredResourceFolderType() ?: return null
    val resourceFileActionGroup = (getAction("Android.CreateResourcesActionGroup") as? CreateResourceFileActionGroup) ?: return null

    return if (resourceFileActionGroup.createResourceFileAction.subactions.any { it.resourceFolderType == resourceFolderType }) {
      NewResourceFileAction(resourceType, resourceFolderType, facet)
    }
    else {
      null
    }
  }
}

/**
 * Will return the preferred [ResourceFolderType], this means, that if available, it'll try to return any other folder other than
 * [ResourceFolderType.VALUES]. E.g: It'll return [ResourceFolderType.COLOR] for [ResourceType.COLOR].
 *
 * However, for [ResourceType.ID] it will always return [ResourceFolderType.VALUES].
 */
private fun ResourceType.getPreferredResourceFolderType(): ResourceFolderType? {
  if (this == ResourceType.ID) {
    return ResourceFolderType.VALUES
  }
  var resourceTypeFolder: ResourceFolderType? = null
  FolderTypeRelationship.getRelatedFolders(this).forEach {
    if (it != ResourceFolderType.VALUES) {
      return it
    }
    resourceTypeFolder = it
  }
  return resourceTypeFolder
}
