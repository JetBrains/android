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

import com.android.tools.idea.explorer.DeviceFileId
import com.google.common.base.Objects
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import org.jdom.Element

/**
 * Persistent [state][FileEditorState] accociated with a file opened with a [SqliteEditor].
 */
class SqliteEditorState(val deviceFileId: DeviceFileId) : FileEditorState {

  fun writeState(targetElement: Element) {
    targetElement.setAttribute(DEVICE_ID_ATTR_NAME, deviceFileId.deviceId)
    targetElement.setAttribute(DEVICE_PATH_ATTR_NAME, deviceFileId.devicePath)
  }

  override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean {
    return otherState is SqliteEditorState
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val that = other as SqliteEditorState?
    return Objects.equal(deviceFileId, that!!.deviceFileId)
  }

  override fun hashCode(): Int {
    return Objects.hashCode(deviceFileId)
  }

  override fun toString(): String {
    return String.format("SqliteEditorState{myDeviceFileId=%s}", deviceFileId)
  }

  companion object {
    private const val DEVICE_ID_ATTR_NAME = "device-id"
    private const val DEVICE_PATH_ATTR_NAME = "device-path"

    fun readState(sourceElement: Element): SqliteEditorState {
      val entryInfo = DeviceFileId(
          sourceElement.getAttributeValue(DEVICE_ID_ATTR_NAME, ""),
          sourceElement.getAttributeValue(DEVICE_PATH_ATTR_NAME, ""))
      return SqliteEditorState(entryInfo)
    }
  }
}