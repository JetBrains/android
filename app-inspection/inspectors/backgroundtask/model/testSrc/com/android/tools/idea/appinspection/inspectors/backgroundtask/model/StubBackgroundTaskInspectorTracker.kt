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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.model

import com.google.wireless.android.sdk.stats.AppInspectionEvent.BackgroundTaskInspectorEvent

class StubBackgroundTaskInspectorTracker : BackgroundTaskInspectorTracker {
  override fun trackTableModeSelected() = Unit
  override fun trackGraphModeSelected(
    context: BackgroundTaskInspectorEvent.Context,
    chainInfo: BackgroundTaskInspectorEvent.ChainInfo
  ) = Unit
  override fun trackJumpedToSource() = Unit
  override fun trackWorkCancelled() = Unit
  override fun trackWorkSelected(context: BackgroundTaskInspectorEvent.Context) = Unit
  override fun trackJobSelected() = Unit
  override fun trackJobUnderWorkSelected() = Unit
  override fun trackAlarmSelected() = Unit
  override fun trackWakeLockSelected() = Unit
  override fun trackWakeLockUnderJobSelected() = Unit
}
