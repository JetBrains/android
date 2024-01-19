/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.properties

import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.intellij.icons.AllIcons

class LayoutInspectorPropertiesPanelDefinition(
  side: Side = Side.RIGHT,
  split: Split = Split.TOP,
  overrideSide: Boolean = false,
  overrideSplit: Boolean = false,
  showGearAction: Boolean = true,
  showHideAction: Boolean = true,
) :
  ToolWindowDefinition<LayoutInspector>(
    "Attributes",
    AllIcons.Toolwindows.ToolWindowStructure,
    "PROPERTIES",
    side,
    split,
    AutoHide.DOCKED,
    DEFAULT_SIDE_WIDTH,
    DEFAULT_BUTTON_SIZE,
    ALLOW_SPLIT_MODE,
    showGearAction,
    showHideAction,
    overrideSide,
    overrideSplit,
    { LayoutInspectorProperties(it) },
  )
