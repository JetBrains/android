/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.toolchain

import com.android.tools.idea.gradle.extensions.getPropertyPath
import com.android.utils.FileUtils
import com.google.common.io.Resources
import com.intellij.util.lang.JavaVersion
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

object GradleDaemonJvmCriteriaTemplatesManager {

  const val TEMPLATE_CRITERIA_PROPERTIES_FILE_FORMAT = "gradle-daemon-jvm-%d.properties"
  const val TEMPLATE_METADATA_FILE = "metadata.properties"
  const val TEMPLATE_RESOURCES_PATH = "/templates/project/toolchain/"

  /**
   * Generates 'gradle/gradle-daemon-jvm.properties' file storing Daemon JVM criteria with exactly same content
   * format as resulted of executing 'updateDaemonJvm' Gradle task obtained from the template resources
   */
  fun generatePropertiesFile(javaVersion: JavaVersion, externalProjectPath: @SystemIndependent String): CompletableFuture<Boolean> {
    val completableFuture = CompletableFuture<Boolean>()
    try {
      val propertiesContent = getTemplateCriteriaPropertiesContent(javaVersion)
      if (propertiesContent.isNullOrEmpty()) {
        completableFuture.complete(false)
      } else {
        val jvmCriteriaPropertiesFile = GradleDaemonJvmPropertiesFile.getPropertyPath(externalProjectPath).toFile()
        FileUtils.createFile(jvmCriteriaPropertiesFile, propertiesContent)
        completableFuture.complete(true)
      }
    } catch (e: Exception) {
      completableFuture.completeExceptionally(e)
    }
    return completableFuture
  }

  @VisibleForTesting
  fun getTemplateCriteriaPropertiesContent(javaVersion: JavaVersion): String? {
    val propertiesTemplatePath = TEMPLATE_RESOURCES_PATH + getTemplatePropertiesFileName(javaVersion)
    val propertiesResourceUrl = Resources.getResource(GradleDaemonJvmCriteriaTemplatesManager::class.java, propertiesTemplatePath)
    return Resources.toString(propertiesResourceUrl, StandardCharsets.UTF_8)
  }

  @VisibleForTesting
  fun getTemplatePropertiesFileName(version: JavaVersion) = TEMPLATE_CRITERIA_PROPERTIES_FILE_FORMAT.format(version.feature)

  @VisibleForTesting
  fun getTemplateMetadata(): URL {
    val metadataPath = TEMPLATE_RESOURCES_PATH + TEMPLATE_METADATA_FILE
    return Resources.getResource(GradleDaemonJvmCriteriaTemplatesManager::class.java, metadataPath)
  }
}