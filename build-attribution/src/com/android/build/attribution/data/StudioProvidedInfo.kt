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

import com.android.SdkConstants
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.Request.RequestData
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.gradle.util.GradleVersions
import com.android.tools.idea.gradle.util.PropertiesFiles
import com.intellij.lang.properties.IProperty
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import org.gradle.util.GradleVersion
import org.jetbrains.android.refactoring.ENABLE_JETIFIER_PROPERTY
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.android.refactoring.isEnableJetifier
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import java.util.Properties

data class StudioProvidedInfo(
  val agpVersion: AgpVersion?,
  val gradleVersion: GradleVersion?,
  val configurationCachingGradlePropertyState: String?,
  val buildInvocationType: BuildInvocationType,
  val enableJetifierPropertyState: Boolean,
  val useAndroidXPropertyState: Boolean,
  val buildRequestHolder: BuildRequestHolder
) {

  val isInConfigurationCacheTestFlow: Boolean get() = buildInvocationType == BuildInvocationType.CONFIGURATION_CACHE_TRIAL

  companion object {
    private const val CONFIGURATION_CACHE_UNSAFE_PROPERTY_NAME = "org.gradle.unsafe.configuration-cache"
    private const val CONFIGURATION_CACHE_PROPERTY_NAME = "org.gradle.configuration-cache"

    fun fromProject(project: Project, buildRequest: BuildRequestHolder, buildInvocationType: BuildInvocationType) = StudioProvidedInfo(
      agpVersion = AndroidPluginInfo.find(project)?.pluginVersion,
      gradleVersion = GradleVersions.getInstance().getGradleVersion(project),
      // First check global user properties as it overrides project properties,
      // see https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties
      configurationCachingGradlePropertyState = getUserPropertiesConfigurationCachePropertyState(project)
                                                ?: getProjectPropertiesConfigurationCachePropertyState(buildRequest.buildRequest.data),
      buildInvocationType = buildInvocationType,
      enableJetifierPropertyState = project.isEnableJetifier(),
      useAndroidXPropertyState = project.isAndroidx(),
      buildRequestHolder = buildRequest
    )

    fun turnOnConfigurationCacheInProperties(project: Project, buildRequestData: RequestData, useStableFeatureProperty: Boolean) {
      getProjectProperties(project, buildRequestData, createIfNotExists = true)?.let { propertiesFile ->
        val propertyName = if (useStableFeatureProperty) CONFIGURATION_CACHE_PROPERTY_NAME else CONFIGURATION_CACHE_UNSAFE_PROPERTY_NAME
        val property = WriteCommandAction.writeCommandAction(project, propertiesFile.containingFile).compute<IProperty, Throwable> {
          propertiesFile.findPropertyByKey(propertyName)?.apply { setValue("true") }
          ?: propertiesFile.addProperty(propertyName, "true")
        }
        val propertyOffset = property?.psiElement?.textOffset ?: -1
        OpenFileDescriptor(project, propertiesFile.virtualFile, propertyOffset).navigate(true)
      }
    }

    fun disableJetifier(project: Project, buildRequestData: RequestData, runAfterDisabling: (IProperty?) -> Unit) {
      getProjectProperties(project, buildRequestData, createIfNotExists = true)?.let { propertiesFile ->
        WriteCommandAction.writeCommandAction(project, propertiesFile.containingFile).run<Throwable> {
          propertiesFile.findPropertyByKey(ENABLE_JETIFIER_PROPERTY).let {
            it?.setValue("false")
            runAfterDisabling(it)
          }
        }
      }
    }

    private fun getUserPropertiesConfigurationCachePropertyState(project: Project): String? {
      return runCatching {
        PropertiesFiles.getProperties(GradleProjectSystemUtil.getUserGradlePropertiesFile(project))
      }.getOrNull()?.getConfigurationCachePropertyState()
    }

    private fun getProjectPropertiesConfigurationCachePropertyState(buildRequestData: RequestData): String? {
      return runCatching {
        PropertiesFiles.getProperties(buildRequestData.rootProjectPath.resolve(SdkConstants.FN_GRADLE_PROPERTIES))
      }.getOrNull()?.getConfigurationCachePropertyState()
    }

    private fun Properties.getConfigurationCachePropertyState() = sequence {
      yield(getProperty(CONFIGURATION_CACHE_PROPERTY_NAME))
      yield(getProperty(CONFIGURATION_CACHE_UNSAFE_PROPERTY_NAME))
    }.filterNotNull().firstOrNull()

    private fun getProjectProperties(project: Project, buildRequestData: RequestData, createIfNotExists: Boolean = false): PropertiesFile? {
      if (project.isDisposed) return null
      val projectBaseDirectory = VfsUtil.findFileByIoFile(buildRequestData.rootProjectPath, true) ?: return null
      val gradlePropertiesFile = if (createIfNotExists) {
        projectBaseDirectory.findOrCreateChildData(this, SdkConstants.FN_GRADLE_PROPERTIES)
      }
      else {
        projectBaseDirectory.findChild(SdkConstants.FN_GRADLE_PROPERTIES)
      }
      val psiPropertiesFile = PsiManager.getInstance(project).findFile(gradlePropertiesFile ?: return null)

      return if (psiPropertiesFile is PropertiesFile) psiPropertiesFile else null
    }
  }
}