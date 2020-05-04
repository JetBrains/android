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
package com.android.tools.idea.appinspection.inspector.ide

import com.android.annotations.concurrency.UiThread

/**
 * A set of utility methods used for communicating requests to the IDE.
 */
interface AppInspectionIdeServices {
  /**
   * Shows the App Inspection tool window.
   * @param callback A callback executed right after the window shows up. The call is asynchronous since it may require animation.
   */
  @UiThread
  fun showToolWindow(@UiThread callback: () -> Unit = { })
}