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

import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.filters.LogcatFilter
import com.android.tools.idea.logcat.filters.LogcatMasterFilter
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.TextAccumulator
import com.android.tools.idea.logcat.settings.AndroidLogcatSettings
import java.nio.file.Path

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
internal class FakeLogcatPresenter : LogcatPresenter {
  private var reloadedMessages = 0
  var logcatRestartedCount = 0
  var attachedDevice: Device? = null
  var device: Device? = null
  var logcatFilter: String = ""

  val messageBatches = mutableListOf<List<LogcatMessage>>()
  val lineBatches = mutableListOf<List<String>>()
  val tagSet = mutableSetOf<String>()
  var showing = true

  override var formattingOptions: FormattingOptions = FormattingOptions()

  override fun reloadMessages() {
    reloadedMessages++
  }

  override fun clearMessageView() {
    lineBatches.clear()
  }

  override fun restartLogcat() {
    logcatRestartedCount++
  }

  override fun isLogcatEmpty(): Boolean = lineBatches.isEmpty()

  override suspend fun processMessages(messages: List<LogcatMessage>) {
    messageBatches.add(messages)
  }

  override suspend fun appendMessages(textAccumulator: TextAccumulator, context: Any?) {
    val list: List<String> = textAccumulator.text.trim().split("\n")
    lineBatches.add(list)
  }

  override fun getConnectedDevice() = attachedDevice

  override fun isShowing() = showing

  override fun openLogcatFile(path: Path) {
    TODO("Not yet implemented")
  }

  override fun getBacklogMessages(): List<LogcatMessage> = messageBatches.flatten()

  override suspend fun enterInvisibleMode() {
    messageBatches.clear()
  }

  override fun getSelectedDevice(): Device? = device

  override fun applyFilter(logcatFilter: LogcatFilter?) {
    TODO("Not yet implemented")
  }

  override fun applyLogcatSettings(logcatSettings: AndroidLogcatSettings) {
    TODO("Not yet implemented")
  }

  override fun getTags(): Set<String> = tagSet

  override fun getPackageNames(): Set<String> {
    TODO("Not yet implemented")
  }

  override fun getProcessNames(): Set<String> {
    TODO("Not yet implemented")
  }

  override fun countFilterMatches(filter: LogcatFilter?): Int =
    LogcatMasterFilter(filter).filter(messageBatches.flatten()).size

  override fun foldImmediately() {
    TODO("Not yet implemented")
  }

  override fun isLogcatPaused(): Boolean {
    TODO("Not yet implemented")
  }

  override fun pauseLogcat() {
    TODO("Not yet implemented")
  }

  override fun resumeLogcat() {
    TODO("Not yet implemented")
  }

  override fun getFilter(): String = logcatFilter

  override fun setFilter(filter: String) {
    logcatFilter = filter
  }

  fun appendMessage(message: String) {
    lineBatches.add(listOf(message))
  }

  override fun isSoftWrapEnabled(): Boolean {
    TODO("Not yet implemented")
  }

  override fun setSoftWrapEnabled(state: Boolean) {
    TODO("Not yet implemented")
  }

  override fun dispose() {}
}
