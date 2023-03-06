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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.flags.ExperimentalSettingsConfigurable
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.view.ExternalProjectsView
import com.intellij.openapi.externalSystem.view.ExternalProjectsViewImpl
import com.intellij.openapi.externalSystem.view.ExternalSystemNode
import com.intellij.openapi.externalSystem.view.ExternalSystemViewContributor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.ui.ContextHelpLabel
import com.intellij.util.containers.MultiMap
import icons.StudioIcons
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * Contributor that adds a label in the Gradle tool window if the list of tasks is not built.
 */
class GradleTaskEmptyContributor: ExternalSystemViewContributor() {
  companion object {
    private const val LABEL_TEXT = "Task list not built..."
    private const val TOOLTIP_TEXT = """
      Only test tasks are configured during Gradle Sync. Configuring all tasks can impact Gradle Sync performance on large projects.
    """
    private const val TOOLTIP_LINK_TEXT = "Show experimental settings..."
  }

  override fun getSystemId(): ProjectSystemId {
    return SYSTEM_ID
  }

  override fun getKeys(): MutableList<Key<*>> {
    return mutableListOf(
      ProjectKeys.MODULE,
      ProjectKeys.DEPENDENCIES_GRAPH,
      ProjectKeys.MODULE_DEPENDENCY,
      ProjectKeys.LIBRARY_DEPENDENCY,
      ProjectKeys.TASK,
    )
  }

  override fun createNodes(
    externalProjectsView: ExternalProjectsView?,
    dataNodes: MultiMap<Key<*>, DataNode<*>>?
  ): MutableList<ExternalSystemNode<*>> {
    if (externalProjectsView is ExternalProjectsViewImpl && externalProjectsView.toolbar != null) {
      addNoTaskLabelAndSetVisibility(externalProjectsView)
    }
    return mutableListOf()
  }

  private fun addNoTaskLabelAndSetVisibility(externalProjectsView: ExternalProjectsViewImpl) {
    val toolbar = externalProjectsView.toolbar ?: return

    val labelName = "noTaskLabel"
    var noTasksLabel = toolbar.components.find { component -> component.name == labelName }
    // Add label if it is not there yet
    if (noTasksLabel == null) {
      val showSettingsRunnable = Runnable {
        ShowSettingsUtil.getInstance().showSettingsDialog(externalProjectsView.project, ExperimentalSettingsConfigurable::class.java)
      }

      noTasksLabel = ContextHelpLabel.createWithLink(null, TOOLTIP_TEXT, TOOLTIP_LINK_TEXT, showSettingsRunnable)
      noTasksLabel.text = "<html><a href='change.experimental.settings'>$LABEL_TEXT<a></html>"
      noTasksLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      noTasksLabel.icon = StudioIcons.Common.INFO
      noTasksLabel.name = labelName
      noTasksLabel.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          try {
            showSettingsRunnable.run()
          }
          catch (ex: Exception) {
            // Pass;
          }
        }
      })
      toolbar.add(noTasksLabel)
    }
    noTasksLabel.isVisible = GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST
  }
}