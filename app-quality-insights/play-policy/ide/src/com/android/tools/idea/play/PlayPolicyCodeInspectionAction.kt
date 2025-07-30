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

import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gservices.DevServicesDeprecationDataProvider
import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import com.android.tools.idea.lint.common.forceRegisterThirdPartyIssues
import com.google.wireless.android.sdk.stats.PlayPolicyInsightsUsageEvent.PlayPolicyInsightsUsageEventType
import com.google.wireless.android.sdk.stats.PlayPolicyInsightsUsageEventKt.batchInspectionDetails
import com.google.wireless.android.sdk.stats.playPolicyInsightsUsageEvent
import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.BaseAnalysisActionDialog
import com.intellij.codeInspection.actions.CodeInspectionAction
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolsSupplier
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.plus
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.jetbrains.rd.util.LogLevel
import com.jetbrains.rd.util.getLogger
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JEditorPane
import kotlinx.coroutines.launch

class PlayPolicyCodeInspectionAction : CodeInspectionAction("Inspect Play Policy", "Play Policy") {

  private val logger
    get() = getLogger<PlayPolicyCodeInspectionAction>()

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = StudioFlags.ENABLE_PLAY_POLICY_INSIGHTS.get()
  }

  override fun runInspections(project: Project, scope: AnalysisScope) {
    val rootProfile = InspectionProjectProfileManager.getInstance(project).currentProfile

    fun getTools() =
      rootProfile.tools.filter {
        (it.tool.tool as? AndroidLintInspectionBase)
          ?.groupPath
          ?.contentEquals(arrayOf("Android", "Lint", "Play Policy")) == true
      }

    val tools =
      getTools().ifEmpty {
        // If the user has not yet run Lint, the Play Policy checks will not be registered as
        // inspections in the profile. We can force this.
        forceRegisterThirdPartyIssues(project, rootProfile)
        // And then try again.
        getTools()
      }

    myExternalProfile =
      InspectionProfileImpl(
        "Play Policy",
        InspectionToolsSupplier.Simple(tools.map { it.tool }),
        rootProfile,
      )
    if (tools.isNotEmpty()) {
      logger.log(LogLevel.Info, "${tools.size} rules are loaded for Play Policy Insights.", null)
    } else {
      logger.log(LogLevel.Warn, "Failed to load rules for Play Policy Insights.", null)
    }

    playPolicyInsightsUsageEvent {
        type = PlayPolicyInsightsUsageEventType.BATCH_INSPECTION
        batchInspectionDetails = batchInspectionDetails {
          enabledIssueCount = tools.count { it.isEnabled }
          totalIssueCount = tools.size
        }
      }
      .track()

    super.runInspections(project, scope)
  }

  override fun getAdditionalActionSettings(
    project: Project,
    dialog: BaseAnalysisActionDialog,
  ): JComponent {
    val deprecationData =
      service<DevServicesDeprecationDataProvider>()
        .getCurrentDeprecationData("aqi/policy", "Play Policy Insights")
    if (deprecationData.isUnsupported()) {
      dialog.isOKActionEnabled = false
    }

    return panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {
        if (!deprecationData.isSupported() && deprecationData.description.isNotEmpty()) {
          dialog.disposable.createCoroutineScope().launch {
            trackDeprecation(deprecationData.status, userNotified = true)
          }
          row {
              icon(
                  if (deprecationData.isDeprecated()) AllIcons.General.Warning
                  else AllIcons.General.Error
                )
                .align(AlignX.LEFT.plus(AlignY.TOP))
                .customize(UnscaledGaps(2, 2, 2, 5))
              panel {
                row {
                  text(deprecationData.description).align(Align.FILL).recalculatePreferredHeight()
                }
                row {
                  if (deprecationData.showUpdateAction) {
                    text(
                        "<a>Update Android Studio</a>",
                        action =
                          HyperlinkEventAction {
                            trackDeprecation(deprecationData.status, userClickedUpdate = true)
                            UpdateChecker.updateAndShowResult(project, UpdateSettings())
                          },
                      )
                      .align(AlignX.LEFT + AlignY.FILL)
                      .customize(UnscaledGaps(0, 0, 0, right = 50))
                  }
                  val moreInfoUrl = deprecationData.moreInfoUrl
                  if (moreInfoUrl.isNotBlank()) {
                    val hyperlinkEventAction = HyperlinkEventAction { e ->
                      trackDeprecation(deprecationData.status, userClickedMoreInfo = true)
                      e.url?.let { BrowserUtil.browse(it) }
                    }

                    text("<a href=${moreInfoUrl}>More Info</a>", action = hyperlinkEventAction)
                      .align(AlignX.LEFT + AlignY.FILL)
                  }
                }
              }
            }
            .customize(UnscaledGapsY(0, bottom = 17))
        }

        row {
          icon(AllIcons.General.Information)
            .align(AlignX.LEFT.plus(AlignY.TOP))
            .customize(UnscaledGaps(2, 2, 2, 5))

          text(
              "Play Policy Insights (Beta) is intended to provide helpful pre-review guidance" +
                " to enable a smoother app submission experience." +
                " It does not cover every policy or provide final app review decisions." +
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
