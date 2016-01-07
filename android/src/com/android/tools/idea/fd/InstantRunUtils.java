/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public class InstantRunUtils {
  private static final Key<Boolean> INCREMENTAL_BUILD = Key.create("android.instant.run.incremental");
  private static final Key<Boolean> APP_RUNNING = Key.create("android.instant.run.app.running");

  public static boolean isIncrementalBuild(@NotNull ExecutionEnvironment env) {
    return Boolean.TRUE.equals(env.getCopyableUserData(INCREMENTAL_BUILD));
  }

  public static void setIncrementalBuild(@NotNull ExecutionEnvironment env, boolean b) {
    env.putCopyableUserData(INCREMENTAL_BUILD, Boolean.TRUE);
  }

  public static boolean isAppRunning(@NotNull ExecutionEnvironment env) {
    return Boolean.TRUE.equals(env.getCopyableUserData(APP_RUNNING));
  }

  public static void setAppRunning(@NotNull ExecutionEnvironment env, boolean en) {
    env.putCopyableUserData(APP_RUNNING, en);
  }
}
