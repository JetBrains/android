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
package com.android.tools.idea.wearwhs.view

import com.android.mockito.kotlin.whenever
import com.android.testutils.ImageDiffUtil
import com.android.test.testutils.TestUtils
import com.android.testutils.waitForCondition
import com.android.tools.adtui.stdui.menu.CommonDropDownButton
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wearwhs.EVENT_TRIGGER_GROUPS
import com.android.tools.idea.wearwhs.WHS_CAPABILITIES
import com.android.tools.idea.wearwhs.WearWhsBundle.message
import com.android.tools.idea.wearwhs.WhsDataType
import com.android.tools.idea.wearwhs.communication.FakeDeviceManager
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.ActionLink
import icons.StudioIcons
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mockStatic

@RunsInEdt
class WearHealthServicesPanelTest {
  companion object {
    const val TEST_MAX_WAIT_TIME_SECONDS = 5L
    const val TEST_POLLING_INTERVAL_MILLISECONDS = 100L
    val TEST_STATE_STALENESS_THRESHOLD = 2.seconds
  }

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val edtRule = EdtRule()

  private val testDataPath: Path
    get() = TestUtils.resolveWorkspacePathUnchecked("tools/adt/idea/wear-whs/testData")

  private val notifications
    get() =
      NotificationsManager.getNotificationsManager()
        .getNotificationsOfType(Notification::class.java, projectRule.project)
        .toList()

  private lateinit var deviceManager: FakeDeviceManager
  private lateinit var stateManager: WearHealthServicesStateManagerImpl
  private lateinit var whsPanel: WearHealthServicesPanel

  @Before
  fun setUp() {
    val testUiScope =
      AndroidCoroutineScope(projectRule.testRootDisposable, AndroidDispatchers.uiThread)
    val testWorkerScope =
      AndroidCoroutineScope(projectRule.testRootDisposable, AndroidDispatchers.workerThread)
    deviceManager = FakeDeviceManager()

    stateManager =
      WearHealthServicesStateManagerImpl(
          deviceManager = deviceManager,
          pollingIntervalMillis = TEST_POLLING_INTERVAL_MILLISECONDS,
          workerScope = testWorkerScope,
          stateStalenessThreshold = TEST_STATE_STALENESS_THRESHOLD,
        )
        .also { Disposer.register(projectRule.testRootDisposable, it) }
        .also { it.serialNumber = "some serial number" }
    whsPanel = createWearHealthServicesPanel(stateManager, testUiScope, testWorkerScope)
  }

  @Test
  fun `test panel screenshot matches expectation for current platform`() = runBlocking {
    val fakeUi = FakeUi(whsPanel.component)

    deviceManager.setCapabilities(
      mapOf(
        WhsDataType.HEART_RATE_BPM to true,
        WhsDataType.LOCATION to true,
        WhsDataType.STEPS to true,
      )
    )

    fakeUi.waitForCheckbox("Heart rate", true)
    fakeUi.waitForCheckbox("Location", true)
    fakeUi.waitForCheckbox("Steps", true)

    fakeUi.root.size = Dimension(400, 400)
    fakeUi.layoutAndDispatchEvents()

    ImageDiffUtil.assertImageSimilarPerPlatform(
      testDataPath = testDataPath,
      fileNameBase = "screens/whs-panel-default",
      actual = fakeUi.render(),
      maxPercentDifferent = 4.0,
    )
  }

  @Test
  fun `test panel screenshot matches expectation with modified state manager values`() =
    runBlocking {
      deviceManager.activeExercise = true

      stateManager.forceUpdateState()

      stateManager.setCapabilityEnabled(deviceManager.capabilities[0], true)
      stateManager.setCapabilityEnabled(deviceManager.capabilities[1], false)
      stateManager.setCapabilityEnabled(deviceManager.capabilities[2], false)
      stateManager.setOverrideValue(deviceManager.capabilities[0], 2f)
      stateManager.setOverrideValue(deviceManager.capabilities[2], 5f)
      stateManager.applyChanges()

      val fakeUi = FakeUi(whsPanel.component)

      fakeUi.waitForCheckbox("Heart rate", true)
      fakeUi.waitForCheckbox("Location", false)
      fakeUi.waitForCheckbox("Steps", false)

      fakeUi.root.size = Dimension(400, 400)
      fakeUi.layoutAndDispatchEvents()

      ImageDiffUtil.assertImageSimilarPerPlatform(
        testDataPath = testDataPath,
        fileNameBase = "screens/whs-panel-state-manager-modified",
        actual = fakeUi.render(),
        maxPercentDifferent = 4.0,
      )
    }

