/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.adb;

import com.android.jdwpscache.SCacheLogger;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

public class StudioSCacheLogger implements SCacheLogger {

  private final Logger logger = Logger.getInstance("scache");

  @Override
  public void info(@NotNull String message) {
      logger.info(message);
  }

  @Override
  public void warn(@NotNull String message) {
     logger.warn(message);
  }

  @Override
  public void error(@NotNull String message) {
      logger.error(message);
  }

  @Override
  public void error(@NotNull String message, @NotNull Throwable t) {
      logger.error(message, t);
  }
}
