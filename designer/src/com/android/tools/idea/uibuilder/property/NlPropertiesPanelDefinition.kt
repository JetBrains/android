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
package com.android.tools.idea.uibuilder.property

import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.idea.common.surface.DesignSurface
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet

private val DEFAULT_PROPERTY_SIDE_WIDTH = JBUI.scale(280)

/** Tool Window definition for the Properties Panel */
class NlPropertiesPanelDefinition(
  facet: AndroidFacet,
  side: Side,
  split: Split,
  autoHide: AutoHide,
) :
  ToolWindowDefinition<DesignSurface<*>>(
    "Attributes",
    StudioIcons.Shell.ToolWindows.ATTRIBUTES,
    "PROPERTIES",
    side,
    split,
    autoHide,
    DEFAULT_PROPERTY_SIDE_WIDTH,
    DEFAULT_BUTTON_SIZE,
    ALLOW_FLOATING or ALLOW_SPLIT_MODE,
    { NlPropertiesPanelToolContent(facet, it) },
  )
