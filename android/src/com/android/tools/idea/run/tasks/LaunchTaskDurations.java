/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.tasks;

/**
 * Constant values of estimated {@link LaunchTask} durations. They are unit-less and relative to
 * one another. For example, connecting a debugger typically lasts five times longer than
 * launching an activity.
 */
public final class LaunchTaskDurations {
  private LaunchTaskDurations() {}

  public static final int ASYNC_TASK = 1;
  public static final int LAUNCH_ACTIVITY = 2;
  public static final int CLEAR_APP_DATA = 2;
  public static final int CONNECT_DEBUGGER = 10;
  public static final int DEPLOY_HOTSWAP = 8;
  public static final int DEPLOY_APK = 20;
  public static final int DEPLOY_INSTANT_APP = 20;
  public static final int UNINSTALL_IOT_APK = 20;
}
