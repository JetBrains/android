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
package com.android.tools.idea.gradle.util

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.gradle.properties.GRADLE_CACHE_DIR_NAME
import org.jetbrains.plugins.gradle.properties.GRADLE_LOCAL_JAVA_HOME_PROPERTY
import org.jetbrains.plugins.gradle.properties.GRADLE_LOCAL_PROPERTIES_FILE_NAME
import java.io.File
import java.nio.file.Paths

/**
 * Utility methods related to a Gradle cache directory located under .gradle/config.properties
 */
class GradleConfigProperties(
  projectFolderPath: File
) {

  constructor(externalProjectSettings: ExternalProjectSettings): this(File(externalProjectSettings.externalProjectPath))

  var javaHome: File? = null
    get() = if (isJavaHomeModified) field else getPath(GRADLE_LOCAL_JAVA_HOME_PROPERTY)
    set(value) {
      isJavaHomeModified = true
      field = value
    }
  val propertiesFilePath = projectFolderPath.resolve(
    Paths.get(GRADLE_CACHE_DIR_NAME, GRADLE_LOCAL_PROPERTIES_FILE_NAME).toString()
  )
  private var isJavaHomeModified = false
  private val properties = PropertiesFiles.getProperties(propertiesFilePath)

  fun save() {
    setPathIfApplicable(isJavaHomeModified, GRADLE_LOCAL_JAVA_HOME_PROPERTY, getPath(GRADLE_LOCAL_JAVA_HOME_PROPERTY), javaHome)
    if (isJavaHomeModified) {
      PropertiesFiles.savePropertiesToFile(properties, propertiesFilePath, null)
    }
    javaHome = null
    isJavaHomeModified = false
  }

  private fun setPathIfApplicable(pathModified: Boolean, propertyName: String, currentPath: File?, newPath: File?) {
    if (pathModified && !FileUtil.filesEqual(currentPath, newPath)) {
      if (newPath?.path != null) {
        properties.setProperty(propertyName, newPath.path)
      } else {
        properties.remove(propertyName)
      }
    }
  }

  private fun getPath(property: String): File? {
    val path = properties.getProperty(property) ?: return null
    return File(path)
  }
}