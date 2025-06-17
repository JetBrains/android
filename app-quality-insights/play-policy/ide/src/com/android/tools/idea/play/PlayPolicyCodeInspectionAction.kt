/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.play

import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import com.android.tools.idea.lint.common.forceRegisterThirdPartyIssues
import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.BaseAnalysisActionDialog
import com.intellij.codeInspection.actions.CodeInspectionAction
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolsSupplier
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.plus
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JEditorPane

class PlayPolicyCodeInspectionAction : CodeInspectionAction("Inspect Play Policy", "Play Policy") {
  override fun runInspections(project: Project, scope: AnalysisScope) {
    val rootProfile = InspectionProjectProfileManager.getInstance(project).currentProfile

    fun getToolWrappers() =
      rootProfile.tools
        .map { it.tool }
        .filter {
          (it.tool as? AndroidLintInspectionBase)
            ?.groupPath
            ?.contentEquals(arrayOf("Android", "Lint", "Play Policy")) == true
        }

    val toolWrappers =
      getToolWrappers().ifEmpty {
        // If the user has not yet run Lint, the Play Policy checks will not be registered as
        // inspections in the profile. We can force this.
        forceRegisterThirdPartyIssues(project, rootProfile)
        // And then try again.
        getToolWrappers()
      }

    myExternalProfile =
      InspectionProfileImpl(
        "Play Policy",
        InspectionToolsSupplier.Simple(toolWrappers),
        rootProfile,
      )
    super.runInspections(project, scope)
  }

  override fun getAdditionalActionSettings(
    project: Project,
    dialog: BaseAnalysisActionDialog,
  ): JComponent? {
    return panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {
        row {
          icon(AllIcons.General.Information)
            .align(AlignX.LEFT.plus(AlignY.TOP))
            .customize(UnscaledGaps(2, 2, 2, 5))

          text(
              "Play policy insights beta is intended to provide helpful pre-review guidance" +
                " to enable a smoother app submission experience" +
                " It doesn't cover every policy or provide final app review decisions." +
                " Always review the full policy in the" +
                " <a href=http://goo.gle/play-policy-center>Policy Center</a> to ensure compliance."
            )
            .align(Align.FILL)
            .recalculatePreferredHeight()
        }
      }
    }
  }

  /**
   * Recalculates the preferred height with a fixed width from DslLabel.
   *
   * DslLabel applies an internal width limit to the component and the preferred height needs to be
   * updated.
   */
  private fun Cell<JEditorPane>.recalculatePreferredHeight(): Cell<JEditorPane> {
    component.size = Dimension(component.preferredSize.width, Int.MAX_VALUE)
    component.preferredSize = null
    component.size = component.preferredSize
    return this
  }

  override fun getDialogTitle(): String = "Specify Inspection Scope"
}
