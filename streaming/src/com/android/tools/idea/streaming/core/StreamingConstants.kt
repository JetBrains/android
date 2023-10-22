/*
 * Copyright (C) 2022 The Android Open Source Project
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
@file:JvmName("StreamingConstants")
package com.android.tools.idea.streaming.core

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroup.Companion.findRegisteredGroup
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.StudioIcons

/** Constants for the Running Devices tool window. */

@JvmField val DISPLAY_VIEW_KEY = DataKey.create<AbstractDisplayView>("DisplayView")

@JvmField val STREAMING_CONTENT_PANEL_KEY = DataKey.create<BorderLayoutPanel>("StreamingContentPanel")

@JvmField val DEVICE_ID_KEY = DataKey.create<DeviceId>("DeviceId")

@JvmField internal val NUMBER_OF_DISPLAYS_KEY = DataKey.create<Int>("NumberOfDisplays")

internal val RUNNING_DEVICES_NOTIFICATION_GROUP: NotificationGroup
  get() = findRegisteredGroup("Running Devices Messages")!!

internal const val PRIMARY_DISPLAY_ID = 0

internal const val STREAMING_SECONDARY_TOOLBAR_ID = "StreamingToolbarSecondary"

internal val FOLDING_STATE_ICONS = mapOf(
  "Closed" to StudioIcons.Emulator.Menu.POSTURE_CLOSED,
  "Dual Display Mode" to StudioIcons.Emulator.Menu.POSTURE_DUAL_DISPLAY,
  "Flipped" to StudioIcons.Emulator.Menu.POSTURE_FLIPPED,
  "Half-Open" to StudioIcons.Emulator.Menu.POSTURE_HALF_FOLDED,
  "Open" to StudioIcons.Emulator.Menu.POSTURE_OPEN,
  "Rear Display Mode" to StudioIcons.Emulator.Menu.POSTURE_REAR_DISPLAY,
  "Tent" to StudioIcons.Emulator.Menu.POSTURE_TENT,
)
