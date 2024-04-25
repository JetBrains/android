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
package com.android.tools.idea.run.deployment.selector

import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.LocalEmulatorSnapshot
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import kotlinx.datetime.Instant
import org.junit.Test
import java.nio.file.Path

class SelectionStatePersistenceTest {
  @Test
  fun selectionState() {
    val selectionState = SelectionStateXml()
    val templateId = DeviceId("Test", true, "abcd")
    val target1 = TargetId(templateId, templateId, DefaultBoot)
    val target2 = TargetId(DeviceId("Test", false, "efg"), templateId, DefaultBoot)
    val target3 = TargetId(DeviceId("Test", false, "hijk"), null, ColdBoot)
    selectionState.runConfigName = "app"
    selectionState.selectionMode = SelectionMode.DIALOG
    selectionState.dropdownSelection =
      DropdownSelectionXml(
        target = target1.toXml(),
        timestamp = Instant.parse("2023-01-08T01:02:03Z")
      )
    selectionState.dialogSelection = DialogSelectionXml(listOf(target2, target3).toXml())

    selectionState.assertSerializes(
      """<SelectionState runConfigName="app">
        |  <option name="selectionMode" value="DIALOG" />
        |  <DropdownSelection timestamp="2023-01-08T01:02:03Z">
        |    <Target type="DEFAULT_BOOT">
        |      <handle />
        |      <template>
        |        <DeviceId pluginId="Test" type="TEMPLATE" identifier="abcd" />
        |      </template>
        |    </Target>
        |  </DropdownSelection>
        |  <DialogSelection>
        |    <targets>
        |      <Target type="DEFAULT_BOOT">
        |        <handle>
        |          <DeviceId pluginId="Test" type="HANDLE" identifier="efg" />
        |        </handle>
        |        <template>
        |          <DeviceId pluginId="Test" type="TEMPLATE" identifier="abcd" />
        |        </template>
        |      </Target>
        |      <Target type="COLD_BOOT">
        |        <handle>
        |          <DeviceId pluginId="Test" type="HANDLE" identifier="hijk" />
        |        </handle>
        |        <template />
        |      </Target>
        |    </targets>
        |  </DialogSelection>
        |</SelectionState>"""
        .trimMargin(),
      exactMatch = true
    )
  }

  @Test
  fun serializeDeviceId() {
    val id = DeviceId("Test", true, "abcd").toXml()
    assertThat(id.toXmlText())
      .isEqualTo("""<DeviceId pluginId="Test" type="TEMPLATE" identifier="abcd" />""")
  }

  @Test
  fun serializeBootWithSnapshot() {
    val target =
      TargetId(
        DeviceId("Test", false, "abcd"),
        null,
        BootSnapshot(LocalEmulatorSnapshot("snap", Path.of("/tmp/foo/snap")))
      )

    val targetXml = target.toXml()
    targetXml.assertSerializes(
      """<Target type="BOOT_WITH_SNAPSHOT">
          |  <handle>
          |    <DeviceId pluginId="Test" type="HANDLE" identifier="abcd" />
          |  </handle>
          |  <Snapshot name="snap" path="/tmp/foo/snap" type="LOCAL_EMULATOR" />
          |</Target>"""
        .trimMargin(),
    )

    assertThat(targetXml.fromXml()).isEqualTo(target)
  }

  /**
   * Verify that this object can be serialized to XML and then deserialized to an equivalent object.
   * Then, if exactMatch is false, verify that the given XML deserializes into an equivalent object.
   * If true, verify that it results in the exact XML text given. (This is useful for generating the
   * expected XML output.)
   */
  internal inline fun <reified T : Any> T.assertSerializes(
    expectedText: String,
    exactMatch: Boolean = false
  ) {
    val element = XmlSerializer.serialize(this)
    if (exactMatch) {
      assertThat(JDOMUtil.write(element)).isEqualTo(expectedText)
    } else {
      assertThat(XmlSerializer.deserialize(JDOMUtil.load(expectedText), T::class.java))
        .isEqualTo(this)
    }
    assertThat(XmlSerializer.deserialize(element, T::class.java)).isEqualTo(this)
  }

  fun Any.toXmlText() = JDOMUtil.write(XmlSerializer.serialize(this))
}
