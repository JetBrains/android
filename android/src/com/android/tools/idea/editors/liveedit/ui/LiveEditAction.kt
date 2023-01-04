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
package com.android.tools.idea.editors.liveedit.ui

import com.android.ddmlib.IDevice
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.editors.literals.LiveEditAnActionListener
import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.sourcecode.isKotlinFileType
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.util.AndroidBundle
import java.awt.Insets
import java.util.Collections
import javax.swing.JComponent
import javax.swing.plaf.FontUIResource

/**
 * An action that displays the Live Edit status in either the edit window or an embedded emulator window.
 */
class LiveEditAction(private val instanceEditor: Editor? = null) : DropDownAction(
  "Live Edit", "Live Edit status", AllIcons.General.InspectionsOKEmpty), CustomComponentAction {
  companion object {
    val FOREGROUND = ColorKey.createColorKey("ActionButton.iconTextForeground", UIUtil.getContextHelpForeground())
    val LIVE_EDIT_STATUS = Key<LiveEditStatus>("android.liveedit.action.editstatus")
    val LIVE_EDIT_STATUS_PREVIOUS = Key<LiveEditStatus>("android.liveedit.action.editstatus.previous")
    val MANUAL_LIVE_EDIT_METHOD = Key<() -> Unit>("android.liveedit.action.manual")
    val deviceMap = HashMap<Project, DeviceGetter>()

    fun registerProject(project: Project, deviceGetter: DeviceGetter) {
      deviceMap[project] = deviceGetter
    }

    fun unregisterProject(project: Project) {
      deviceMap.remove(project)
    }
  }

  /**
   * An interface to exposed to external modules to implement in order to receive device serials and IDevices associated with those serials.
   */
  interface DeviceGetter {
    fun serial(dataContext: DataContext): String?
    fun device(dataContext: DataContext): IDevice?
    fun devices(): List<IDevice>
  }

  lateinit var component: ActionButtonWithText

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    component = object : ActionButtonWithText(this, presentation, place, JBUI.size(18)) {
      override fun iconTextSpace() = JBUI.scale(2)
      override fun getInsets(): Insets = if (place == "EditorInspectionsToolbar") JBUI.insets(2) else JBUI.emptyInsets()
      override fun getMargins(): Insets = if (place == "EditorInspectionsToolbar") JBUI.insetsRight(5) else JBUI.emptyInsets()

      override fun updateToolTipText() {
        if (Registry.`is`("ide.helptooltip.enabled")) {
          val status = myPresentation.getClientProperty(LIVE_EDIT_STATUS)
          val previousStatus = myPresentation.getClientProperty(LIVE_EDIT_STATUS_PREVIOUS)

          // We have to store the current status because tooltips can't compare due to us setting the ActionLink (it has no equals override).
          myPresentation.putClientProperty(LIVE_EDIT_STATUS_PREVIOUS, status)

          if (status == null) {
            toolTipText = myPresentation.description
            return
          }

          var tooltip = HelpTooltip.getTooltipFor(this)
          if (tooltip == null || status != previousStatus) {
            tooltip = HelpTooltip().setTitle(myPresentation.description).setDescription(status.description)
            if (!status.actionId.isNullOrBlank()) {
              val actionId = status.actionId
              val action = ActionManager.getInstance().getAction(actionId)
              val shortcut = KeymapManager.getInstance()?.activeKeymap?.getShortcuts(actionId)?.toList()?.firstOrNull()
              tooltip.setLink("${action.templateText}${if (shortcut != null) " (${KeymapUtil.getShortcutText(shortcut)})" else ""}") {
                myPresentation.getClientProperty(MANUAL_LIVE_EDIT_METHOD)?.invoke()
              }
            }
            HelpTooltip.dispose(this)
            tooltip.installOn(this)
          }
        }
        else {
          super.updateToolTipText()
        }
      }

      override fun updateIcon() {
        val status = myPresentation.getClientProperty(LIVE_EDIT_STATUS)
        if (status == null) {
          toolTipText = myPresentation.description
          return
        }
        myPresentation.icon = status.icon
        super.updateIcon()
      }
    }.also {
      it.foreground = JBColor { instanceEditor?.colorsScheme?.getColor(FOREGROUND) ?: FOREGROUND.defaultColor }
      if (!SystemInfo.isWindows) {
        it.font = FontUIResource(it.font.deriveFont(it.font.style, it.font.size - JBUIScale.scale(2).toFloat()))
      }
    }
    return component
  }

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    if (!LiveEditApplicationConfiguration.getInstance().isLiveEdit) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    update(project, e.presentation, e.dataContext)
  }

  fun update(project: Project, presentation: Presentation, dataContext: DataContext) {
    val editor: Editor? = dataContext.getData(CommonDataKeys.EDITOR)
    val editStatus: LiveEditStatus
    val liveEditService = LiveEditService.getInstance(project)
    if (editor != null) {
      val allDevices = liveEditService.devices()
      val insetDevices = deviceMap[project]?.devices()?.let { HashSet<IDevice>(it) } ?: Collections.emptySet()

      editStatus = allDevices
        .filter { it !in insetDevices }
        .map { liveEditService.editStatus(it) }
        .fold(LiveEditStatus.Disabled, LiveEditStatus::merge)

      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
      if (!project.isInitialized ||
          psiFile == null ||
          !psiFile.virtualFile.isKotlinFileType() ||
          !editor.document.isWritable) {
        presentation.isEnabledAndVisible = false
        return
      }
    }
    else {
      val device = deviceMap[project]?.device(dataContext)
      if (device == null) {
        presentation.isEnabledAndVisible = false
        return
      }
      editStatus = liveEditService.editStatus(device)
    }
    presentation.icon = editStatus.icon
    presentation.isEnabledAndVisible = (editStatus != LiveEditStatus.Disabled)
    presentation.putClientProperty(LIVE_EDIT_STATUS, editStatus)
    presentation.putClientProperty(MANUAL_LIVE_EDIT_METHOD) {
      val actionManager = ActionManager.getInstance()
      actionManager.tryToExecute(actionManager.getAction(LiveEditService.PIGGYBACK_ACTION_ID), null, null, null, true)
      LiveEditAnActionListener.triggerLiveEdit(project)
    }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    (component as ActionButtonWithText).update()
  }

  override fun updateActions(context: DataContext): Boolean {
    removeAll()
    add(DefaultActionGroup(ToggleLiveEditStatusAction()))
    return false
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  /**
   * Action that opens the Live Edit settings page for the user to enable/disable live edit.
   */
  class ToggleLiveEditStatusAction: AnAction() {
    override fun update(e: AnActionEvent) {
      e.presentation.text = if (LiveEditApplicationConfiguration.getInstance().isLiveEdit) {
        AndroidBundle.message("live.edit.action.disable.title")
      } else {
        AndroidBundle.message("live.edit.action.enable.title")
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      ShowSettingsUtil.getInstance().showSettingsDialog(e.project, LiveEditConfigurable::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }
  }
}