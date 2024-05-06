/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.DetailsViewDeviceSelectorListView.DetailsViewDeviceSelectorListViewListener
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import icons.StudioIcons
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import java.time.Duration
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Unit tests for [DetailsViewDeviceSelectorListView].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class DetailsViewDeviceSelectorListViewTest {
  @get:Rule val edtRule = EdtRule()

  @Mock lateinit var mockListener: DetailsViewDeviceSelectorListViewListener

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun deviceListIsEmptyByDefault() {
    val view = DetailsViewDeviceSelectorListView(mockListener)
    assertThat(view.deviceList.itemsCount).isEqualTo(0)
  }

  @Test
  fun addDevice() {
    val view = DetailsViewDeviceSelectorListView(mockListener)
    val device = device(id = "device id", name = "device name")

    view.addDevice(device)

    assertThat(view.deviceList.itemsCount).isEqualTo(1)
    assertThat(view.deviceList.model.getElementAt(0)).isEqualTo(device)
  }

  @Test
  fun selectDevice() {
    val view = DetailsViewDeviceSelectorListView(mockListener)
    val device1 = device(id = "device id 1", name = "device name 1")
    val device2 = device(id = "device id 2", name = "device name 2")

    view.addDevice(device1)
    view.addDevice(device2)

    assertThat(view.deviceList.itemsCount).isEqualTo(2)
    assertThat(view.deviceList.selectedIndices).isEmpty()  // Nothing is selected initially.

    view.deviceList.selectedIndex = 0

    verify(mockListener).onDeviceSelected(eq(device1))
  }

  @Test
  fun selectRawOutputItem() {
    val view = DetailsViewDeviceSelectorListView(mockListener)
    val device = device(id = "device id", name = "device name")

    view.addDevice(device)
    view.selectRawOutputItem()

    assertThat(view.deviceList.itemsCount).isEqualTo(2)
    assertThat(view.deviceList.model.getElementAt(0)).isInstanceOf(DetailsViewDeviceSelectorListView.RawOutputItem::class.java)
    assertThat(view.deviceList.model.getElementAt(1)).isEqualTo(device)
    assertThat(view.deviceList.selectedValue).isInstanceOf(DetailsViewDeviceSelectorListView.RawOutputItem::class.java)
  }

  @Test
  fun cellRenderer() {
    val device = device(id = "device id", name = "<device name>")
    val results = mock<AndroidTestResults>().apply {
      whenever(getDuration(eq(device))).thenReturn(Duration.ofMillis(1234))
      whenever(getTestCaseResult(eq(device))).thenReturn(AndroidTestCaseResult.FAILED)
    }
    val view = DetailsViewDeviceSelectorListView(mockListener).apply {
      addDevice(device)
      setAndroidTestResults(results)
    }

    val rendererComponent = view.deviceList.cellRenderer.getListCellRendererComponent(
      view.deviceList, view.deviceList.model.getElementAt(0), 0, true, true) as JPanel
    val deviceLabelContainer = rendererComponent.getComponent(0) as JPanel
    val deviceLabel = deviceLabelContainer.getComponent(1) as JLabel
    val statusLabel = rendererComponent.getComponent(1) as JLabel

    assertThat(deviceLabel.text).isEqualTo("<html>&lt;device name&gt;<br><font color='#999999'>API 28 - 1 s</font></html>")
    assertThat(deviceLabel.icon).isSameAs(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)
    assertThat(deviceLabel.font).isSameAs(view.deviceList.font)
    assertThat(statusLabel.text).isEqualTo("")
    assertThat(statusLabel.icon).isSameAs(AllIcons.RunConfigurations.TestFailed)
    assertThat(statusLabel.font).isSameAs(view.deviceList.font)
  }

  private fun device(id: String, name: String): AndroidDevice {
    return AndroidDevice(id, name, name, AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(28))
  }
}