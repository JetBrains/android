/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.editors.sqlite

import com.android.tools.idea.device.fs.DeviceFileId
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.testFramework.UsefulTestCase
import org.jdom.Element

class SqliteEditorStateTest : UsefulTestCase() {

  @Throws(Exception::class)
  fun testWriteState() {
    // Prepare
    val fileId = DeviceFileId("device", "/path/to/file")
    val state = SqliteEditorState(fileId)
    val element = Element("state")

    // Act
    state.writeState(element)

    // Assert
    assertThat(element.getAttributeValue("device-id")).isEqualTo("device")
    assertThat(element.getAttributeValue("device-path")).isEqualTo("/path/to/file")
  }

  @Throws(Exception::class)
  fun testReadState() {
    // Prepare
    val element = Element("state")
    element.setAttribute("device-id", "device")
    element.setAttribute("device-path", "/path/to/file")

    // Act
    val state = SqliteEditorState.readState(element)

    // Assert
    assertThat(state.deviceFileId.deviceId).isEqualTo("device")
    assertThat(state.deviceFileId.devicePath).isEqualTo("/path/to/file")
  }

  @Throws(Exception::class)
  fun testEqualsAndHashCode() {
    // Prepare
    val fileId1 = DeviceFileId("device", "/path/to/file")
    val state1 = SqliteEditorState(fileId1)

    val fileId2 = DeviceFileId("device", "/path/to/file")
    val state2 = SqliteEditorState(fileId2)

    // Act

    // Assert
    assertThat(state1).isEqualTo(state2)
    assertThat(state2).isEqualTo(state1)
    assertThat(state1.hashCode()).isEqualTo(state2.hashCode())
  }

  @Throws(Exception::class)
  fun testEqualsAndHashCodeDifferent() {
    // Prepare
    val fileId1 = DeviceFileId("device", "/path/to/file")
    val state1 = SqliteEditorState(fileId1)

    val fileId2 = DeviceFileId("device-2", "/path/to/file")
    val state2 = SqliteEditorState(fileId2)

    // Act

    // Assert
    assertThat(state1).isNotEqualTo(state2)
    assertThat(state2).isNotEqualTo(state1)
    assertThat(state1.hashCode()).isNotEqualTo(state2.hashCode())
  }

  @Throws(Exception::class)
  fun testEqualsAndHashCodeDifferent2() {
    // Prepare
    val fileId1 = DeviceFileId("device", "/path/to/file")
    val state1 = SqliteEditorState(fileId1)

    val fileId2 = DeviceFileId("device", "/path/to/file-2")
    val state2 = SqliteEditorState(fileId2)

    // Act

    // Assert
    assertThat(state1).isNotEqualTo(state2)
    assertThat(state2).isNotEqualTo(state1)
    assertThat(state1.hashCode()).isNotEqualTo(state2.hashCode())
  }

  @Throws(Exception::class)
  fun testCanBeMerged() {
    // Prepare
    val fileId1 = DeviceFileId("device", "/path/to/file")
    val state1 = SqliteEditorState(fileId1)

    val fileId2 = DeviceFileId("device", "/path/to/file-2")
    val state2 = SqliteEditorState(fileId2)

    // Act

    // Assert
    assertThat(state1.canBeMergedWith(state2, FileEditorStateLevel.FULL)).isTrue()
    assertThat(state2.canBeMergedWith(state1, FileEditorStateLevel.FULL)).isTrue()

    assertThat(state1.canBeMergedWith(FileEditorState.INSTANCE, FileEditorStateLevel.FULL)).isFalse()
  }
}
