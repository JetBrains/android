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
package com.android.tools.idea.devicemanagerv2

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.Reservation
import com.android.sdklib.deviceprovisioner.ReservationState
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.categorytable.TablePresentation
import com.android.tools.adtui.categorytable.TablePresentationManager
import com.android.tools.idea.wearpairing.WearPairingManager
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.AppUIUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import java.awt.Color
import java.time.Instant
import java.time.ZoneId
import javax.swing.UIManager
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

@RunsInEdt
class DeviceNamePanelTest {

  private val themeManagerRule = ThemeManagerRule()

  @get:Rule
  val ruleChain =
    RuleChain.outerRule(ApplicationRule()).around(themeManagerRule).around(EdtRule())!!

  @Test
  fun changeTheme() {
    themeManagerRule.setDarkTheme(false)

    val panel = DeviceNamePanel(WearPairingManager.getInstance())
    panel.updateTablePresentation(
      TablePresentationManager(),
      TablePresentation(foreground = JBColor.BLACK, background = JBColor.WHITE, false)
    )

    assertThat(panel.nameLabel.foreground).isEqualTo(JBColor.BLACK)
    assertThat(panel.nameLabel.foreground.rgb and 0xFFFFFF).isEqualTo(0)
    assertThat(panel.line2Label.foreground).isEqualTo(JBColor.BLACK.lighten())
    assertThat(panel.line2Label.foreground.rgb and 0xFFFFFF).isEqualTo(0x323232)

    themeManagerRule.setDarkTheme(true)

    // This assertion is the same, but JBColor.BLACK means something different now:
    assertThat(panel.nameLabel.foreground).isEqualTo(JBColor.BLACK)
    assertThat(panel.nameLabel.foreground.rgb and 0xFFFFFF).isEqualTo(0xBBBBBB)
    assertThat(panel.line2Label.foreground).isEqualTo(JBColor.BLACK.lighten())
    @Suppress("UseJBColor")
    assertThat(panel.line2Label.foreground.rgb and 0xFFFFFF)
      .isEqualTo(Color(0xBBBBBB).darker().rgb and 0xFFFFFF)
  }

  @Test
  fun stateTransitionText() {
    fun rowData(isTransitioning: Boolean, status: String): DeviceRowData {
      val state = DeviceState.Disconnected(DeviceProperties.build {}, isTransitioning, status, null)
      val handle = mock<DeviceHandle>()
      whenever(handle.state).thenReturn(state)
      val rowData = mock<DeviceRowData>()
      whenever(rowData.handle).thenReturn(handle)
      return rowData
    }

    assertThat(rowData(true, "Reserving a device").stateTransitionText())
      .isEqualTo("Reserving a device")
    assertThat(rowData(false, "Reserving a device").stateTransitionText()).isNull()
  }

  @Test
  fun reservationText() {
    assertThat(
        Reservation(
            ReservationState.ACTIVE,
            "Connected",
            null,
            Instant.parse("2023-02-03T19:15:30.00Z")
          )
          .line2Text(ZoneId.of("UTC"))
      )
      .isEqualTo("Connected; device will expire at 7:15 PM")

    assertThat(
        Reservation(ReservationState.ACTIVE, "", null, Instant.parse("2023-02-03T19:15:30.00Z"))
          .line2Text(ZoneId.of("UTC"))
      )
      .isEqualTo("Device will expire at 7:15 PM")

    assertThat(
        Reservation(ReservationState.PENDING, "Connection pending", null, null)
          .line2Text(ZoneId.of("UTC"))
      )
      .isEqualTo("Connection pending")

    assertThat(
        Reservation(
            ReservationState.ACTIVE,
            "",
            null,
            null,
          )
          .line2Text(ZoneId.of("UTC"))
      )
      .isNull()
  }
}

/**
 * Rule for testing changes between light and dark themes. Actually changing the themes the way the
 * IDE does it is highly problematic in headless unit tests; this changes the relevant bits needed
 * for this class.
 */
class ThemeManagerRule : ExternalResource() {

  private val defaults = mutableMapOf<String, Any?>("Label.foreground" to Gray.xBB)

  override fun before() {
    swapDefaults()
  }

  override fun after() {
    swapDefaults()
  }

  private fun swapDefaults() {
    defaults.iterator().forEach { entry ->
      val oldValue = UIManager.getDefaults().get(entry.key)
      UIManager.getDefaults().put(entry.key, entry.value)
      entry.setValue(oldValue)
    }
  }

  fun setDarkTheme(dark: Boolean) {
    AppUIUtil.updateForDarcula(dark)
  }
}
