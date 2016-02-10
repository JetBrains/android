/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.fd;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.ILogger;
import com.intellij.openapi.diagnostic.Logger;

public class LogWrapper implements ILogger {
  private final Logger myLog;

  public LogWrapper(Logger log) {
    myLog = log;
  }

  @Override
  public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
    if (msgFormat == null) {
      if (t != null) {
        myLog.error(t);
      }
      return;
    }

    myLog.error(String.format(msgFormat, args), t);
  }

  @Override
  public void warning(@NonNull String msgFormat, Object... args) {
    myLog.warn(String.format(msgFormat, args));
  }

  @Override
  public void info(@NonNull String msgFormat, Object... args) {
    myLog.info(String.format(msgFormat, args));
  }

  @Override
  public void verbose(@NonNull String msgFormat, Object... args) {
    myLog.debug(String.format(msgFormat, args));
  }
}