  @Test
  fun `test override value doesn't get reformatted from int to float`() = runBlocking {
    val fakeUi = FakeUi(whsPanel.component)

    deviceManager.activeExercise = true

    val textField = fakeUi.waitForDescendant<JTextField> { it.isVisible }
    textField.text = "50"

    val applyButton = fakeUi.waitForDescendant<JButton> { it.text == "Apply" }
    applyButton.doClick()

    delay(2 * TEST_POLLING_INTERVAL_MILLISECONDS)

    assertThat(textField.text).isEqualTo("50")
  }

  @Test
  fun `test override value allows numbers and decimals and rejects invalid text`() = runBlocking {
    val fakeUi = FakeUi(whsPanel.component)

    deviceManager.activeExercise = true

    val textField = fakeUi.waitForDescendant<JTextField> { it.isVisible }

    // ALLOW: Numbers
    textField.text = "50"
    assertThat(textField.text).isEqualTo("50")

    // ALLOW: Decimal number
    textField.text = "50.0"
    assertThat(textField.text).isEqualTo("50.0")

    // ALLOW: Decimal number
    textField.text = "50.00123"
    assertThat(textField.text).isEqualTo("50.00123")

    // ALLOW: Decimal with leading zero
    textField.text = "0.5"
    assertThat(textField.text).isEqualTo("0.5")

    // ALLOW: Decimal without leading zero
    textField.text = ".5"
    assertThat(textField.text).isEqualTo(".5")

    // ALLOW: One leading zero
    textField.text = "01"
    assertThat(textField.text).isEqualTo("01")

    // ALLOW: Dot after number
    textField.text = "50."
    assertThat(textField.text).isEqualTo("50.")

    // ALLOW: Empty
    textField.text = ""
    assertThat(textField.text).isEmpty()

    // DISALLOW: Number with letters
    textField.text = "50f"
    assertThat(textField.text).isEmpty()

    // DISALLOW: Decimal number with letters
    textField.text = "50.0a"
    assertThat(textField.text).isEmpty()

    // DISALLOW: Letters
    textField.text = "test"
    assertThat(textField.text).isEmpty()

    // DISALLOW: >50 characters
    textField.text = "012345678901234567890123456789012345678901234567890123456789"
    assertThat(textField.text).isEmpty()

    // DISALLOW: Too many leading zeros
    textField.text = "005"
    assertThat(textField.text).isEmpty()
  }

  @Test
  fun `test panel displays the dropdown for event triggers`() = runBlocking {
    val fakeUi = FakeUi(whsPanel.component)
    val dropDownButton = fakeUi.waitForDescendant<CommonDropDownButton>()
    assertThat(dropDownButton).isNotNull()
    assertThat(dropDownButton.action.childrenActions).hasSize(EVENT_TRIGGER_GROUPS.size)
    assertThat(dropDownButton.action.childrenActions[0].childrenActions)
      .hasSize(EVENT_TRIGGER_GROUPS[0].eventTriggers.size)
    assertThat(dropDownButton.action.childrenActions[1].childrenActions)
      .hasSize(EVENT_TRIGGER_GROUPS[1].eventTriggers.size)
    assertThat(dropDownButton.action.childrenActions[2].childrenActions)
      .hasSize(EVENT_TRIGGER_GROUPS[2].eventTriggers.size)
  }

  @Test
  fun `test panel disables checkboxes and load preset button during an exercise`() =
    runBlocking<Unit> {
      val fakeUi = FakeUi(whsPanel.component)

      fakeUi.waitForDescendant<JButton> {
        it.text == message("wear.whs.panel.load.preset") && it.isEnabled
      }
      fakeUi.waitForDescendant<JCheckBox> {
        it.hasLabel(message("wear.whs.capability.heart.rate.label")) && it.isEnabled
      }
      fakeUi.waitForDescendant<JCheckBox> {
        it.hasLabel(message("wear.whs.capability.steps.label")) && it.isEnabled
      }

      deviceManager.activeExercise = true

      fakeUi.waitForDescendant<JButton> {
        it.text == message("wear.whs.panel.load.preset") && !it.isEnabled
      }
      fakeUi.waitForDescendant<JCheckBox> {
        it.hasLabel(message("wear.whs.capability.heart.rate.label")) && !it.isEnabled
      }
      fakeUi.waitForDescendant<JCheckBox> {
        it.hasLabel(message("wear.whs.capability.steps.label")) && !it.isEnabled
      }
    }

