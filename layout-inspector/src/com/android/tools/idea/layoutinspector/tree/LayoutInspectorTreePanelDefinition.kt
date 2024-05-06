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
package com.android.tools.idea.layoutinspector.tree

import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.intellij.icons.AllIcons

class LayoutInspectorTreePanelDefinition(
  side: Side = Side.RIGHT,
  split: Split = Split.TOP,
  overrideSide: Boolean = false,
  overrideSplit: Boolean = false,
  showGearAction: Boolean = true,
  showHideAction: Boolean = true
) :
  ToolWindowDefinition<LayoutInspector>(
    "Component Tree",
    AllIcons.Toolwindows.ToolWindowStructure,
    "TREE",
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
    { LayoutInspectorTreePanel(it) }
  )
