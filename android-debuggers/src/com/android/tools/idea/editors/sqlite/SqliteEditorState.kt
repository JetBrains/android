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
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import org.jdom.Element

/**
 * Persistent [state][FileEditorState] associated with a file opened with a [SqliteEditor].
 */
data class SqliteEditorState(val deviceFileId: DeviceFileId) : FileEditorState {

  fun writeState(targetElement: Element) {
    targetElement.setAttribute(DEVICE_ID_ATTR_NAME, deviceFileId.deviceId)
    targetElement.setAttribute(DEVICE_PATH_ATTR_NAME, deviceFileId.devicePath)
  }

  override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel) = otherState is SqliteEditorState

  companion object {
    private const val DEVICE_ID_ATTR_NAME = "device-id"
    private const val DEVICE_PATH_ATTR_NAME = "device-path"

    fun readState(sourceElement: Element?): SqliteEditorState {
      return SqliteEditorState(
        if (sourceElement == null) {
          DeviceFileId.UNKNOWN
        }
        else {
          DeviceFileId(
            sourceElement.getAttributeValue(DEVICE_ID_ATTR_NAME, ""),
            sourceElement.getAttributeValue(DEVICE_PATH_ATTR_NAME, "")
          )
        }
      )
    }
  }
}