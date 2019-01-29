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

public class FakeLogService extends LogService {
  @NotNull
  @Override
  public FakeLogger getLogger(@NotNull String category) {
    return new FakeLogger();
  }

  @NotNull
  @Override
  public FakeLogger getLogger(@NotNull Class clazz) {
    return new FakeLogger();
  }

  public static class FakeLogger implements LogService.Logger {
    @Override
    public void error(@NotNull String error) {
      throw new AssertionError(error);
    }

    @Override
    public void error(@NotNull Throwable t) {
      throw new AssertionError(t);
    }

    @Override
    public void warn(@NotNull String warning) {

    }

    @Override
    public void warn(@NotNull Throwable t) {

    }

    @Override
    public void debug(@NotNull String msg, @NotNull Throwable t) {

    }

    @Override
    public void debug(@NotNull String msg) {

    }

    @Override
    public void info(@NotNull String info) {

    }
  }
}
