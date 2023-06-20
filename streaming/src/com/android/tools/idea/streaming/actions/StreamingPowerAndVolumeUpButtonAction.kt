/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.streaming.actions

import com.android.tools.idea.streaming.device.actions.DevicePowerAndVolumeUpButtonAction
import com.android.tools.idea.streaming.emulator.actions.EmulatorPowerAndVolumeUpButtonAction

/**
 * Simulates pressing the Power and the Volume Up buttons together on an Android device.
 * This button combination invokes Android Power Menu on devices with API >= 31.
 */
internal class StreamingPowerAndVolumeUpButtonAction :
  StreamingPushButtonAction(EmulatorPowerAndVolumeUpButtonAction(), DevicePowerAndVolumeUpButtonAction())