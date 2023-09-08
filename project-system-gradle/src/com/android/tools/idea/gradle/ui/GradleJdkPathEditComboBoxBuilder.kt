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
package com.android.tools.idea.gradle.ui

import com.android.tools.idea.sdk.Jdks
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.SystemIndependent
import java.nio.file.Path

/**
 * Builder for [GradleJdkPathEditComboBox] that creates the component based on a provided list of [Sdk] usually obtained from jdk.table entries.
 * Responsible in filtering, validating and sorting the provided jdks that will be displayed in the component dropdown.
 */
object GradleJdkPathEditComboBoxBuilder {

  fun build(
    currentJdkPath: @SystemIndependent String?,
    embeddedJdkPath: Path,
    suggestedJdks: List<Sdk>,
    hintMessage: String,
  ) = GradleJdkPathEditComboBox(
    currentJdkPath = currentJdkPath ?: embeddedJdkPath.toString(),
    suggestedJdkPaths = getKnownValidJdks(suggestedJdks, embeddedJdkPath),
    hintMessage = hintMessage
  )

  private fun getKnownValidJdks(jdkList: List<Sdk>, embeddedJdkPath: Path) =
    jdkList
      .asSequence()
      .filter { ExternalSystemJdkUtil.isValidJdk(it) }
      .mapNotNull { it.homeDirectory?.toNioPath() }
      .plus(listOf(embeddedJdkPath))
      .distinct()
      .sortedByDescending { Jdks.getInstance().findVersion(it) }
      .mapNotNull { path ->
        JavaSdk.getInstance().getVersionString(path.toString())?.let { version ->
          LabelAndFileForLocation(version, path)
        }
      }
      .toList()
}