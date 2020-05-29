/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink

import com.android.tools.idea.gradle.util.GradleProperties
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.android.refactoring.ENABLE_JETIFIER_PROPERTY
import org.jetbrains.android.refactoring.USE_ANDROIDX_PROPERTY
import java.util.Properties

class EnableAndroidXHyperlink: NotificationHyperlink(
  "enable.AndroidX",
  "Enable AndroidX in project's Gradle properties"
) {
  override fun execute(project: Project) {
    if (project.isDisposed) {
      return
    }
    val gradleProperties = GradleProperties(project)
    setProperties(gradleProperties.properties)
    gradleProperties.save()
    LocalFileSystem.getInstance().refreshIoFiles(ImmutableList.of(gradleProperties.path))
  }

  @VisibleForTesting
  fun setProperties(properties: Properties) {
    properties.setProperty(USE_ANDROIDX_PROPERTY, "true")
    properties.setProperty(ENABLE_JETIFIER_PROPERTY, "true")
  }
}