  @Test
  fun `test star is only visible when changes are pending`(): Unit = runBlocking {
    val fakeUi = FakeUi(whsPanel.component)

    val hrCheckBox = fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate") }
    hrCheckBox.doClick()

    fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate*") }

    val applyButton = fakeUi.waitForDescendant<JButton> { it.text == "Apply" }
    applyButton.doClick()

    fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate") }

    deviceManager.activeExercise = true
    stateManager.ongoingExercise.waitForValue(true)
    val textField = fakeUi.waitForDescendant<JTextField> { it.isVisible }
    textField.text = "50"

    fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate*") }

    applyButton.doClick()

    fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate") }
  }

  @Test
  fun `test enabled sensors have enabled override value fields and units during exercise`() =
    runBlocking<Unit> {
      val fakeUi = FakeUi(whsPanel.component)

      deviceManager.activeExercise = true
      stateManager.ongoingExercise.waitForValue(true)

      // Heart Rate
      stateManager.setCapabilityEnabled(WHS_CAPABILITIES[0], true)
      stateManager.setOverrideValue(WHS_CAPABILITIES[0], 50f)
      stateManager.applyChanges()

      fakeUi.waitForCheckbox("Heart rate", true)
      fakeUi.waitForDescendant<JTextField> { it.text == "50" && it.isVisible && it.isEnabled }
      fakeUi.waitForDescendant<JLabel> { it.text == "bpm" && it.isEnabled }
    }

  @Test
  fun `test disabled sensors have disabled override value fields and units during exercise`() =
    runBlocking<Unit> {
      val fakeUi = FakeUi(whsPanel.component)

      deviceManager.activeExercise = true
      stateManager.ongoingExercise.waitForValue(true)

      // Heart Rate
      stateManager.setCapabilityEnabled(WHS_CAPABILITIES[0], false)
      stateManager.setOverrideValue(WHS_CAPABILITIES[0], 50f)
      stateManager.applyChanges()

      fakeUi.waitForCheckbox("Heart rate", false)
      fakeUi.waitForDescendant<JTextField> { it.text == "50" && it.isVisible && !it.isEnabled }
      fakeUi.waitForDescendant<JLabel> { it.text == "bpm" && !it.isEnabled }
    }

  @Test
  fun `test apply button flow notification`(): Unit = runBlocking {
    val fakeUi = FakeUi(whsPanel.component)
    val userApplyChanges = whsPanel.onUserApplyChangesFlow

    deviceManager.activeExercise = true

    val textField = fakeUi.waitForDescendant<JTextField> { it.isVisible }
    textField.text = "50"

    val applyButton = fakeUi.waitForDescendant<JButton> { it.text == "Apply" }
    applyButton.doClick()

    userApplyChanges.take(1).collect {}
  }

  @Test
  fun `test successful apply changes shows in information label when panel is showing`(): Unit =
    runBlocking {
      val fakeUi = FakeUi(whsPanel.component, createFakeWindow = true)

      val applyButton = fakeUi.waitForDescendant<JButton> { it.text == "Apply" }
      applyButton.doClick()

      whsPanel.onUserApplyChangesFlow.take(1).collectLatest {}

      fakeUi.waitForDescendant<JLabel> { it.text == message("wear.whs.panel.apply.success") }
    }

  @Test
  fun `test failed apply changes shows in information label when panel is showing`(): Unit =
    runBlocking {
      val fakeUi = FakeUi(whsPanel.component, createFakeWindow = true)

      deviceManager.failState = true

      val applyButton = fakeUi.waitForDescendant<JButton> { it.text == "Apply" }
      applyButton.doClick()

      whsPanel.onUserApplyChangesFlow.take(1).collectLatest {}

      fakeUi.waitForDescendant<JLabel> { it.text == message("wear.whs.panel.apply.failure") }
    }

  @Test
  fun `test user is notified of successful apply changes when panel is not showing`(): Unit =
    runBlocking {
      val fakeUi = FakeUi(whsPanel.component, createFakeWindow = false)

      val applyButton = fakeUi.waitForDescendant<JButton> { it.text == "Apply" }
      applyButton.doClick()

      whsPanel.onUserApplyChangesFlow.take(1).collectLatest {}

      waitForCondition(2, TimeUnit.SECONDS) {
        notifications.any {
          it.content == message("wear.whs.panel.apply.success") &&
            it.type == NotificationType.INFORMATION
        }
      }
    }

  @Test
  fun `test user is notified of failed apply changes when panel is not showing`(): Unit =
    runBlocking {
      val fakeUi = FakeUi(whsPanel.component, createFakeWindow = false)

      deviceManager.failState = true

      val applyButton = fakeUi.waitForDescendant<JButton> { it.text == "Apply" }
      applyButton.doClick()

      whsPanel.onUserApplyChangesFlow.take(1).collectLatest {}

      waitForCondition(2, TimeUnit.SECONDS) {
        notifications.any {
          it.content == message("wear.whs.panel.apply.failure") && it.type == NotificationType.ERROR
        }
      }
    }

