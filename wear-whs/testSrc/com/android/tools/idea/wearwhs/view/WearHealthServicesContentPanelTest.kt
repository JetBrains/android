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
import com.android.tools.idea.wearwhs.WhsDataType
import com.android.tools.idea.wearwhs.communication.FakeDeviceManager
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.awt.Dimension
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class WearHealthServicesContentPanelTest {
  companion object {
    const val TEST_MAX_WAIT_TIME_SECONDS = 5L
    const val TEST_POLLING_INTERVAL_MILLISECONDS = 100L
  }

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val edtRule = EdtRule()

  private val testDataPath: Path
    get() = TestUtils.resolveWorkspacePathUnchecked("tools/adt/idea/wear-whs/testData")

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
          deviceManager,
          pollingIntervalMillis = TEST_POLLING_INTERVAL_MILLISECONDS,
          workerScope = testWorkerScope,
        )
        .also { Disposer.register(projectRule.testRootDisposable, it) }
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

      stateManager.preset.value = Preset.CUSTOM
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

    assertThat(textField.text).isNotEqualTo("50.0")
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
  fun `test panel disables checkboxes and dropdown during an exercise`() =
    runBlocking<Unit> {
      val fakeUi = FakeUi(whsPanel.component)

      fakeUi.waitForDescendant<ComboBox<Preset>> { it.isEnabled }
      fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate") && it.isEnabled }
      fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Steps") && it.isEnabled }

      deviceManager.activeExercise = true

      fakeUi.waitForDescendant<ComboBox<Preset>> { !it.isEnabled }
      fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate") && !it.isEnabled }
      fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Steps") && !it.isEnabled }
    }

  @Test
  fun `test star is only visible when changes are pending`(): Unit = runBlocking {
    val fakeUi = FakeUi(whsPanel.component)

    // TODO: Remove this apply when ag/26161198 is merged
    val applyButton = fakeUi.waitForDescendant<JButton> { it.text == "Apply" }
    applyButton.doClick()

    val hrCheckBox = fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate") }
    hrCheckBox.doClick()

    fakeUi.waitForDescendant<JCheckBox> { it.hasLabel("Heart rate*") }

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

      // Heart Rate
      stateManager.setCapabilityEnabled(WHS_CAPABILITIES[0], true)
      stateManager.setOverrideValue(WHS_CAPABILITIES[0], 50f)
      stateManager.applyChanges()

      deviceManager.activeExercise = true

      fakeUi.waitForCheckbox("Heart rate", true)
      fakeUi.waitForDescendant<JTextField> { it.text == "50.0" && it.isVisible && it.isEnabled }
      fakeUi.waitForDescendant<JLabel> { it.text == "bpm" && it.isEnabled }
    }

  @Ignore("b/342411390")
  @Test
  fun `test disabled sensors have disabled override value fields and units during exercise`() =
    runBlocking<Unit> {
      val fakeUi = FakeUi(whsPanel.component)

      // Heart Rate
      stateManager.setCapabilityEnabled(WHS_CAPABILITIES[0], false)
      stateManager.setOverrideValue(WHS_CAPABILITIES[0], 50f)
      stateManager.applyChanges()

      deviceManager.activeExercise = true

      fakeUi.waitForCheckbox("Heart rate", false)
      fakeUi.waitForDescendant<JTextField> { it.text == "50.0" && it.isVisible && !it.isEnabled }
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
