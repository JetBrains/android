/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.utils

import com.android.tools.idea.gradle.project.sync.model.GradleRoot

private const val PROJECT_DIR = "${'$'}PROJECT_DIR${'$'}"

object ProjectIdeaConfigFilesUtils {

  fun buildMiscXmlConfig(jdkName: String) =
    """
      |<?xml version="1.0" encoding="UTF-8"?>
      |<project version="4">
      |  <component name="ProjectRootManager" version="2" project-jdk-name="$jdkName" project-jdk-type="JavaSDK" />
      |</project>
    """.trimMargin()

  fun buildGradleXmlConfig(gradleRoots: List<GradleRoot>) =
    """
      |<?xml version="1.0" encoding="UTF-8"?>
      |<project version="4">
      |  <component name="GradleSettings">
      |    <option name="linkedExternalProjectsSettings">
      |      ${buildGradleSettings(gradleRoots)}
      |    </option>
      |  </component>
      |</project>
    """.trimMargin()

  private fun buildGradleSettings(gradleRoots: List<GradleRoot>) = buildString {
    gradleRoots.forEach {
      appendLine(buildGradleSettings(it))
    }
  }.trim()

  private fun buildGradleSettings(gradleRoot: GradleRoot) =
    """
      <GradleProjectSettings>
        <option name="testRunner" value="GRADLE" />
        <option name="distributionType" value="DEFAULT_WRAPPED" />
        <option name="externalProjectPath" value="${getProjectPath(gradleRoot.name)}" />
        ${gradleRoot.gradleJvm?.let { buildGradleJvmXml(it) } ?: run { "" }}
        ${buildProjectModulesXml(gradleRoot.modulesPath)}
      </GradleProjectSettings>
    """

  private fun getProjectPath(rootName: String) =
    if (rootName.isEmpty() || rootName.isBlank()) PROJECT_DIR else "$PROJECT_DIR/$rootName"

  private fun buildGradleJvmXml(gradleJvm: String) =
    "<option name=\"gradleJvm\" value=\"$gradleJvm\" />"

  private fun buildProjectModulesXml(modules: List<String>) =
    """
     <option name="modules">
       <set>
         ${buildProjectModulesListXml(modules)}
       </set>
     </option>
    """.trimIndent()

  private fun buildProjectModulesListXml(modules: List<String>) = buildString {
    appendLine("<option value=\"$PROJECT_DIR\" />")
    modules.forEach {
      appendLine("<option value=\"${getProjectPath(it)}\" />")
    }
  }.trim()
}