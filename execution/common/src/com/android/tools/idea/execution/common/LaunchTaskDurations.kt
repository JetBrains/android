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
package com.android.tools.idea.execution.common

/**
 * Constant values of estimated [LaunchTask] durations. They are unit-less and relative to
 * one another. For example, connecting a debugger typically lasts five times longer than
 * launching an activity.
 */
object LaunchTaskDurations {
  const val ASYNC_TASK = 1
  const val LAUNCH_ACTIVITY = 2
  const val CLEAR_APP_DATA = 2
  const val CONNECT_DEBUGGER = 10
  const val DEPLOY_HOTSWAP = 8
  const val DEPLOY_APK = 20
  const val DEPLOY_INSTANT_APP = 20
  const val UNINSTALL_IOT_APK = 20
}