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
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.filters.LogcatFilter
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.TextAccumulator
import com.android.tools.idea.logcat.settings.AndroidLogcatSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey

/**
 * Encapsulates the presentation of Logcat messages.
 */
internal interface LogcatPresenter : TagsProvider, PackageNamesProvider, ProcessNamesProvider, Disposable {
  var formattingOptions: FormattingOptions

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
   * Clears the message view
   */
  fun clearMessageView()

  @UiThread
  fun restartLogcat()

  /**
   * Returns true if the attached logcat is empty
   */
  fun isLogcatEmpty(): Boolean

  /**
   * Processes incoming messages from logcat
   */
  suspend fun processMessages(messages: List<LogcatMessage>)

  /**
   * Emits formatted text to the message view
   */
  suspend fun appendMessages(textAccumulator: TextAccumulator)

  /**
   * Returns the connected device or null if not connected
   */
  fun getConnectedDevice(): Device?

  fun getSelectedDevice(): Device?

  fun applyLogcatSettings(logcatSettings: AndroidLogcatSettings)

  fun countFilterMatches(filter: LogcatFilter?): Int

  fun foldImmediately()

  fun isLogcatPaused(): Boolean

  fun pauseLogcat()

  fun resumeLogcat()

  fun getFilter(): String

  fun setFilter(filter: String)

  fun isSoftWrapEnabled(): Boolean

  fun setSoftWrapEnabled(state: Boolean)

  fun getBacklogMessages() : List<LogcatMessage>

  suspend fun enterInvisibleMode()

  fun isShowing() : Boolean

  companion object {
    val LOGCAT_PRESENTER_ACTION = DataKey.create<LogcatPresenter>("LogcatPresenter")
  }
}
