/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.screenshottest

import com.intellij.ide.plugins.CountIcon
import com.intellij.openapi.fileTypes.FileType
import javax.swing.Icon

/**
 * Custom FileType for screenshot test results in memory virtual file.
 */
class ScreenshotTestResultFileType private constructor() : FileType {
  override fun getName(): String = "Screenshot Test Result"

  override fun getDescription(): String = "Screenshot test results details view"

  override fun getDefaultExtension(): String = ""

  // TODO(b/393431825) Design a new icon for screenshot test result
  override fun getIcon(): Icon = CountIcon()

  override fun isBinary(): Boolean = false

  companion object {
    val INSTANCE = ScreenshotTestResultFileType()
  }
}