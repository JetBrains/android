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
package com.android.tools.idea.run.activity.launch

import com.android.tools.idea.run.activity.ActivityLocatorUtils
import com.android.tools.idea.run.editor.DeepLinkChooserDialog
import com.intellij.execution.ExecutionBundle
import com.intellij.ide.util.TreeClassChooser
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.ProjectScope
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.android.util.AndroidTreeClassChooserFactory.createInheritanceClassChooser
import org.jetbrains.android.util.AndroidUtils

class DeepLinkConfigurable(project: Project, context: LaunchOptionConfigurableContext) : LaunchOptionConfigurable<DeepLinkLaunch.State?> {
  private val deepLinkField = ComponentWithBrowseButton(JBTextField(), null).apply {
    addActionListener {
      if (!project.isInitialized) {
        return@addActionListener
      }
      val module = context.getModule()
      if (module == null) {
        Messages.showErrorDialog(project, ExecutionBundle.message("module.not.specified.error.text"), "Deep Link Launcher")
        return@addActionListener
      }
      val dialog = DeepLinkChooserDialog(project, module)
      dialog.title = "Select URL"
      dialog.show()
      val deepLinkSelected = dialog.selectedDeepLink
      if (!deepLinkSelected.isNullOrEmpty()) {
        childComponent.setText(deepLinkSelected)
      }
    }
    childComponent.emptyText.text = "Specify URL declared in the manifest"
  }
  private val activityField = ComponentWithBrowseButton(JBTextField(), null).apply {
    addActionListener {
      if (!project.isInitialized) {
        return@addActionListener
      }
      val facade = JavaPsiFacade.getInstance(project)
      val activityBaseClass = facade.findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, ProjectScope.getAllScope(project))
      if (activityBaseClass == null) {
        Messages.showErrorDialog(project, AndroidBundle.message("cant.find.activity.class.error"), "Specific Activity Launcher")
        return@addActionListener
      }
      val module = context.getModule()
      if (module == null) {
        Messages.showErrorDialog(project, ExecutionBundle.message("module.not.specified.error.text"), "Specific Activity Launcher")
        return@addActionListener
      }
      val initialSelection = facade.findClass(childComponent.text, module.moduleWithDependenciesScope)
      val chooser: TreeClassChooser = createInheritanceClassChooser(
        project, "Select Activity Class", module.moduleWithDependenciesScope, activityBaseClass, initialSelection, null
      )
      chooser.showDialog()
      val selClass = chooser.selected
      if (selClass != null) {
        childComponent.text = ActivityLocatorUtils.getQualifiedActivityName(selClass)
      }
    }
  }
  private val panel = panel {
    row {
      label("URL:")
      cell(deepLinkField).resizableColumn().align(AlignX.FILL)
    }
    row {
      label("Activity (optional):")
      cell(activityField).resizableColumn().align(AlignX.FILL)
    }
  }

  override fun createComponent() = panel

  override fun resetFrom(state: DeepLinkLaunch.State) {
    deepLinkField.childComponent.setText(StringUtil.notNullize(state.DEEP_LINK))
    activityField.childComponent.setText(StringUtil.notNullize(state.ACTIVITY))
  }

  override fun applyTo(state: DeepLinkLaunch.State) {
    state.DEEP_LINK = StringUtil.notNullize(deepLinkField.childComponent.getText())
    state.ACTIVITY = StringUtil.notNullize(activityField.childComponent.getText())
  }
}
