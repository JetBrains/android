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
package com.android.tools.datastore;

import org.jetbrains.annotations.NotNull;

public abstract class LogService {
  @NotNull
  public abstract Logger getLogger(@NotNull String category);

  @NotNull
  public abstract Logger getLogger(@NotNull Class clazz);

  public interface Logger {
    void error(@NotNull String error);

    void error(@NotNull Throwable t);

    void warn(@NotNull String warning);

    void warn(@NotNull Throwable t);

    void debug(@NotNull String msg, @NotNull Throwable t);

    void debug(@NotNull String msg);

    void info(@NotNull String info);
  }
}
