/*
  * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.snapshots

import com.intellij.openapi.fileTypes.FileType
import icons.StudioIcons
import javax.swing.Icon

const val EXT_LAYOUT_INSPECTOR = "li"
const val DOT_EXT_LAYOUT_INSPECTOR = ".li"

object LayoutInspectorFileType : FileType {
  // For reference from META-INF:
  val INSTANCE: FileType = this

  override fun getName(): String {
    return "Layout Inspector"
  }

  override fun getDescription(): String {
    return "Layout Inspector Snapshot"
  }

  override fun getDefaultExtension(): String {
    return EXT_LAYOUT_INSPECTOR
  }

  override fun getIcon(): Icon? {
    return StudioIcons.Shell.Menu.LAYOUT_INSPECTOR
  }

  override fun isBinary(): Boolean {
    return true
  }

  override fun isReadOnly(): Boolean {
    return true
  }
}
