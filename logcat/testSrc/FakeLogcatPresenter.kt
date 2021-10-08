import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.messages.TextAccumulator

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
  var reloadedMessages = 0
  val messageBatches = mutableListOf<List<String>>()

  override fun reloadMessages() {
    reloadedMessages++
  }

  override suspend fun processMessages(messages: List<LogCatMessage>) {
    TODO("Not yet implemented")
  }

  override suspend fun appendMessages(textAccumulator: TextAccumulator) {
    messageBatches.add(textAccumulator.text.trim().split("\n"))
  }

  override fun dispose() {}
}