  @Test
  fun `test successful reset shows in information label when panel is showing`(): Unit =
    runBlocking {
      val fakeUi = FakeUi(whsPanel.component, createFakeWindow = true)

      val resetButton = fakeUi.waitForDescendant<JButton> { it.text == "Reset" }
      resetButton.doClick()

      fakeUi.waitForDescendant<JLabel> { it.text == message("wear.whs.panel.reset.success") }
    }

  @Test
  fun `test failed reset shows in information label when panel is showing`(): Unit = runBlocking {
    val fakeUi = FakeUi(whsPanel.component, createFakeWindow = true)

    deviceManager.failState = true

    val resetButton = fakeUi.waitForDescendant<JButton> { it.text == "Reset" }
    resetButton.doClick()

    fakeUi.waitForDescendant<JLabel> { it.text == message("wear.whs.panel.reset.failure") }
  }

  @Test
  fun `test user is notified of successful reset when panel is not showing`(): Unit = runBlocking {
    val fakeUi = FakeUi(whsPanel.component, createFakeWindow = false)

    val resetButton = fakeUi.waitForDescendant<JButton> { it.text == "Reset" }
    resetButton.doClick()

    waitForCondition(2, TimeUnit.SECONDS) {
      notifications.any {
        it.content == message("wear.whs.panel.reset.success") &&
          it.type == NotificationType.INFORMATION
      }
    }
  }

  @Test
  fun `test user is notified of failed reset when panel is not showing`(): Unit = runBlocking {
    val fakeUi = FakeUi(whsPanel.component, createFakeWindow = false)

    deviceManager.failState = true

    val resetButton = fakeUi.waitForDescendant<JButton> { it.text == "Reset" }
    resetButton.doClick()

    waitForCondition(2, TimeUnit.SECONDS) {
      notifications.any {
        it.content == message("wear.whs.panel.reset.failure") && it.type == NotificationType.ERROR
      }
    }
  }

  @Test
  fun `test successful trigger event shows in information label when panel is showing`(): Unit =
    runBlocking {
      val fakeUi = FakeUi(whsPanel.component, createFakeWindow = true)

      val dropDownButton = fakeUi.waitForDescendant<CommonDropDownButton>()
      val triggerEventAction = dropDownButton.action.childrenActions.first().childrenActions.first()
      triggerEventAction.actionPerformed(ActionEvent("source", 1, ""))
      whsPanel.onUserTriggerEventFlow.take(1).collectLatest {}

      fakeUi.waitForDescendant<JLabel> { it.text == message("wear.whs.event.trigger.success") }
    }

  @Test
  fun `test failed trigger event shows in information label when panel is showing`(): Unit =
    runBlocking {
      val fakeUi = FakeUi(whsPanel.component, createFakeWindow = true)

      deviceManager.failState = true

      val dropDownButton = fakeUi.waitForDescendant<CommonDropDownButton>()
      val triggerEventAction = dropDownButton.action.childrenActions.first().childrenActions.first()
      triggerEventAction.actionPerformed(ActionEvent("source", 1, ""))
      whsPanel.onUserTriggerEventFlow.take(1).collectLatest {}

      fakeUi.waitForDescendant<JLabel> { it.text == message("wear.whs.event.trigger.failure") }
    }

  @Test
  fun `test user is notified of successful trigger event when panel is not showing`(): Unit =
    runBlocking {
      val fakeUi = FakeUi(whsPanel.component, createFakeWindow = false)

      val dropDownButton = fakeUi.waitForDescendant<CommonDropDownButton>()
      val triggerEventAction = dropDownButton.action.childrenActions.first().childrenActions.first()
      triggerEventAction.actionPerformed(ActionEvent("source", 1, ""))
      whsPanel.onUserTriggerEventFlow.take(1).collectLatest {}

      waitForCondition(2, TimeUnit.SECONDS) {
        notifications.any {
          it.content == message("wear.whs.event.trigger.success") &&
            it.type == NotificationType.INFORMATION
        }
      }
    }

  @Test
  fun `test user is notified of failed trigger event when panel is not showing`(): Unit =
    runBlocking {
      val fakeUi = FakeUi(whsPanel.component, createFakeWindow = false)

      deviceManager.failState = true

      val dropDownButton = fakeUi.waitForDescendant<CommonDropDownButton>()
      val triggerEventAction = dropDownButton.action.childrenActions.first().childrenActions.first()
      triggerEventAction.actionPerformed(ActionEvent("source", 1, ""))
      whsPanel.onUserTriggerEventFlow.take(1).collectLatest {}

      waitForCondition(2, TimeUnit.SECONDS) {
        notifications.any {
          it.content == message("wear.whs.event.trigger.failure") &&
            it.type == NotificationType.ERROR
        }
      }
    }

