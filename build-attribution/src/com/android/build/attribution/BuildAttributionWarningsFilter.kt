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
package com.android.build.attribution

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "BuildAttributionWarningsFilter")
class BuildAttributionWarningsFilter : PersistentStateComponent<BuildAttributionWarningsFilter.SuppressedWarnings> {
  private var suppressedWarnings: SuppressedWarnings = SuppressedWarnings()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): BuildAttributionWarningsFilter {
      return ServiceManager.getService(project, BuildAttributionWarningsFilter::class.java)
    }
  }

  // TODO(b/138362804): use task class name instead of gradle's task name, to avoid having to suppress tasks per variant
  fun applyTaskFilter(taskName: String): Boolean {
    return !suppressedWarnings.tasks.contains(taskName)
  }

  fun applyPluginFilter(pluginDisplayName: String): Boolean {
    return !suppressedWarnings.plugins.contains(pluginDisplayName)
  }

  fun applyAnnotationProcessorFilter(annotationProcessorClassName: String): Boolean {
    return !suppressedWarnings.annotationProcessors.contains(annotationProcessorClassName)
  }

  fun suppressWarningsForTask(taskName: String) {
    suppressedWarnings.tasks.add(taskName)
  }

  fun suppressWarningsForPlugin(pluginDisplayName: String) {
    suppressedWarnings.plugins.add(pluginDisplayName)
  }

  fun suppressWarningsForAnnotationProcessor(annotationProcessorClassName: String) {
    suppressedWarnings.annotationProcessors.add(annotationProcessorClassName)
  }

  fun unsuppressWarningsForTask(taskName: String) {
    suppressedWarnings.tasks.remove(taskName)
  }

  fun unsuppressWarningsForPlugin(pluginDisplayName: String) {
    suppressedWarnings.plugins.remove(pluginDisplayName)
  }

  fun unsuppressWarningsForAnnotationProcessor(annotationProcessorClassName: String) {
    suppressedWarnings.annotationProcessors.remove(annotationProcessorClassName)
  }

  override fun getState(): SuppressedWarnings? {
    return suppressedWarnings
  }

  override fun loadState(state: SuppressedWarnings) {
    suppressedWarnings = state
  }

  data class SuppressedWarnings(val tasks: MutableSet<String> = HashSet(),
                                val plugins: MutableSet<String> = HashSet(),
                                val annotationProcessors: MutableSet<String> = HashSet())
}
