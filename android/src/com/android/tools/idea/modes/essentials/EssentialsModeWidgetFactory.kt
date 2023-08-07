/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.modes.essentials

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import icons.StudioIcons
import javax.swing.Icon

class EssentialsModeWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = "Essentials Mode Widget"
  override fun isAvailable(project: Project): Boolean = EssentialsMode.isEnabled()
  override fun getDisplayName(): String = "Essentials Mode Status Indicator"
  override fun createWidget(project: Project): StatusBarWidget = EssentialsModeWidget()

  internal class EssentialsModeWidget : StatusBarWidget.MultipleTextValuesPresentation, StatusBarWidget.Multiframe {
    override fun getIcon(): Icon = StudioIcons.Shell.StatusBar.ESSENTIALS_MODE
    override fun getSelectedValue(): String = "Essentials Mode"
    override fun getTooltipText(): String = "Essentials Mode: Enabled"

    override fun getPopup(): JBPopup {
      return JBPopupFactory.getInstance().createActionGroupPopup("Essentials Mode",
                                                                 DefaultActionGroup(EssentialsModeSettingsPageAction()),
                                                                 DataContext.EMPTY_CONTEXT,
                                                                 JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                                                 false)
    }

    override fun ID(): String = "Essentials Mode Widget"
    override fun copy(): StatusBarWidget = EssentialsModeWidget()
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
  }

  internal class EssentialsModeSettingsPageAction : AnAction("Settings Page") {
    override fun actionPerformed(e: AnActionEvent) {
      ShowSettingsUtilImpl.showSettingsDialog(e.project, "essentials.mode.settings", "")
    }
  }
}
