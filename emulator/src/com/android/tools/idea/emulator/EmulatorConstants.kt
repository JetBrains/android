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
@file:JvmName("EmulatorConstants")
package com.android.tools.idea.emulator

import com.android.tools.idea.IdeInfo
import com.intellij.build.BuildContentManager
import com.intellij.notification.NotificationGroup.Companion.toolWindowGroup
import com.intellij.openapi.actionSystem.DataKey

/** Embedded Emulator constants. */

const val EMULATOR_TOOL_WINDOW_ID = "Android Emulator"

val EMULATOR_TOOL_WINDOW_TITLE
  get() = if (IdeInfo.getInstance().isAndroidStudio) "Emulator" else "Android Emulator"

@JvmField val EMULATOR_CONTROLLER_KEY = DataKey.create<EmulatorController>("EmulatorController")

@JvmField val EMULATOR_VIEW_KEY = DataKey.create<EmulatorView>("EmulatorView")

internal const val EMULATOR_MAIN_TOOLBAR_ID = "EmulatorToolbar"

@JvmField internal val EMULATOR_TOOL_WINDOW_NOTIFICATION_GROUP = toolWindowGroup("Android Emulator", BuildContentManager.TOOL_WINDOW_ID)
