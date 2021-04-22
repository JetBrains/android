/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.build.attribution.data

import com.android.build.attribution.ui.controllers.ConfigurationCacheTestBuildFlowRunner
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.android.refactoring.getProjectProperties
import org.jetbrains.kotlin.idea.util.application.runReadAction

data class StudioProvidedInfo(
  val agpVersion: GradleVersion?,
  val configurationCachingGradlePropertyState: String?,
  val isInConfigurationCacheTestFlow: Boolean
) {
  companion object {
    private const val CONFIGURATION_CACHE_PROPERTY_NAME = "org.gradle.unsafe.configuration-cache"

    fun fromProject(project: Project) = StudioProvidedInfo(
      agpVersion = AndroidPluginInfo.find(project)?.pluginVersion,
      configurationCachingGradlePropertyState = runReadAction {
        project.getProjectProperties(createIfNotExists = true)?.findPropertyByKey(CONFIGURATION_CACHE_PROPERTY_NAME)?.value
      },
      isInConfigurationCacheTestFlow = ConfigurationCacheTestBuildFlowRunner.getInstance(project).runningTestConfigurationCacheBuild
    )

    fun turnOnConfigurationCacheInProperties(project: Project) {
      project.getProjectProperties(createIfNotExists = true)?.apply {
        WriteCommandAction.writeCommandAction(project, this.containingFile).run<Throwable> {
          findPropertyByKey(CONFIGURATION_CACHE_PROPERTY_NAME)?.setValue("true") ?: addProperty(CONFIGURATION_CACHE_PROPERTY_NAME, "true")
        }
        val propertyOffset = findPropertyByKey(CONFIGURATION_CACHE_PROPERTY_NAME)?.psiElement?.textOffset ?: -1
        OpenFileDescriptor(project, virtualFile, propertyOffset).navigate(true)
      }
    }
  }
}