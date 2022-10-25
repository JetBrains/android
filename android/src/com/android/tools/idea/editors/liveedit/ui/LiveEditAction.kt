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
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.editors.literals.EditState
import com.android.tools.idea.editors.literals.EditStatus
import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.sourcecode.isKotlinFileType
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
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Color
import java.awt.Insets
import java.util.Collections
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.plaf.FontUIResource

/**
 * An action that displays the Live Edit status in either the edit window or an embedded emulator window.
 */
class LiveEditAction(private val instanceEditor: Editor? = null) : DropDownAction(
  "Live Edit", "Live Edit status", AllIcons.General.InspectionsOKEmpty), CustomComponentAction {
  companion object {
    val FOREGROUND = ColorKey.createColorKey("ActionButton.iconTextForeground", UIUtil.getContextHelpForeground())
    val LIVE_EDIT_STATUS = Key<EditStatus>("android.liveedit.action.editstatus")
    val deviceMap = HashMap<Project, DeviceGetter>()

    val stateToIcon = hashMapOf<EditState, Icon>(
      EditState.ERROR to ColoredIconGenerator.generateColoredIcon(AllIcons.General.InspectionsError, Color.RED),
      EditState.PAUSED to ColoredIconGenerator.generateColoredIcon(AllIcons.General.InspectionsPause, Color.RED),
      EditState.IN_PROGRESS to AnimatedIcon.Default.INSTANCE,
      EditState.UP_TO_DATE to AllIcons.General.InspectionsOK,
      EditState.OUT_OF_DATE to ColoredIconGenerator.generateColoredIcon(AllIcons.General.InlineRefreshHover, Color(0x62B543)),
      EditState.RECOMPOSE_NEEDED to ColoredIconGenerator.generateColoredIcon(AllIcons.General.InlineRefreshHover, Color.RED),
      EditState.RECOMPOSE_ERROR to AllIcons.General.Warning
      // DISABLED will end up with null icon
    )

    fun registerProject(project: Project, deviceGetter: DeviceGetter) {
      deviceMap[project] = deviceGetter
    }

    fun unregisterProject(project: Project) {
      deviceMap.remove(project)
    }

    @VisibleForTesting
    fun getIconForState(state: EditState): Icon? {
      return stateToIcon[state]
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
      override fun getInsets(): Insets = JBUI.insets(2)
      override fun getMargins(): Insets = JBUI.insetsRight(5)

      override fun updateToolTipText() {
        if (Registry.`is`("ide.helptooltip.enabled")) {
          val status = myPresentation.getClientProperty(LIVE_EDIT_STATUS)
          if (status == null) {
            toolTipText = myPresentation.description
            return
          }
          HelpTooltip.dispose(this)
          val tooltip = HelpTooltip().setTitle(myPresentation.description).setDescription(status.message)

          if (!status.actionId.isNullOrBlank()) {
            val actionId = status.actionId
            val action = ActionManager.getInstance().getAction(actionId)
            val shortcut = KeymapManager.getInstance()?.activeKeymap?.getShortcuts(actionId)?.toList()?.firstOrNull()
            tooltip.setLink("${action.templateText}${if (shortcut != null) " (${KeymapUtil.getShortcutText(shortcut)})" else ""}") {
              ActionManager.getInstance().tryToExecute(action, null, null, null, true)
            }
          }

          tooltip.installOn(this)
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
        myPresentation.icon = stateToIcon[status.editState]
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
    val editStatus: EditStatus
    if (editor != null) {
      val statuses = LiveEditService.getInstance(project).editStatus()
      val insetDevices = deviceMap[project]?.devices()?.let { HashSet<IDevice>(it) } ?: Collections.emptySet()
      statuses.keys.removeIf { insetDevices.contains(it) }
      editStatus = LiveEditService.getInstance(project).mergeStatuses(statuses)

      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
      if (!project.isInitialized ||
          psiFile == null ||
          statuses.isEmpty() ||
          !psiFile.virtualFile.isKotlinFileType() ||
          !editor.document.isWritable ||
          editStatus.editState == EditState.DISABLED) {
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
      editStatus = LiveEditService.getInstance(project).editStatus(device)
    }
    presentation.icon = stateToIcon[editStatus.editState]
    presentation.isEnabledAndVisible = (editStatus.editState != EditState.DISABLED)
    presentation.putClientProperty(LIVE_EDIT_STATUS, editStatus)
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    (component as ActionButtonWithText).updateIcon()
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