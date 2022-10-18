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

private const val PROJECT_DIR = "${'$'}PROJECT_DIR${'$'}"

object ProjectIdeaConfigFilesUtils {

  fun buildMiscXmlConfig(jdkName: String) =
    """
      |<?xml version="1.0" encoding="UTF-8"?>
      |<project version="4">
      |  <component name="ProjectRootManager" version="2" project-jdk-name="$jdkName" project-jdk-type="JavaSDK" />
      |</project>
    """.trimMargin()

  fun buildGradleXmlConfig(
    rootsName: List<String> = listOf(""),
    jdkName: String? = null,
    projectModules: List<String>? = null) =
    """
      |<?xml version="1.0" encoding="UTF-8"?>
      |<project version="4">
      |  <component name="GradleSettings">
      |    <option name="linkedExternalProjectsSettings">
      |      ${buildGradleSettings(rootsName, jdkName, projectModules)}
      |    </option>
      |  </component>
      |</project>
    """.trimMargin()

  private fun buildGradleSettings(rootName: List<String>, jdkName: String?, projectModules: List<String>?) = buildString {
    rootName.forEach {
      appendLine(buildGradleSettings(it, jdkName, projectModules))
    }
  }.trim()

  private fun buildGradleSettings(rootName: String, jdkName: String?, projectModules: List<String>?) =
    """
      <GradleProjectSettings>
        <option name="testRunner" value="GRADLE" />
        <option name="distributionType" value="DEFAULT_WRAPPED" />
        <option name="externalProjectPath" value="${getProjectPath(rootName)}" />
        ${jdkName?.let { buildGradleJvmXml(it) } ?: run { "" }}
        ${projectModules?.let { buildProjectModulesXml(it) } ?: run { "" }}
      </GradleProjectSettings>
    """

  private fun getProjectPath(rootName: String) =
    if (rootName.isEmpty() || rootName.isBlank()) PROJECT_DIR else "$PROJECT_DIR/$rootName"

  private fun buildGradleJvmXml(jdkName: String) =
    "<option name=\"gradleJvm\" value=\"$jdkName\" />"

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