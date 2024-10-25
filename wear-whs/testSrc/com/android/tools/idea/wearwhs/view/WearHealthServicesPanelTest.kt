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
import com.android.testutils.TestUtils
import com.android.testutils.retryUntilPassing
import com.android.testutils.waitForCondition
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.mapState
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.ui.FakeActionPopupMenu
import com.android.tools.idea.wearwhs.EVENT_TRIGGER_GROUPS
import com.android.tools.idea.wearwhs.EventTrigger
import com.android.tools.idea.wearwhs.WHS_CAPABILITIES
import com.android.tools.idea.wearwhs.WearWhsBundle.message
import com.android.tools.idea.wearwhs.WhsDataType
import com.android.tools.idea.wearwhs.communication.FakeDeviceManager
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.components.ActionLink
import icons.StudioIcons
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.spy
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

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

  private lateinit var deviceManager: FakeDeviceManager
  private lateinit var stateManager: WearHealthServicesStateManagerImpl
  private lateinit var fakePopup: FakeActionPopupMenu
  private lateinit var uiScope: CoroutineScope
  private lateinit var workerScope: CoroutineScope
  private val informationLabelFlow = MutableStateFlow("")

  @Before
  fun setUp() {
    uiScope = AndroidCoroutineScope(projectRule.testRootDisposable, AndroidDispatchers.uiThread)
    workerScope =
      AndroidCoroutineScope(projectRule.testRootDisposable, AndroidDispatchers.workerThread)
    deviceManager = FakeDeviceManager()

    stateManager =
      WearHealthServicesStateManagerImpl(
          deviceManager = deviceManager,
          pollingIntervalMillis = TEST_POLLING_INTERVAL_MILLISECONDS,
          workerScope = workerScope,
          stateStalenessThreshold = TEST_STATE_STALENESS_THRESHOLD,
        )
        .also { Disposer.register(projectRule.testRootDisposable, it) }
        .also { it.serialNumber = "some serial number" }

    val actionManager = spy(ActionManager.getInstance() as ActionManagerEx)
    doAnswer { invocation ->
        fakePopup = FakeActionPopupMenu(invocation.getArgument(1))
        fakePopup
      }
      .whenever(actionManager)
      .createActionPopupMenu(anyString(), any())
    ApplicationManager.getApplication()
      .replaceService(ActionManager::class.java, actionManager, projectRule.testRootDisposable)
  }

  @Test
  fun `test panel screenshot matches expectation for current platform`() = runBlocking {
    val fakeUi = FakeUi(createWhsPanel().component)

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
      maxPercentDifferent = 0.0,
    )
  }

  @Test
  fun `test panel screenshot matches expectation with modified state manager values`() =
    runBlocking {
      stateManager.setCapabilityEnabled(deviceManager.capabilities[0], true)
      stateManager.setCapabilityEnabled(deviceManager.capabilities[1], false)
      stateManager.setCapabilityEnabled(deviceManager.capabilities[2], false)
      stateManager.applyChanges()

      deviceManager.activeExercise = true

      stateManager.forceUpdateState()

      stateManager.setOverrideValue(deviceManager.capabilities[0], 2f)
      stateManager.setOverrideValue(deviceManager.capabilities[2], 5f)
      stateManager.applyChanges()

      val fakeUi = FakeUi(createWhsPanel().component)

      fakeUi.waitForCheckbox("Heart rate", true)
      fakeUi.waitForCheckbox("Location", false)
      fakeUi.waitForCheckbox("Steps", false)

      fakeUi.root.size = Dimension(400, 400)
      fakeUi.layoutAndDispatchEvents()

      ImageDiffUtil.assertImageSimilarPerPlatform(
        testDataPath = testDataPath,
        fileNameBase = "screens/whs-panel-state-manager-modified",
        actual = fakeUi.render(),
        maxPercentDifferent = 0.0,
      )
    }

  @Test
  fun `test override value doesn't get reformatted from int to float`() = runBlocking {
    val fakeUi = FakeUi(createWhsPanel().component)

    deviceManager.activeExercise = true

    val textField = fakeUi.waitForDescendant<JTextField> { it.isVisible }
    textField.text = "50"

    fakeUi.clickOnApplyButton()

    delay(2 * TEST_POLLING_INTERVAL_MILLISECONDS)

    assertThat(textField.text).isEqualTo("50")
  }

  @Test
  fun `test override value allows numbers and decimals and rejects invalid text`() = runBlocking {
    val fakeUi = FakeUi(createWhsPanel().component)

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
  fun `test panel displays a popup for event triggers`(): Unit = runBlocking {
    val fakeUi = FakeUi(createWhsPanel().component)
    val eventsButton = fakeUi.triggerEventsButton()
    assertThat(eventsButton).isNotNull()

    eventsButton.click()

    val eventTriggerGroups = fakePopup.getActions().mapNotNull { it as? DropDownAction }
    assertThat(eventTriggerGroups).hasSize(EVENT_TRIGGER_GROUPS.size)

    for (i in EVENT_TRIGGER_GROUPS.indices) {
      assertThat(eventTriggerGroups[i].templatePresentation.text)
        .isEqualTo(EVENT_TRIGGER_GROUPS[i].eventGroupLabel)
      val eventTriggerActions = eventTriggerGroups[i].childActionsOrStubs

      assertThat(eventTriggerActions.map { it.templatePresentation.text })
        .isEqualTo(EVENT_TRIGGER_GROUPS[i].eventTriggers.map { it.eventLabel })
    }
  }

  @Test
  fun `test panel disables checkboxes and load preset combobox during an exercise`() =
    runBlocking<Unit> {
      val fakeUi = FakeUi(createWhsPanel().component)

      fakeUi.waitForDescendant<ComboBox<Preset>> { it.isEnabled }
      fakeUi.waitForDescendant<JCheckBox> {
        it.hasLabel(message("wear.whs.capability.heart.rate.label")) && it.isEnabled
      }
      fakeUi.waitForDescendant<JCheckBox> {
        it.hasLabel(message("wear.whs.capability.steps.label")) && it.isEnabled
      }

      deviceManager.activeExercise = true

      fakeUi.waitForDescendant<ComboBox<Preset>> { !it.isEnabled }
      fakeUi.waitForDescendant<JCheckBox> {
        it.hasLabel(message("wear.whs.capability.heart.rate.label")) && !it.isEnabled
      }
      fakeUi.waitForDescendant<JCheckBox> {
        it.hasLabel(message("wear.whs.capability.steps.label")) && !it.isEnabled
      }
    }

  @Test
  fun `test star is only visible when changes are pending`(): Unit = runBlocking {
    val whsPanel =
      createWhsPanel(applyChanges = { workerScope.launch { stateManager.applyChanges() } })
    val fakeUi = FakeUi(whsPanel.component)

    val hrCheckBox = fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate") }
    hrCheckBox.doClick()

    fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate*") }

    fakeUi.clickOnApplyButton()

    fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate") }

    deviceManager.activeExercise = true
    stateManager.ongoingExercise.waitForValue(true)
    val textField = fakeUi.waitForDescendant<JTextField> { it.isVisible && it.isEnabled }
    textField.text = "50"

    fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Steps*") }

    fakeUi.clickOnApplyButton()

    fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Steps") }
  }

  @Test
  fun `test enabled sensors have enabled override value fields and units during exercise`() =
    runBlocking<Unit> {
      val fakeUi = FakeUi(createWhsPanel().component)

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
      val fakeUi = FakeUi(createWhsPanel().component)

      val heartRateCapability = WHS_CAPABILITIES[0]
      stateManager.setCapabilityEnabled(heartRateCapability, false)
      stateManager.applyChanges()

      deviceManager.activeExercise = true
      stateManager.ongoingExercise.waitForValue(true)

      stateManager.setOverrideValue(heartRateCapability, 50f)
      stateManager.applyChanges()

      fakeUi.waitForCheckbox("Heart rate", false)
      fakeUi.waitForDescendant<JTextField> { it.isVisible && !it.isEnabled }
      fakeUi.waitForDescendant<JLabel> { it.text == "bpm" && !it.isEnabled }
    }

  @Test
  fun `test information label shows in panel`(): Unit = runBlocking {
    val fakeUi = FakeUi(createWhsPanel().component)

    informationLabelFlow.value = "some information label"

    fakeUi.waitForDescendant<JLabel> { it.text == "some information label" }
  }

  @Test
  fun `test reset button`(): Unit = runBlocking {
    val completableDeferred = CompletableDeferred<Unit>()
    val whsPanel = createWhsPanel(reset = { completableDeferred.complete(Unit) })
    val fakeUi = FakeUi(whsPanel.component)

    deviceManager.activeExercise = true

    val resetJButton =
      fakeUi.waitForDescendant<JButton> { it.text == message("wear.whs.panel.reset") }
    resetJButton.doClick()

    completableDeferred.await()
  }

  @Test
  fun `test apply button`(): Unit = runBlocking {
    val completableDeferred = CompletableDeferred<Unit>()
    val whsPanel = createWhsPanel(applyChanges = { completableDeferred.complete(Unit) })
    val fakeUi = FakeUi(whsPanel.component)

    deviceManager.activeExercise = true

    val textField = fakeUi.waitForDescendant<JTextField> { it.isVisible }
    textField.text = "50"

    val applyButton =
      fakeUi.waitForDescendant<JButton> { it.text == message("wear.whs.panel.apply") }
    applyButton.doClick()

    completableDeferred.await()
  }

  @Test
  fun `stale data is shown as a warning icon`(): Unit = runBlocking {
    val fakeUi = FakeUi(createWhsPanel().component, createFakeWindow = false)

    val label =
      fakeUi.waitForDescendant<JLabel> {
        it.icon == AllIcons.Empty && it.text == message("wear.whs.panel.exercise.inactive")
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
      label.icon == AllIcons.Empty && label.toolTipText != message("wear.whs.panel.stale.data")
    }
  }

  @Test
  fun `the apply button has tooltip texts`(): Unit = runBlocking {
    val fakeUi = FakeUi(createWhsPanel().component)

    deviceManager.activeExercise = false
    val applyButton =
      fakeUi.waitForDescendant<JButton> { it.text == message("wear.whs.panel.apply") }
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
      val fakeUi = FakeUi(createWhsPanel().component)
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
  fun `test trigger event button`(): Unit = runBlocking {
    val completableDeferred = CompletableDeferred<EventTrigger>()
    val whsPanel = createWhsPanel(triggerEvent = { completableDeferred.complete(it) })
    val fakeUi = FakeUi(whsPanel.component)

    fakeUi.clickOnTriggerEvent(
      { fakePopup },
      eventName = message("wear.whs.event.trigger.golf.shot.partial"),
    )

    val triggeredEvent = completableDeferred.await()
    assertThat(triggeredEvent.eventLabel)
      .isEqualTo(message("wear.whs.event.trigger.golf.shot.partial"))
  }

  @Test
  fun `has learn more link`(): Unit = runBlocking {
    val fakeUi = FakeUi(createWhsPanel().component)

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

  @Test
  fun `an asterisk only shows if a capability has a different value than what is on the device`():
    Unit = runBlocking {
    val whsPanel =
      createWhsPanel(applyChanges = { workerScope.launch { stateManager.applyChanges() } })
    val fakeUi = FakeUi(whsPanel.component)

    val hrCheckBox = fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate") }
    run {
      hrCheckBox.doClick()
      fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate*") }

      // revert value
      hrCheckBox.doClick()
      fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate") }
    }

    deviceManager.activeExercise = true
    stateManager.ongoingExercise.waitForValue(true)

    val textField = fakeUi.waitForDescendant<JTextField> { it.isVisible }
    run {
      textField.text = "50"
      fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate*") }

      // revert value
      textField.text = ""
      fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate") }
    }

    textField.text = "50"
    fakeUi.clickOnApplyButton()
    fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate") }

    run {
      textField.text = "60"
      fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate*") }

      // revert value
      textField.text = "50"
      fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate") }
    }
  }

  @Test
  fun `override text fields handle backspaces and clears overrides when empty`(): Unit =
    runBlocking {
      val fakeUi = FakeUi(createWhsPanel().component)
      deviceManager.activeExercise = true
      stateManager.ongoingExercise.waitForValue(true)

      val textField = fakeUi.waitForDescendant<JTextField> { it.isVisible }
      runInEdt {
        fakeUi.keyboard.setFocus(textField)
        fakeUi.keyboard.type(KeyEvent.VK_5)
      }

      assertThat(textField.text).isEqualTo("5")
      stateManager
        .getState(WHS_CAPABILITIES[0])
        .mapState { (it as? PendingUserChangesCapabilityUIState)?.userState?.overrideValue }
        .waitForValue(WhsDataType.HEART_RATE_BPM.value(5))

      runInEdt { fakeUi.keyboard.type(KeyEvent.VK_0) }

      assertThat(textField.text).isEqualTo("50")
      stateManager
        .getState(WHS_CAPABILITIES[0])
        .mapState { (it as? PendingUserChangesCapabilityUIState)?.userState?.overrideValue }
        .waitForValue(WhsDataType.HEART_RATE_BPM.value(50))

      runInEdt { fakeUi.keyboard.pressAndRelease(KeyEvent.VK_BACK_SPACE) }

      assertThat(textField.text).isEqualTo("5")
      stateManager
        .getState(WHS_CAPABILITIES[0])
        .mapState { (it as? PendingUserChangesCapabilityUIState)?.userState?.overrideValue }
        .waitForValue(WhsDataType.HEART_RATE_BPM.value(5))

      runInEdt { fakeUi.keyboard.pressAndRelease(KeyEvent.VK_BACK_SPACE) }

      assertThat(textField.text).isEmpty()
      stateManager
        .getState(WHS_CAPABILITIES[0])
        .mapState { (it as? UpToDateCapabilityUIState)?.upToDateState?.overrideValue }
        .waitForValue(WhsDataType.HEART_RATE_BPM.noValue())
    }

  @Test
  fun `when an exercise is started pending capability changes are hidden`(): Unit = runBlocking {
    val fakeUi = FakeUi(createWhsPanel().component)

    val heartRateCapability = WHS_CAPABILITIES[0]
    stateManager.setCapabilityEnabled(heartRateCapability, false)
    fakeUi.waitForCheckbox("Heart rate*", selected = false)
    fakeUi.waitForDescendant<JLabel> { it.text == "Heart rate*" && it.isEnabled }

    deviceManager.activeExercise = true
    stateManager.ongoingExercise.waitForValue(true)

    fakeUi.waitForCheckbox("Heart rate", selected = true)
    fakeUi.waitForDescendant<JLabel> { it.text == "Heart rate" && it.isEnabled }
  }

  @Test
  fun `when an exercise is stopped pending user override changes are not shown`(): Unit =
    runBlocking {
      val fakeUi = FakeUi(createWhsPanel().component)
      deviceManager.activeExercise = true
      stateManager.ongoingExercise.waitForValue(true)

      val heartRateCapability = WHS_CAPABILITIES[0]
      stateManager.setOverrideValue(heartRateCapability, 50)
      fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate*") }

      deviceManager.activeExercise = false
      stateManager.ongoingExercise.waitForValue(false)
      fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate") }
    }

  @Test
  fun `reset and apply buttons are enabled during an exercise if at least one capability is enabled`():
    Unit = runBlocking {
    stateManager.setCapabilityEnabled(stateManager.capabilitiesList.first(), true)
    stateManager.capabilitiesList.drop(1).forEach { stateManager.setCapabilityEnabled(it, false) }
    stateManager.applyChanges()

    val fakeUi = FakeUi(createWhsPanel().component)
    val resetButton =
      fakeUi.waitForDescendant<JButton> { it.text == message("wear.whs.panel.reset") }
    val applyButton =
      fakeUi.waitForDescendant<JButton> { it.text == message("wear.whs.panel.apply") }

    assertThat(resetButton.isEnabled).isTrue()
    assertThat(applyButton.isEnabled).isTrue()

    deviceManager.activeExercise = true
    stateManager.ongoingExercise.waitForValue(true)

    assertThat(resetButton.isEnabled).isTrue()
    assertThat(applyButton.isEnabled).isTrue()
  }

  // Regression test for b/371285068
  @Test
  fun `reset and apply buttons are disabled during an exercise if no capabilities are enabled`():
    Unit = runBlocking {
    stateManager.capabilitiesList.forEach { stateManager.setCapabilityEnabled(it, false) }
    stateManager.applyChanges()

    val fakeUi = FakeUi(createWhsPanel().component)
    val resetButton =
      fakeUi.waitForDescendant<JButton> { it.text == message("wear.whs.panel.reset") }
    val applyButton =
      fakeUi.waitForDescendant<JButton> { it.text == message("wear.whs.panel.apply") }

    assertThat(resetButton.isEnabled).isTrue()
    assertThat(applyButton.isEnabled).isTrue()

    deviceManager.activeExercise = true
    stateManager.ongoingExercise.waitForValue(true)

    retryUntilPassing(2.seconds) {
      assertThat(resetButton.isEnabled).isFalse()
      assertThat(applyButton.isEnabled).isFalse()
    }
  }

  // Regression test for b/372265643
  @Test
  fun `disabled sensors don't show values during an exercise`() =
    runBlocking<Unit> {
      deviceManager.setCapabilities(mapOf(WhsDataType.HEART_RATE_BPM to false))
      deviceManager.overrideValues(listOf(WhsDataType.HEART_RATE_BPM.value(89)))
      deviceManager.activeExercise = true
      stateManager.forceUpdateState()

      val fakeUi = FakeUi(createWhsPanel().component)
      val heartRateTextField =
        fakeUi.waitForDescendant<JTextField> { it.isVisible && !it.isEnabled }
      assertThat(heartRateTextField.text).isEmpty()
    }

  private fun FakeUi.waitForCheckbox(text: String, selected: Boolean) =
    waitForDescendant<JCheckBox> { checkbox ->
      checkbox.hasLabel(text) && checkbox.isSelected == selected
    }

  private fun JCheckBox.hasLabel(text: String) =
    parent.findDescendant<JLabel> { it.text == text } != null

  private var whsPanelCreated = false

  private fun createWhsPanel(
    reset: () -> Unit = {},
    applyChanges: () -> Unit = {},
    triggerEvent: (EventTrigger) -> Unit = {},
  ): WearHealthServicesPanel {
    if (whsPanelCreated) {
      throw IllegalStateException(
        "The WHS Panel should only be created once per test. " +
          "This is because the coroutines will end up interfering with each other if there are more than one panel at a time."
      )
    }
    return createWearHealthServicesPanel(
        stateManager = stateManager,
        uiScope = uiScope,
        workerScope = workerScope,
        informationLabelFlow = informationLabelFlow,
        reset = { reset() },
        applyChanges = { applyChanges() },
        triggerEvent = { triggerEvent(it) },
      )
      .also { whsPanelCreated = true }
  }
}
