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
package com.android.tools.idea.gradle.project.upgrade.ui

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity
import com.android.tools.idea.gradle.project.upgrade.ContentManager
import com.android.tools.idea.gradle.project.upgrade.LOG_CATEGORY
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

private val LOG = Logger.getInstance(LOG_CATEGORY)

class ContentManagerImpl(val project: Project): ContentManager {
  init {
    ApplicationManager.getApplication().invokeAndWait {
      // Force EDT here to ease the testing (see com.intellij.ide.plugins.CreateAllServicesAndExtensionsAction: it instantiates services
      //  on a background thread). There is no performance penalties when already invoked on EDT.
      ToolWindowManager.getInstance(project).registerToolWindow(
        RegisterToolWindowTask.closable(TOOL_WINDOW_ID, icons.GradleIcons.ToolWindowGradle)
      )
    }
  }

  override fun showContent(recommended: AgpVersion?) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    toolWindow.contentManager.removeAllContents(true)
    val model = UpgradeAssistantWindowModel(
      project, currentVersionProvider = { AndroidPluginInfo.find(project)?.pluginVersion }, recommended = recommended
    )
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    val content = ContentFactory.getInstance().createContent(view.content, model.current.contentDisplayName(), true)
    content.setDisposer(model)
    content.isPinned = true
    toolWindow.contentManager.addContent(content)
    toolWindow.show()
  }

}

const val TOOL_WINDOW_ID = "Upgrade Assistant"


fun AgpUpgradeComponentNecessity.treeText() = when (this) {
  AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT -> "Upgrade prerequisites"
  AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT -> "Upgrade"
  AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT -> "Recommended post-upgrade steps"
  AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT -> "Recommended steps"
  else -> {
    LOG.error("Irrelevant steps tree text requested")
    "Irrelevant steps"
  }
}

fun AgpUpgradeComponentNecessity.checkboxToolTipText(enabled: Boolean, selected: Boolean) =
  if (enabled) null
  else when (this to selected) {
    AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT to true -> "Cannot be deselected while ${AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT.treeText()} is selected"
    AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT to false -> "Cannot be selected while ${AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT.treeText()} is unselected"
    AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT to true -> "Cannot be deselected while ${AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT.treeText()} is selected"
    AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT to false -> "Cannot be selected while ${AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT.treeText()} is unselected"
    else -> {
      LOG.error("Irrelevant step tooltip text requested")
      null
    }
  }

fun AgpUpgradeComponentNecessity.description() = when (this) {
  AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT ->
    "These steps are required to perform the upgrade of this project.\n" +
    "You can choose to do them in separate steps, in advance of the Android\n" +
    "Gradle Plugin upgrade itself."
  AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT ->
    "These steps are required to perform the upgrade of this project.\n" +
    "They must all happen together, at the same time as the Android Gradle Plugin\n" +
    "upgrade itself."
  AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT ->
    "These steps are not required to perform the upgrade of this project at this time,\n" +
    "but will be required when upgrading to a later version of the Android Gradle\n" +
    "Plugin.  You can choose to do them in this upgrade to prepare for the future, but\n" +
    "only if the Android Gradle Plugin is upgraded to its new version."
  AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT ->
    "These steps are not required to perform the upgrade of this project at this time,\n" +
    "but will be required when upgrading to a later version of the Android Gradle\n" +
    "Plugin.  You can choose to do them in this upgrade to prepare for the future,\n" +
    "with or without upgrading the Android Gradle Plugin to its new version."
  else -> {
    LOG.error("Irrelevant step description requested")
    "These steps are irrelevant to this upgrade (and should not be displayed)"
  }
}

fun AgpVersion?.upgradeLabelText() = when (this) {
  null -> "Upgrade Android Gradle Plugin from unknown version to"
  else -> "Upgrade Android Gradle Plugin from version $this to"
}

fun AgpVersion?.contentDisplayName() = when (this) {
  null -> "Upgrade project from unknown AGP"
  else -> "Upgrade project from AGP $this"
}