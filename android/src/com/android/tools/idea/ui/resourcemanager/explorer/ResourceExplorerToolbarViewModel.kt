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
import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.ResourceManagerTracking
import com.android.tools.idea.ui.resourcemanager.actions.NewResourceValueAction
import com.android.tools.idea.ui.resourcemanager.importer.ImportersProvider
import com.android.tools.idea.ui.resourcemanager.importer.ResourceImportDialog
import com.android.tools.idea.ui.resourcemanager.importer.ResourceImportDialogViewModel
import com.android.tools.idea.ui.resourcemanager.model.FilterOptions
import com.android.tools.idea.ui.resourcemanager.plugin.ResourceImporter
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.util.toVirtualFile
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
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.android.actions.CreateResourceFileAction
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import org.jetbrains.android.util.AndroidResourceUtil
import kotlin.properties.Delegates

/**
 * View model for the [com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerToolbar].
 * @param facetUpdaterCallback callback to call when a new facet is selected.
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

  var facetUpdaterCallback: (AndroidFacet) -> Unit = {}

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
      addAll(actionManager.getAction("Android.CreateResourcesActionGroup") as DefaultActionGroup)
      when (resourceType) {
        ResourceType.MIPMAP,
        ResourceType.DRAWABLE -> {
          add(actionManager.getAction("NewAndroidImageAsset"))
          add(actionManager.getAction("NewAndroidVectorAsset"))
          add(Separator())
          add(ImportResourceAction())
        }
        ResourceType.BOOL,
        ResourceType.COLOR,
        ResourceType.DIMEN,
        ResourceType.INTEGER,
        ResourceType.STRING -> add(NewResourceValueAction(resourceType, facet))
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
        supportedFileTypes.any { Comparing.equal(file.extension, it, SystemInfo.isFileSystemCaseSensitive) }
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

  var searchString: String by Delegates.observable("") { _, old, new ->
    if (new != old) {
      filterOptions.searchString = new
    }
  }

  /**
   * Implementation of [IdeView.getDirectories] that returns the resource directories of
   * the selected facet.
   * This is needed to run [CreateResourceFileAction]
   */
  override fun getDirectories() = ResourceFolderManager.getInstance(facet).folders
    .mapNotNull { runReadAction { PsiManager.getInstance(facet.module.project).findDirectory(it) } }
    .toTypedArray()

  override fun getOrChooseDirectory() = DirectoryChooserUtil.getOrChooseDirectory(this)

  /**
   * Implementation of [DataProvider] needed for [CreateResourceFileAction]
   */
  override fun getData(dataId: String): Any? = when (dataId) {
    CommonDataKeys.PROJECT.name -> facet.module.project
    LangDataKeys.MODULE.name -> facet.module
    LangDataKeys.IDE_VIEW.name -> this
    CommonDataKeys.PSI_ELEMENT.name -> getVirtualFileForResourceType()?.let {
      PsiManager.getInstance(facet.module.project).findDirectory(it)
    }
    else -> null
  }

  /**
   * Returns one of the existing directories used for the current [ResourceType]. Returns null if there's no directory. This is used to
   * enable [CreateResourceFileAction] for the current [ResourceType] with a preselected destination.
   */
  private fun getVirtualFileForResourceType(): VirtualFile? {
    val resDirs = facet.mainSourceProvider.resDirectories.mapNotNull { it.toVirtualFile() }
    return FolderTypeRelationship.getRelatedFolders(resourceType).firstOrNull()?.let { resourceFolderType ->
      // TODO: Make a smart suggestion. E.g: Colors may be on a colors or values directory and the first might be preferred.
      AndroidResourceUtil.getResourceSubdirs(resourceFolderType, resDirs).firstOrNull()
    }
  }

  /**
   * Return the [AnAction]s to switch to another module.
   * This method only returns Android modules.
   */
  fun getAvailableModules(): List<String> = ModuleManager.getInstance(facet.module.project)
    .modules
    .mapNotNull { it.androidFacet }
    .map { it.module.name }
    .sorted()

  fun onModuleSelected(moduleName: String?) {
    ModuleManager.getInstance(facet.module.project)
      .modules
      .firstOrNull { it.name == moduleName }
      ?.let { it.androidFacet }
      ?.run(facetUpdaterCallback)
  }

  inner class ImportResourceAction : AnAction("Import Drawables", "Import drawable files from disk", AllIcons.Actions.Upload), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        ResourceManagerTracking.logAssetAddedViaButton()
        ResourceImportDialog(
          ResourceImportDialogViewModel(facet, emptySequence(), importersProvider = importersProvider)).show()
    }
  }
}
