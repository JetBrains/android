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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.editor.AndroidRunConfigurationEditor
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.RunManager
import com.intellij.execution.impl.ProjectRunConfigurationConfigurable
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.GlassPanel
import com.intellij.openapi.options.newEditor.SingleSettingEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.ui.components.JBTabbedPane
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import java.awt.Component
import java.awt.Container
import java.awt.Graphics2D
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

@VisibleForTesting
const val KEY_HIDE_ACTIVITY_RESTART_BANNER = "live.layout.inspector.activity.restart.banner.hide"

/**
 * Show a banner with "Activity Restarted" and a link to turn on "Layout inspection without an activity restart".
 */
fun showActivityRestartedInBanner(project: Project, process: ProcessDescriptor) {

  /**
   * Action for opening the "Miscellaneous" tab in the Android run configuration for the project,
   * and mark where the "Enable layout inspection without an activity restart" checkbox is.
   */
  val enableInRunConfigAction = object : AnAction(LayoutInspectorBundle.message("activity.restart.action")) {
    override fun actionPerformed(event: AnActionEvent) {
      val configurable = ProjectRunConfigurationConfigurable(project)
      ShowSettingsUtil.getInstance().editConfigurable(project, configurable) {
        configurable.selectConfigurableOnShow()
        ApplicationManager.getApplication().invokeLater {
          val dialog = SwingUtilities.windowForComponent(configurable.tree)
          val tabbedPanel = dialog.firstComponentOfClass(JBTabbedPane::class.java)
          val checkBox = tabbedPanel?.firstComponentWithName(AndroidRunConfigurationEditor.LAYOUT_INSPECTION_WITHOUT_ACTIVITY_RESTART)
                           as? JComponent ?: return@invokeLater
          val editor = SwingUtilities.getAncestorOfClass(SingleSettingEditor::class.java, tabbedPanel) as SingleSettingEditor
          val glassPanel = GlassPanel(editor)
          val painter = object : AbstractPainter() {
            override fun executePaint(component: Component, g: Graphics2D) {
              glassPanel.paintSpotlight(g, editor)
            }
            override fun needsRepaint() = true
          }
          glassPanel.addSpotlight(checkBox)
          val disposable = Disposer.newDisposable()
          IdeGlassPaneUtil.installPainter(editor, painter, disposable)
          tabbedPanel.selectTabWithComponent(checkBox)
          dialog.addComponentListener(object : ComponentAdapter() {
            override fun componentHidden(event: ComponentEvent) {
              // Remove the painter when the dialog is closed:
              Disposer.dispose(disposable)
            }
          })
        }
      }
    }
  }

  val doNotShowAgainAction = object : AnAction(LayoutInspectorBundle.message("do.not.show.again")) {
    override fun actionPerformed(event: AnActionEvent) {
      PropertiesComponent.getInstance().setValue(KEY_HIDE_ACTIVITY_RESTART_BANNER, true)
      val banner = InspectorBannerService.getInstance(project) ?: return
      banner.DISMISS_ACTION.actionPerformed(event)
    }
  }

  if (PropertiesComponent.getInstance().getBoolean(KEY_HIDE_ACTIVITY_RESTART_BANNER)) {
    return // The user already opted out of this banner
  }

  // Only add enableInRunConfigAction if we are inspecting the current project.
  val module = ApplicationManager.getApplication().runReadAction<Module?> {
    moduleFromCurrentProjectBeingInspected(project, process)
  }

  val banner = InspectorBannerService.getInstance(project) ?: return
  val config = module?.let { getConfiguration(module) }
  val showEnableAction = config?.INSPECTION_WITHOUT_ACTIVITY_RESTART == false
  val actions = mutableListOf(doNotShowAgainAction, banner.DISMISS_ACTION)
  if (showEnableAction) {
    actions.add(0, enableInRunConfigAction)
  }
  banner.setNotification(LayoutInspectorBundle.message("activity.restart"), actions)
}

private fun moduleFromCurrentProjectBeingInspected(project: Project, process: ProcessDescriptor): Module? =
  project.allModules().firstOrNull { process.name == AndroidModuleInfo.getInstance(it)?.`package` }

/**
 * Get the [AndroidRunConfiguration] for the specified [module].
 */
private fun getConfiguration(module: Module): AndroidRunConfiguration? {
  val runManager = RunManager.getInstance(module.project)
  return runManager.allConfigurationsList
    .filterIsInstance<AndroidRunConfiguration>()
    .singleOrNull { it.configurationModule.module == module }
}

/**
 * Return the first subcomponent (DFS) of class [cls] found in the current [Container]
 */
private fun <T : Component> Component.firstComponentOfClass(cls: Class<T>): T? {
  if (cls.isInstance(this)) {
    return cls.cast(this)
  }
  if (this is Container) {
    return components.asSequence().mapNotNull { it.firstComponentOfClass(cls) }.firstOrNull()
  }
  return null
}

/**
 * Return the first subcomponent (DFS) with the name [name] found in the current [Container]
 */
private fun Component.firstComponentWithName(name: String): Component? {
  if (this.name == name) {
    return this
  }
  if (this is Container) {
    components.asSequence().forEach { component ->
      component.firstComponentWithName(name)?.let { return it }
    }
  }
  return null
}

/**
 * Select the tab that contains the [wantedComponent].
 */
private fun JBTabbedPane.selectTabWithComponent(wantedComponent: Component) {
  val tabs = components
  var component: Component? = wantedComponent
  while (component != null) {
    if (component in tabs) {
      selectedComponent = component
      return
    }
    component = component.parent
  }
}
