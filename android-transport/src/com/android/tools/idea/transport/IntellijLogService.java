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
package com.android.tools.idea.transport;

import com.android.annotations.NonNull;
import com.android.tools.datastore.LogService;

public class IntellijLogService extends LogService {
  @NonNull
  @Override
  public LogService.Logger getLogger(@NonNull String category) {
    return new Logger(com.intellij.openapi.diagnostic.Logger.getInstance(category));
  }

  @NonNull
  @Override
  public Logger getLogger(@NonNull Class clazz) {
    return new Logger(com.intellij.openapi.diagnostic.Logger.getInstance(clazz));
  }

  public static class Logger implements LogService.Logger {
    @NonNull private final com.intellij.openapi.diagnostic.Logger myLogger;

    private Logger(@NonNull com.intellij.openapi.diagnostic.Logger logger) {
      myLogger = logger;
    }

    @Override
    public void error(@NonNull String error) {
      myLogger.error(error);
    }

    @Override
    public void error(@NonNull Throwable t) {
      myLogger.error(t);
    }

    @Override
    public void warn(@NonNull String warning) {
      myLogger.error(warning);
    }

    @Override
    public void warn(@NonNull Throwable t) {
      myLogger.error(t);
    }

    @Override
    public void debug(@NonNull String msg, @NonNull Throwable t) {
      myLogger.debug(msg, t);
    }

    @Override
    public void debug(@NonNull String msg) {
      myLogger.debug(msg);
    }

    @Override
    public void info(@NonNull String info) {
      myLogger.info(info);
    }
  }
}
