/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.importing

import com.android.SdkConstants
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.util.GradleUtil
import com.intellij.facet.FacetManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager.Companion.getInstance
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.toCanonicalPath
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleWithNameAlreadyExists
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.PathUtil
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

class TopLevelModuleFactory() {

  /**
   * Creates and configures a temporary module holding covering sources in the root of the Gradle project root to solve the
   * following problems:
   *
   * (1) We need a way to display the content of the project in the Android project view while the project is being setup or in the case
   * when it cannot be setup. The problem, here, is that there is not a node class capable to show the virtual file system outside of
   * the project scope. Nodes that exist like those in Project files view require a different tree model and thus cannot be used
   * directly.
   *
   * (2) IDEA automatically configures a top level module when opening a project without modules by [PlatformProjectOpenProvider] which
   * happens when re-opening Android Studio and would happen if we do not configure the top level module ourselves in the case of
   * failing or not-finished sync.
   *
   * (3) A temporary project structure needs to be configured in a way that excludes Gradle directories like `build` or `.gradle` (to
   * some extent) from indexing. Otherwise indexing that might happen before the first sync or will happen if sync fails or Android
   * Studio is restarted would index too many files and take too long.
   *
   * (4) The top-level module if created needs to be registered with the external system so that it is later picked up as a module for
   * the `:` Gradle project.
   *
   * (5) The support for Groovy based `build.gradle` requires the module holding them to have a JDK.
   */
  fun createOrConfigureTopLevelModule(project: Project) {
    GradleSettings.getInstance(project)
      .linkedProjectsSettings
      .map { File(it.externalProjectPath) }
      .forEach { createOrConfigureTopLevelModule(project, it) }
  }

  private fun createOrConfigureTopLevelModule(project: Project, gradleRoot: File) {
    val projectRoot = Projects.getBaseDirPath(project)
    val gradleRootPath = PathUtil.toSystemIndependentName(gradleRoot.path)
    val gradleRootVirtualFile = VfsUtil.findFileByIoFile(gradleRoot, true) ?: return
    val gradleRootUrl = gradleRootVirtualFile.url
    val moduleManager = ModuleManager.getInstance(project)
    val moduleFile = File(
      File(File(projectRoot, Project.DIRECTORY_STORE_FOLDER), "modules"),  // "modules" is private in GradleManager.
      gradleRoot.name + ".iml"
    )
    val projectModifieableModel = moduleManager.getModifiableModel()
    // Find or create the top level module. Normally, when invoked from `AndroidGradleProjectConfigurator` it already exists as it is
    // created by `PlatformProjectConfigurator`, which runs first.
    val module = projectModifieableModel
      .modules
      .singleOrNull { ModuleRootManager.getInstance(it).contentEntries.singleOrNull()?.url == gradleRootUrl }
      ?: projectModifieableModel.newModule(moduleFile.path, StdModuleTypes.JAVA.id)
    try {
      // A top level module name is usually the same as the name of the project it is contained in. If the caller of this method sets
      // up the project name correctly, we can prevent the root mdule from being disposed by sync if we configure its name correctly.
      // NOTE: We do not expect the project name to always be correct (i.e. match the name configured by Gradle at this point) and
      //       therefore it is still possible that the module created here will be disposed and re-created by sync.
      if (gradleRootPath == PathUtil.toSystemIndependentName(projectRoot.path) && module.name != project.name) {
        projectModifieableModel.renameModule(module, project.name)
      }
    } catch (ex: ModuleWithNameAlreadyExists) {
      // The top module only plays a temporary role while project is not properly synced. Ignore any errors and let sync corrent
      // the problem.
      LOG.warn("Failed to rename module '${module.name}' to '${project.name}'", ex)
    }
    projectModifieableModel.commit()
    val projectRootDirPath = PathUtil.toSystemIndependentName(gradleRoot.path)
    getInstance(module)
      .setExternalOptions(
        GradleUtil.GRADLE_SYSTEM_ID,
        ModuleData(
          ":",
          GradleUtil.GRADLE_SYSTEM_ID,
          StdModuleTypes.JAVA.id, gradleRoot.name,
          projectRootDirPath!!,
          projectRootDirPath
        ),
        ProjectData(
          /* owner = */ GradleUtil.GRADLE_SYSTEM_ID,
          /* externalName = */ project.name,
          /* ideProjectFileDirectoryPath = */ gradleRootPath,
          /* linkedExternalProjectPath = */ toCanonicalPath(gradleRoot.canonicalPath)
        )
      )
    val model = ModuleRootManager.getInstance(module).modifiableModel

    if (model.contentEntries.singleOrNull() == null) {
      model.addContentEntry(gradleRootVirtualFile)
    }
    if (IdeInfo.getInstance().isAndroidStudio) {
      // If sync fails, make sure that the project has a JDK, otherwise Groovy indices won't work (a common scenario where
      // users will update build.gradle files to fix Gradle sync.)
      // See: https://code.google.com/p/android/issues/detail?id=194621
      model.inheritSdk()
    }
    model.commit()
    val facetManager = FacetManager.getInstance(module)
    val facetModel = facetManager.createModifiableModel()
    try {
      var gradleFacet = GradleFacet.getInstance(module)
      if (gradleFacet == null) {
        // Add "gradle" facet, to avoid balloons about unsupported compilation of modules.
        gradleFacet = facetManager.createFacet(GradleFacet.getFacetType(), GradleFacet.getFacetName(), null)

        facetModel.addFacet(gradleFacet, ExternalSystemApiUtil.toExternalSource(GradleConstants.SYSTEM_ID))
      }
      gradleFacet.configuration.GRADLE_PROJECT_PATH = SdkConstants.GRADLE_PATH_SEPARATOR
    } finally {
      facetModel.commit()
    }
  }
}

private val LOG = Logger.getInstance(TopLevelModuleFactory::class.java)