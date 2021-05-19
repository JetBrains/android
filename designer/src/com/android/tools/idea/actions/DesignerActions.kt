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
package com.android.tools.idea.actions

/**
 * List all the Action Ids for Android Designer Tools.
 * The registered Actions in designer/src/META-INF/designer.xml should use the Ids listed in this class.
 *
 * The action system doesn't allow ID duplications, so all the below Group and Action Ids should have "Android.Designer." prefix.
 */
@Suppress("unused") // values are mapped to META-INF/designer.xml
object DesignerActions {
  private const val PREFIX = "Android.Designer"

  const val GROUP_COMMON = "$PREFIX.CommonActions"
  const val GROUP_TOOLS = "$PREFIX.ToolsActions"

  const val GROUP_LAYOUT_EDITOR = "$PREFIX.LayoutEditorActions"

  //<editor-fold desc="Common Actions">
  const val ACTION_FORCE_REFRESH_PREVIEW = "$PREFIX.ForceRefreshPreview"
  const val ACTION_TOGGLE_ISSUE_PANEL = "$PREFIX.IssueNotificationAction"
  const val ACTION_RUN_LAYOUT_SCANNER = "$PREFIX.LayoutScannerAction"
  //</editor-fold>

  //<editor-fold desc="Layout Editor Actions">
  const val ACTION_SWITCH_DESIGN_MODE = "$PREFIX.SwitchDesignMode"
  const val ACTION_TOGGLE_DEVICE_ORIENTATION = "$PREFIX.ToggleDeviceOrientation"
  const val ACTION_TOGGLE_DEVICE_NIGHT_MODE = "$PREFIX.ToggleDeviceNightMode"
  const val ACTION_NEXT_DEVICE = "$PREFIX.NextDevice"
  const val ACTION_PREVIOUS_DEVICE = "$PREFIX.PreviousDevice"
  //</editor-fold>
}
