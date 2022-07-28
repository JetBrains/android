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
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.kotlin.idea.gradleJava.configuration.CompilerArgumentsCacheMergeManager
import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheAware

class KotlinProperties(private val data: Map<Long, Map<Int, String?>>) {

  fun repopulateKotlinProperties() {
    data.forEach { (cacheOriginIdentifier, data) ->
      CompilerArgumentsCacheMergeManager.compilerArgumentsCacheHolder.mergeCacheAware(
        object: CompilerArgumentsCacheAware {
          override val cacheOriginIdentifier: Long = cacheOriginIdentifier
          override fun distributeCacheIds(): Iterable<Int> = data.keys
          override fun getCached(cacheId: Int): String? = data[cacheId]
        }
      )
    }
  }

  companion object {
    @JvmStatic
    fun createKotlinProperties(cacheOriginIdentifiers: Collection<Long>): KotlinProperties {
      return KotlinProperties(
        cacheOriginIdentifiers
          .mapNotNull {
            it to (CompilerArgumentsCacheMergeManager.compilerArgumentsCacheHolder.getCacheAware(it) ?: return@mapNotNull null)
          }
          .associate {
            it.first to it.second.distributeCacheIds().associateWith { id -> it.second.getCached(id) }
          }
      )
    }
  }
}

fun DataNode<ProjectData>.preserveKotlinUserDataInDataNodes(cacheOriginIdentifiers: Collection<Long>) {
  createChild(AndroidGradleProjectResolver.KOTLIN_PROPERTIES, KotlinProperties.createKotlinProperties(cacheOriginIdentifiers))
}

fun DataNode<ProjectData>.restoreKotlinUserDataFromDataNodes() {
  val kotlinPropertiesDataNode = ExternalSystemApiUtil.find(this, AndroidGradleProjectResolver.KOTLIN_PROPERTIES)
  kotlinPropertiesDataNode?.data?.repopulateKotlinProperties()
}
