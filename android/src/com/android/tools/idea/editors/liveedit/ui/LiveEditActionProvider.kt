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

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.editors.literals.EditState
import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.editors.literals.ui.LiveLiteralsConfigurable
import com.android.tools.idea.flags.StudioFlags
import com.intellij.icons.AllIcons.General.InspectionsError
import com.intellij.icons.AllIcons.General.InspectionsOK
import com.intellij.icons.AllIcons.General.InspectionsOKEmpty
import com.intellij.icons.AllIcons.General.InspectionsPause
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.NotNullProducer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Insets
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.plaf.FontUIResource

class LiveEditActionProvider : InspectionWidgetActionProvider {
  override fun createAction(editor: Editor): AnAction? {
    val project: Project? = editor.project
    return if (project == null ||
               project.isDefault ||
               StudioFlags.COMPOSE_DEPLOY_LIVE_LITERALS.get() ||
               !StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT.get()) null else
      object : DefaultActionGroup(LiveEditAction(editor), Separator.create()) {
        override fun update(e: AnActionEvent) {
          val proj = e.project ?: return
          if (!proj.isInitialized || PsiDocumentManager.getInstance(proj).getPsiFile(editor.document) == null) {
            e.presentation.isEnabledAndVisible = false
            return
          }
          val editStatus = LiveEditService.getInstance(project).editStatus()
          e.presentation.isEnabledAndVisible = (editStatus.editState != EditState.DISABLED)
        }
      }
  }


  private class LiveEditAction(private val editor: Editor) : AnAction("Live Edit", "Live Edit status", InspectionsOKEmpty), CustomComponentAction {
    val stateToIcon = hashMapOf<EditState, Icon>(
      EditState.ERROR to ColoredIconGenerator.generateColoredIcon(InspectionsError, Color.RED),
      EditState.PAUSED to ColoredIconGenerator.generateColoredIcon(InspectionsPause, Color.RED),
      EditState.IN_PROGRESS to AnimatedIcon.Default.INSTANCE,
      EditState.UP_TO_DATE to InspectionsOK,
      // DISABLED will end up with null icon
    )

    override fun actionPerformed(e: AnActionEvent) {
      // TBD
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
      object : ActionButtonWithText(this, presentation, place, JBUI.size(18)) {
        override fun iconTextSpace() = JBUI.scale(2)
        override fun getInsets(): Insets = JBUI.insets(2)
        override fun getMargins(): Insets = JBUI.insetsRight(5)

        override fun updateToolTipText() {
          val project = editor.project
          if (project == null) {
            toolTipText = myPresentation.description
            return
          }
          val editState = LiveEditService.getInstance(project).editStatus()
          HelpTooltip.dispose(this)
          HelpTooltip()
            .setTitle(myPresentation.description)
            .setDescription(editState.message)
            .setLink("Configure live edit")
            { ShowSettingsUtil.getInstance().showSettingsDialog(project, LiveLiteralsConfigurable::class.java) }
            .installOn(this)
        }
      }.also {
        it.foreground = JBColor { editor.colorsScheme.getColor(FOREGROUND) ?: FOREGROUND.defaultColor }
        if (!SystemInfo.isWindows) {
          it.font = FontUIResource(it.font.deriveFont(it.font.style, it.font.size - JBUIScale.scale(2).toFloat()))
        }
      }

    override fun update(e: AnActionEvent) {
      val project = e.project ?: return
      val editState = LiveEditService.getInstance(project).editStatus()
      e.presentation.icon = stateToIcon[editState.editState]
      e.presentation.isEnabledAndVisible = (editState.editState != EditState.DISABLED)
    }
  }

  companion object {
    val FOREGROUND = ColorKey.createColorKey("ActionButton.iconTextForeground", UIUtil.getContextHelpForeground())
  }
}