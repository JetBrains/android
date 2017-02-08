/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class FakeIdeProfilerServices implements IdeProfilerServices {
  /**
   * Callback to be run after the executor calls its execute() method.
   */
  @Nullable
  Runnable myOnExecute;

  /**
   * The pool executor runs code in a separate thread. Sometimes is useful to check the state of the profilers
   * just before calling pool executor's execute method (e.g. verifying Stage's transient status before making a gRPC call).
   */
  @Nullable
  Runnable myPrePoolExecute;

  @NotNull
  @Override
  public Executor getMainExecutor() {
    return (runnable) -> {
      runnable.run();
      if (myOnExecute != null) {
        myOnExecute.run();
      }
    };
  }

  @NotNull
  @Override
  public Executor getPoolExecutor() {
    return (runnable) -> {
      if (myPrePoolExecute != null) {
        myPrePoolExecute.run();
      }
      runnable.run();
    };
  }

  @Override
  public void saveFile(@NotNull File file, @NotNull Consumer<FileOutputStream> fileOutputStreamConsumer, @Nullable Runnable postRunnable) {
  }

  public void setOnExecute(@Nullable Runnable onExecute) {
    myOnExecute = onExecute;
  }

  public void setPrePoolExecutor(@Nullable Runnable prePoolExecute) {
    myPrePoolExecute = prePoolExecute;
  }
}
