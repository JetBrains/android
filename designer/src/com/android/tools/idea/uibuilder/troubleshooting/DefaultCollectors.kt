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
package com.android.tools.idea.uibuilder.troubleshooting

import com.android.tools.idea.editors.build.PsiCodeFileChangeDetectorService
import com.android.tools.idea.editors.build.PsiCodeFileOutOfDateStatusReporter
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.LibraryDependenciesTroubleInfoCollectorToken
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.getTokenOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.troubleshooting.TroubleInfoCollector
import org.jetbrains.kotlin.idea.base.util.isGradleModule

/**
 * List of prefixes of libraries that we want to collect version info from the project. This list is
 * used to prevent collecting unnecessary information of the user libraries.
 */
private val libraryAllowedPrefixes = listOf("com.google.", "androidx.", "org.jetbrains.kotlin.")

/** General collector with general information about the project. */
internal class ProjectInfoTroubleInfoCollector : TroubleInfoCollector {
  override fun collectInfo(project: Project): String {
    val output = StringBuilder()

    output.appendLine("Project:")
    project.modules.forEach { module ->
      val moduleSystem = module.getModuleSystem()
      val token =
        project
          .getProjectSystem()
          .getTokenOrNull(LibraryDependenciesTroubleInfoCollectorToken.EP_NAME)
      val libraryDependencies =
        token?.run { getDependencies(moduleSystem, module) }
          // fall back to the library dependencies of the main artifact, if the module system can
          // determine that.
          ?: moduleSystem.getAndroidLibraryDependencies(DependencyScopeType.MAIN)
      output.appendLine(
        """Module(${module.name}): isLoaded=${module.isLoaded} type=${moduleSystem.type} isDisposed=${module.isDisposed}
  isGradleModule=${module.isGradleModule}${token?.getInfoString(module) ?: "\n "} useAndroidX=${moduleSystem.useAndroidX} rClassTransitive=${moduleSystem.isRClassTransitive}
  libDepCount=${libraryDependencies.size}"""
      )
      libraryDependencies.forEach { library ->
        val libraryName =
          if (libraryAllowedPrefixes.any { library.address.startsWith(it) }) library.address
          else "<user-lib>"
        output.appendLine("  Library: $libraryName hasResources=${library.hasResources}")
      }
    }
    return output.toString()
  }
}

/** [TroubleInfoCollector] for build status. */
internal class BuildStatusTroubleInfoCollector : TroubleInfoCollector {
  override fun collectInfo(project: Project): String {
    val buildManager = ProjectSystemService.getInstance(project).projectSystem.getBuildManager()
    return "LastBuildResult: ${buildManager.getLastBuildResult()}"
  }
}

/** [TroubleInfoCollector] for [FastPreviewManager] status. */
internal class FastPreviewTroubleInfoCollector : TroubleInfoCollector {
  override fun collectInfo(project: Project): String {
    val fastPreview = FastPreviewManager.getInstance(project)
    return if (fastPreview.isEnabled)
      "FastPreviewStatus: available=${fastPreview.isAvailable} disableReason=${fastPreview.disableReason}"
    else ""
  }
}

/** [TroubleInfoCollector] for [PsiCodeFileChangeDetectorService] status. */
internal class PsiCodeFileChangeDetectorServiceTroubleInfoCollector : TroubleInfoCollector {
  override fun collectInfo(project: Project): String =
    "PsiCodeFileOutOfDateStatusReporter: outOfDateFiles=[${PsiCodeFileOutOfDateStatusReporter.getInstance(project).outOfDateFiles.joinToString(",")}]"
}
