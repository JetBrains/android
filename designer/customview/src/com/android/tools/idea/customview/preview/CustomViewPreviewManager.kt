/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.customview.preview

interface CustomViewPreviewManager {
  /**
   * A state exposed to the CustomViewPreviewManager clients to know what type of notifications to display.
   */
  enum class NotificationsState {
    /**
     * Awaiting for build to finish.
     */
    BUILDING,
    /**
     * Related source code is modified, therefore preview might be not up to date.
     */
    CODE_MODIFIED,
    /**
     * There are no notifications to show (all is fine).
     */
    NO_NOTIFICATIONS,
    /**
     * The previous build failed, therefore preview might be incorrect.
     */
    BUILD_FAILED,
  }

  val views: List<String>
  var currentView: String
  var shrinkHeight: Boolean
  var shrinkWidth: Boolean

  val notificationsState: NotificationsState
}