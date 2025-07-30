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

import com.android.testutils.VirtualTimeScheduler
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gservices.DevServicesDeprecationData
import com.android.tools.idea.gservices.DevServicesDeprecationDataProvider
import com.android.tools.idea.gservices.DevServicesDeprecationStatus
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DevServiceDeprecationInfo.DeliveryType
import com.google.wireless.android.sdk.stats.DevServiceDeprecationInfo.DeprecationStatus
import com.google.wireless.android.sdk.stats.PlayPolicyInsightsUsageEvent
import com.google.wireless.android.sdk.stats.PlayPolicyInsightsUsageEvent.PlayPolicyInsightsUsageEventType
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.components.DslLabel
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

private fun waitForCondition(condition: () -> Boolean) = waitForCondition(30.seconds, condition)

class PlayPolicyCodeInspectionActionTest {

  @get:Rule val projectRule = ProjectRule()

  private val unsupportedData: DevServicesDeprecationData =
    DevServicesDeprecationData(
      header = "",
      description = "my description",
      moreInfoUrl = "link",
      showUpdateAction = true,
      status = DevServicesDeprecationStatus.UNSUPPORTED,
    )

  private val deprecatedData: DevServicesDeprecationData =
    unsupportedData.copy(status = DevServicesDeprecationStatus.DEPRECATED)

  private lateinit var mockDeprecationService: DevServicesDeprecationDataProvider
  private lateinit var tracker: TestUsageTracker

  fun configureDeprecationService(deprecationProto: DevServicesDeprecationData) {
    mockDeprecationService = mock()
    doAnswer { deprecationProto }
      .whenever(mockDeprecationService)
      .getCurrentDeprecationData(any(), any())
    ApplicationManager.getApplication()
      .replaceService(
        DevServicesDeprecationDataProvider::class.java,
        mockDeprecationService,
        projectRule.disposable,
      )
  }

  @Before
  fun setUp() {
    enableHeadlessDialogs(projectRule.project)
    tracker = TestUsageTracker(VirtualTimeScheduler())
    UsageTracker.setWriterForTest(tracker)
  }

  @Test
  fun testDialogContent() = runBlocking {
    val inspectAction = PlayPolicyCodeInspectionAction()
    val mouseEvent = MouseEvent(JPanel(), MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, true, 0)
    val event =
      TestActionEvent.createTestEvent(
        inspectAction,
        {
          when (it) {
            CommonDataKeys.PROJECT.name -> projectRule.project
            else -> null
          }
        },
        mouseEvent,
      )
    withContext(Dispatchers.EDT) {
      createModalDialogAndInteractWithIt({ inspectAction.actionPerformed(event) }) { dialog ->
        assertThat(dialog.title).isEqualTo("Specify Inspection Scope")
        assertThat(dialog.isOKActionEnabled).isTrue()
      }
    }
  }

  @Test
  fun testServiceUnsupported() = runBlocking {
    configureDeprecationService(unsupportedData)
    val inspectAction = PlayPolicyCodeInspectionAction()
    val mouseEvent = MouseEvent(JPanel(), MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, true, 0)
    val event =
      TestActionEvent.createTestEvent(
        inspectAction,
        {
          when (it) {
            CommonDataKeys.PROJECT.name -> projectRule.project
            else -> null
          }
        },
        mouseEvent,
      )
    withContext(Dispatchers.EDT) {
      createModalDialogAndInteractWithIt({ inspectAction.actionPerformed(event) }) { dialog ->
        assertThat(dialog.title).isEqualTo("Specify Inspection Scope")
        assertThat(dialog.isOKActionEnabled).isFalse()
        findDeprecationEvent {
          val info = it.serviceDeprecationInfo.devServiceDeprecationInfo
          info.deliveryType == DeliveryType.PANEL &&
            info.userNotified &&
            info.deprecationStatus == DeprecationStatus.UNSUPPORTED
        }
        verifyDeprecationInformation(dialog.contentPanel, unsupportedData)
      }
    }
  }

  @Test
  fun testServiceDeprecated() = runBlocking {
    configureDeprecationService(deprecatedData)
    val inspectAction = PlayPolicyCodeInspectionAction()
    val mouseEvent = MouseEvent(JPanel(), MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, true, 0)
    val event =
      TestActionEvent.createTestEvent(
        inspectAction,
        {
          when (it) {
            CommonDataKeys.PROJECT.name -> projectRule.project
            else -> null
          }
        },
        mouseEvent,
      )
    withContext(Dispatchers.EDT) {
      createModalDialogAndInteractWithIt({ inspectAction.actionPerformed(event) }) { dialog ->
        assertThat(dialog.title).isEqualTo("Specify Inspection Scope")
        assertThat(dialog.isOKActionEnabled).isTrue()
        findDeprecationEvent {
          val info = it.serviceDeprecationInfo.devServiceDeprecationInfo
          info.deliveryType == DeliveryType.PANEL &&
            info.userNotified &&
            info.deprecationStatus == DeprecationStatus.DEPRECATED
        }
        verifyDeprecationInformation(dialog.contentPanel, deprecatedData)
      }
    }
  }

  private fun findDeprecationEvent(
    predicate: (PlayPolicyInsightsUsageEvent) -> Boolean
  ): PlayPolicyInsightsUsageEvent {
    var event: PlayPolicyInsightsUsageEvent? = null
    waitForCondition {
      val logEvent =
        tracker.usages.lastOrNull {
          it.studioEvent.playPolicyInsightsUsageEvent.type ==
            PlayPolicyInsightsUsageEventType.SERVICE_DEPRECATION
        }
      event = logEvent?.studioEvent?.playPolicyInsightsUsageEvent
      event != null && predicate(event!!)
    }
    return event!!
  }

  private fun verifyDeprecationInformation(
    panel: JComponent,
    deprecationData: DevServicesDeprecationData,
  ) {
    val description =
      panel.findDescendant<JEditorPane> { it.text?.contains(deprecationData.description) == true }
    assertThat(description).isNotNull()
    val components = description!!.parent.components
    assertThat((components[0] as JLabel).icon)
      .isEqualTo(
        if (deprecationData.isDeprecated()) AllIcons.General.Warning else AllIcons.General.Error
      )
    assertThat(components[1]).isEqualTo(description)

    val updateLabel = components[2] as DslLabel
    assertThat(updateLabel.text).contains("Update Android Studio")
    val event = HyperlinkEvent(components[3], HyperlinkEvent.EventType.ACTIVATED, null)
    val moreInfoLabel = components[3] as DslLabel
    assertThat(moreInfoLabel.text).contains("More Info")
    assertThat(moreInfoLabel.text).contains(deprecationData.moreInfoUrl)
    (moreInfoLabel.action as HyperlinkEventAction).hyperlinkActivated(event)
    findDeprecationEvent { it.serviceDeprecationInfo.devServiceDeprecationInfo.moreInfoClicked }
  }
}
