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
package com.android.tools.idea.logcat

import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.logcat.filters.LogcatFilter
import com.android.tools.idea.logcat.messages.TextAccumulator
import com.android.tools.idea.logcat.settings.LogcatSettings
import com.intellij.openapi.Disposable

/**
 * Encapsulates the presentation of Logcat messages.
 */
internal interface LogcatPresenter : TagsProvider, PackageNamesProvider, Disposable {
  /**
   * Reloads messages from the backlog into the view
   */
  @UiThread
  fun reloadMessages()

  /**
   * Applies a filter and reloads
   */
  @UiThread
  fun applyFilter(logcatFilter: LogcatFilter?)

  /**
   * Enabled/disables the `My Apps` filter
   */
  @UiThread
  fun setShowOnlyProjectApps(enabled: Boolean)

  /**
   * Clears the message view
   */
  @UiThread
  fun clearMessageView()

  /**
   * Returns true if the attached logcat is empty
   */
  fun isLogcatEmpty(): Boolean

  /**
   * Processes incoming messages from logcat
   */
  suspend fun processMessages(messages: List<LogCatMessage>)

  /**
   * Emits formatted text to the message view
   */
  suspend fun appendMessages(textAccumulator: TextAccumulator)

  /**
   * Returns `true` if panel is attached to a device
   */
  fun isAttachedToDevice(): Boolean

  fun applyLogcatSettings(logcatSettings: LogcatSettings)
}
