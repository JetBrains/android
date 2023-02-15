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
package com.android.tools.idea.appinspection.inspectors.network.view.constants

import com.android.tools.adtui.common.canvasTooltipBackground
import com.android.tools.adtui.common.primaryContentBackground
import com.intellij.ui.JBColor
import java.awt.Color

val TRANSPARENT_COLOR: Color = JBColor(Color(0, 0, 0, 0), Color(0, 0, 0, 0))
val DEFAULT_BACKGROUND: Color = primaryContentBackground
val DEFAULT_STAGE_BACKGROUND: Color = primaryContentBackground
val MONITOR_BORDER_COLOR: Color = JBColor(0xC9C9C9, 0x3F4142)
val NETWORK_RECEIVING_COLOR: Color = JBColor(0x5882CC, 0x557CC1)
val NETWORK_RECEIVING_SELECTED_COLOR: Color = JBColor(0x8ebdff, 0x8ebdff)
val NETWORK_SENDING_COLOR: Color = JBColor(0xF4AF6F, 0xFFC187)
val NETWORK_WAITING_COLOR: Color = JBColor(0xAAAAAA, 0xAAAAAA)
val NETWORK_THREADS_VIEW_TOOLTIP_DIVIDER: Color = JBColor(0xD3D3D3, 0x565656)
val TOOLTIP_BACKGROUND: Color = canvasTooltipBackground
val TOOLTIP_TEXT = JBColor.foreground()
