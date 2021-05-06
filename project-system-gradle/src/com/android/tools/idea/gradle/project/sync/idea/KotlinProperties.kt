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
package com.android.tools.idea.gradle.project.sync.idea

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.kotlin.gradle.ArgsInfo
import org.jetbrains.kotlin.idea.configuration.compilerArgumentsBySourceSet
import org.jetbrains.kotlin.idea.configuration.hasKotlinPlugin
import org.jetbrains.kotlin.idea.configuration.isResolved

class KotlinProperties {
  var isResolved = false
  var compilerArgumentsBySourceSet: Map<String, ArgsInfo>? = null

  fun repopulateKotlinProperties(node: DataNode<ModuleData>) {
    node.hasKotlinPlugin = true
    node.isResolved = isResolved
    node.compilerArgumentsBySourceSet = compilerArgumentsBySourceSet
  }

  companion object {
    @JvmStatic
    fun createKotlinProperties(node: DataNode<ModuleData>): KotlinProperties {
      return KotlinProperties().also { properties ->
        properties.isResolved = node.isResolved
        properties.compilerArgumentsBySourceSet = node.compilerArgumentsBySourceSet
      }
    }
  }
}

fun DataNode<ProjectData>.preserveKotlinUserDataInDataNodes() {
  for (child in children) {
    val moduleDataNode = child.getDataNode(ProjectKeys.MODULE)
    if (moduleDataNode != null) {
      if (moduleDataNode.hasKotlinPlugin) {
        moduleDataNode.createChild(AndroidGradleProjectResolver.KOTLIN_PROPERTIES, KotlinProperties.createKotlinProperties(moduleDataNode))
      }
    }
  }
}

fun DataNode<ProjectData>.restoreKotlinUserDataFromDataNodes() {
  val moduleNodes = ExternalSystemApiUtil.findAll(this, ProjectKeys.MODULE)
  for (moduleNode in moduleNodes) {
    val kotlinPropertiesDataNode = ExternalSystemApiUtil.find(moduleNode, AndroidGradleProjectResolver.KOTLIN_PROPERTIES)
    kotlinPropertiesDataNode?.data?.repopulateKotlinProperties(moduleNode)
  }
}

