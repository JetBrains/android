/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.configurations

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.State
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.getDeviceState
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.res.getFolderType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.resourceManagers.LocalResourceManager

class ConfigurationForFile(
  val file: VirtualFile,
  manager: ConfigurationManager,
  editedConfig: FolderConfiguration
) : Configuration(manager, editedConfig) {
  private var psiFile: PsiFile? = null

  override fun calculateActivity(): String? {
    return ApplicationManager.getApplication().runReadAction(
        Computable {
          if (psiFile == null) {
            psiFile = PsiManager.getInstance(settings.project).findFile(file);
          }
          val psiXmlFile = psiFile as? XmlFile
          psiXmlFile?.rootTag?.getAttribute(SdkConstants.ATTR_CONTEXT, SdkConstants.TOOLS_URI)?.value
        })
  }

  override fun computeBestDevice(): Device? {
    for (device in mySettings.recentDevices) {
      val finalStateName = stateName ?: device.defaultState.name
      val selectedState: State = device.getDeviceState(finalStateName)!!
      val module = settings.module
      val currentConfig = getFolderConfig(mySettings.configModule, selectedState, locale, target) ?: continue
      if (!myEditedConfig.isMatchFor(currentConfig)) continue
      val repositoryManager = mySettings.configModule.resourceRepositoryManager ?: continue
      val folderType: ResourceFolderType? = getFolderType(file)
      if (folderType != null) {
        if (ResourceFolderType.VALUES == folderType) {
          // If it's a file in the values folder, ResourceRepository.getMatchingFiles won't work.
          // We get instead all the available folders and check that there is one compatible.
          val resourceManager = LocalResourceManager.getInstance(module)
          if (resourceManager != null) {
            for (resourceFile in resourceManager.findResourceFiles(ResourceNamespace.TODO(), ResourceFolderType.VALUES)) {
              if (file != resourceFile.virtualFile) continue
              val parent = AndroidPsiUtils.getPsiDirectorySafely(resourceFile)
              if (parent != null) {
                val folderConfiguration = FolderConfiguration.getConfigForFolder(parent.name)
                if (currentConfig.isMatchFor(folderConfiguration)) {
                  return device
                }
              }
            }
          }
        }
        else {
          val types = FolderTypeRelationship.getRelatedResourceTypes(folderType)
          if (types.isNotEmpty()) {
            val type = types[0]
            val resources: ResourceRepository = repositoryManager.appResources
            val matches = ConfigurationMatcher.getMatchingFiles(resources, file, ResourceNamespace.TODO(), type, currentConfig)
            if (matches.contains(file)) {
              return device
            }
          }
        }
      }
      else if ("Kotlin" == file.fileType.name) {
        return device
      }
      else if (file == settings.project.projectFile) {
        return device // Takes care of correct device selection for Theme Editor.
      }
    }

    return super.computeBestDevice()
  }

  override fun save() {
    val fileState = ConfigurationFileState()
    fileState.saveState(this)
    settings.stateManager.setConfigurationState(file, fileState)
  }

  override fun clone(): ConfigurationForFile {
    return ConfigurationForFile(file, settings, FolderConfiguration.copyOf(editedConfig)).also { it.copyFrom(this) }
  }

  override fun getSettings(): ConfigurationManager {
    return mySettings as ConfigurationManager
  }

  companion object {
    @JvmStatic
    fun create(manager: ConfigurationManager,
               file: VirtualFile,
               fileState: ConfigurationFileState?,
               editedConfig: FolderConfiguration): ConfigurationForFile {
      val configuration = ConfigurationForFile(file, manager, editedConfig)
      configuration.startBulkEditing()
      fileState?.loadState(configuration)
      configuration.finishBulkEditing()
      return configuration
    }

    /**
     * Creates a configuration suitable for the given file.
     *
     * @param base the base configuration to base the file configuration off of
     * @param file the file to look up a configuration for
     * @return a suitable configuration
     */
    @JvmStatic
    fun create(base: ConfigurationForFile, file: VirtualFile): ConfigurationForFile {
      // TODO: Figure out whether we need this, or if it should be replaced by a call to ConfigurationManager#createSimilar()
      val configuration = ConfigurationForFile(file, base.settings, FolderConfiguration.copyOf(base.editedConfig))
      configuration.copyFrom(base)

      configuration.editedConfig.set(FolderConfiguration.getConfigForFolder(file.parent.name))
      val matcher = ConfigurationMatcher(configuration, file)
      matcher.adaptConfigSelection(true /*needBestMatch*/)
      return configuration
    }
  }
}