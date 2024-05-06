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
package com.android.tools.idea.adb.wireless

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findDescendant
import com.google.common.net.InetAddresses
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JScrollPane

/** Tests for [PairingCodeContentPanel]. */
@RunsInEdt
class PairingCodeContentPanelTest {

  @get:Rule
  val edtRule = EdtRule()

  @Test
  fun test() {
    val panel = PairingCodeContentPanel()
    panel.component.setSize(100, 100)
    val ui = FakeUi(panel.component)
    val scrollPane = ui.getComponent<JScrollPane>()
    val deviceList = scrollPane.viewport.getComponent(0) as Container

    val services1 = listOf(
      MdnsService("Service1", ServiceType.PairingCode, InetAddresses.fromInteger(1), 1001),
      MdnsService("Service2", ServiceType.QrCode, InetAddresses.fromInteger(1), 1002)
    )
    panel.showDevices(services1) {}
    assertThat(deviceList.components).hasLength(2)
    assertThat(deviceList.components[0].findDescendant<JLabel> { it.text == "0.0.0.1:1001" }).isNotNull()
    assertThat(deviceList.components[1].findDescendant<JLabel> { it.text == "0.0.0.1:1002" }).isNotNull()

    val services2 = listOf(
      MdnsService("Service2", ServiceType.QrCode, InetAddresses.fromInteger(1), 1002),
      MdnsService("Service3", ServiceType.PairingCode, InetAddresses.fromInteger(1), 1003),
    )
    panel.showDevices(services2) {}
    assertThat(deviceList.components).hasLength(2)
    assertThat(deviceList.components[0].findDescendant<JLabel> { it.text == "0.0.0.1:1002" }).isNotNull()
    assertThat(deviceList.components[1].findDescendant<JLabel> { it.text == "0.0.0.1:1003" }).isNotNull()

    panel.showDevices(listOf()) {}
    assertThat(deviceList.components).isEmpty()
  }
}