/*
 * Copyright (C) 2019 The Android Open Source Project
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
@file:JvmName("DataNodeDumper")

package com.android.tools.idea.gradle.project.sync.internal

import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.plugins.gradle.model.ExternalProject

fun <T : Any> DataNode<T>.dump(): String = buildString {

  fun Any.dumpData() = when (this) {
    is ModuleData -> "\n" + """
      id = $id
      owner = $owner
      moduleTypeId = $moduleTypeId
      externalName = $externalName
      moduleFileDirectoryPath = $moduleFileDirectoryPath
      externalConfigPath = $linkedExternalProjectPath""".replaceIndent("        ")
    is GradleModuleModel -> "\n" + """
      moduleName = $moduleName
      taskNames = ${taskNames.take(3)}...
      gradlePath = $gradlePath
      rootFolderPath = $rootFolderPath
      gradlePlugins = $gradlePlugins
      buildFilePath = $buildFilePath
      gradleVersion = $gradleVersion
      agpVersion = $agpVersion
      isKaptEnabled = $isKaptEnabled""".replaceIndent("        ")
    is ExternalProject -> "\n" + """
      externalSystemId = $externalSystemId
      id = $id
      name = $name
      qName = $qName
      description = $description
      group = $group
      version = $version
      getChildProjects = $childProjects
      projectDir = $projectDir
      buildDir = $buildDir
      buildFile = $buildFile
      tasks = ${tasks.entries.take(3)}...
      sourceSets = $sourceSets
      artifacts = ${artifacts.take(3)}...
      artifactsByConfiguration = ${artifactsByConfiguration.entries.take(3)}...
      """.replaceIndent("        ")
    else -> toString()
  }

  fun DataNode<T>.dumpNode(prefix: String = "") {
    val v: T = data
    appendln("$key(${v.javaClass.typeName}) ==> ${v.dumpData()}".replaceIndent(prefix))

    children
      .filterNot { it.data is com.intellij.openapi.externalSystem.model.task.TaskData }
      .forEach { it.safeAs<DataNode<T>>()?.dumpNode("$prefix    ") }
  }

  this@dump.dumpNode()
}