  @Test
  fun `stale data is shown as a warning icon`(): Unit = runBlocking {
    val fakeUi = FakeUi(whsPanel.component, createFakeWindow = false)

    val label =
      fakeUi.waitForDescendant<JLabel> {
        it.icon == StudioIcons.Common.INFO && it.text == message("wear.whs.panel.exercise.inactive")
      }

    // once the syncs start failing, then the state will eventually become stale
    deviceManager.failState = true
    waitForCondition(5.seconds) {
      label.icon == StudioIcons.Common.WARNING &&
        label.toolTipText == message("wear.whs.panel.stale.data")
    }

    // when the sync succeeds again, then the state will no longer be warned as stale
    deviceManager.failState = false
    waitForCondition(5.seconds) {
      label.icon == StudioIcons.Common.INFO &&
        label.toolTipText != message("wear.whs.panel.stale.data")
    }
  }

  @Test
  fun `the apply button has tooltip texts`(): Unit = runBlocking {
    val fakeUi = FakeUi(whsPanel.component)

    deviceManager.activeExercise = false
    val applyButton = fakeUi.waitForDescendant<JButton> { it.text == "Apply" }
    assertThat(applyButton.toolTipText)
      .isEqualTo(message("wear.whs.panel.apply.tooltip.no.exercise"))

    deviceManager.activeExercise = true
    waitForCondition(5.seconds) {
      applyButton.toolTipText == message("wear.whs.panel.apply.tooltip.during.exercise")
    }
  }

  @Test
  fun `while ongoing exercise location has note explaining that it cannot be overridden`(): Unit =
    runBlocking {
      val fakeUi = FakeUi(whsPanel.component)
      deviceManager.activeExercise = true
      stateManager.forceUpdateState()

      val locationLabel =
        fakeUi.waitForDescendant<JLabel> {
          it.text == message("wear.whs.capability.location.label")
        }

      waitForCondition(5.seconds) {
        val siblingComponents = locationLabel.parent.components
        siblingComponents
          .filterIsInstance<JPanel>()
          .single()
          .components
          .filterIsInstance<JLabel>()
          .any {
            it.icon == AllIcons.General.Note &&
              it.toolTipText == message("wear.whs.capability.override.not.supported") &&
              it.isVisible
          }
      }
    }

  @Test
  fun `test trigger event flow notification`(): Unit = runBlocking {
    val fakeUi = FakeUi(whsPanel.component)
    val triggerEventFlow = whsPanel.onUserTriggerEventFlow

    val dropDownButton = fakeUi.waitForDescendant<CommonDropDownButton>()
    val triggerEventAction = dropDownButton.action.childrenActions.first().childrenActions.first()
    triggerEventAction.actionPerformed(ActionEvent("source", 1, ""))

    withTimeout(1.seconds) { triggerEventFlow.take(1).collect {} }
  }

  @Test
  fun `has learn more link`(): Unit = runBlocking {
    val fakeUi = FakeUi(whsPanel.component)

    val learnMoreLink =
      fakeUi.waitForDescendant<ActionLink> { it.text == message("wear.whs.panel.learn.more") }
    mockStatic(BrowserUtil::class.java).use { browserUtil ->
      val url = CompletableDeferred<String>()
      browserUtil
        .whenever<String> { BrowserUtil.browse(anyString()) }
        .thenAnswer {
          url.complete(it.getArgument(0) as String)
          Unit
        }

      learnMoreLink.doClick()

      assertEquals(LEARN_MORE_URL, url.await())
    }
  }

  private fun FakeUi.waitForCheckbox(text: String, selected: Boolean) =
    waitForDescendant<JCheckBox> { checkbox ->
      checkbox.hasLabel(text) && checkbox.isSelected == selected
    }

  private fun JCheckBox.hasLabel(text: String) =
    parent.findDescendant<JLabel> { it.text.contains(text) } != null

  // The UI loads on asynchronous coroutine, we need to wait
  private inline fun <reified T> FakeUi.waitForDescendant(
    crossinline predicate: (T) -> Boolean = { true }
  ): T {
    waitForCondition(TEST_MAX_WAIT_TIME_SECONDS, TimeUnit.SECONDS) {
      root.findDescendant(predicate) != null
    }
    return root.findDescendant(predicate)!!
  }
}
