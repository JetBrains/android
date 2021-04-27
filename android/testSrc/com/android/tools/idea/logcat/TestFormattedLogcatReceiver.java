/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.logcat;

import com.android.ddmlib.logcat.LogCatMessage;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

final class TestFormattedLogcatReceiver implements AndroidLogcatService.LogcatListener {
  private final List<LogCatMessage> myLogCatMessages = new ArrayList<>();

  @Override
  public void onLogLineReceived(@NotNull LogCatMessage line) {
    myLogCatMessages.add(line);
  }

  @Override
  public void onCleared() {
    myLogCatMessages.clear();
  }

  public List<LogCatMessage> getLogCatMessages() {
    return myLogCatMessages;
  }
}