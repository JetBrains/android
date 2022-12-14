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
package com.android.tools.idea.streaming

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroup.Companion.findRegisteredGroup
import com.intellij.openapi.actionSystem.DataKey

/** Constants for the Running Devices tool window. */

@JvmField internal val NUMBER_OF_DISPLAYS = DataKey.create<Int>("NumberOfDisplays")

internal val RUNNING_DEVICES_NOTIFICATION_GROUP: NotificationGroup
  get() = findRegisteredGroup("Running Devices Messages")!!

internal const val PRIMARY_DISPLAY_ID = 0

internal const val STREAMING_SECONDARY_TOOLBAR_ID = "StreamingToolbarSecondary"
