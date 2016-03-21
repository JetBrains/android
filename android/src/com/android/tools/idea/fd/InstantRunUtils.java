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

import com.android.ddmlib.IDevice;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InstantRunUtils {
  private static final Key<Boolean> CLEAN_BUILD = Key.create("android.instant.run.clean");
  private static final Key<Boolean> FULL_BUILD = Key.create("android.instant.run.full.build");
  private static final Key<Boolean> APP_RUNNING = Key.create("android.instant.run.app.running");
  private static final Key<IDevice> RESTART_ON_DEVICE = Key.create("android.instant.run.restart.device");
  private static final Key<Boolean> IR_ENABLED = Key.create("android.instant.run.enabled.for.run.config");
  private static final Key<Boolean> RERUN = Key.create("android.instant.run.rerun");
  private static final Key<Boolean> CLEAN_RERUN = Key.create("android.instant.run.clean.rerun");

  public static void setInstantRunEnabled(@NotNull ExecutionEnvironment env, boolean en) {
    env.putCopyableUserData(IR_ENABLED, en);
  }

  public static boolean isInstantRunEnabled(@NotNull ExecutionEnvironment env) {
    return Boolean.TRUE.equals(env.getCopyableUserData(IR_ENABLED));
  }

  public static boolean needsFullBuild(@NotNull ExecutionEnvironment env) {
    return Boolean.TRUE.equals(env.getCopyableUserData(FULL_BUILD));
  }

  public static void setNeedsFullBuild(@NotNull ExecutionEnvironment env, boolean en) {
    env.putCopyableUserData(FULL_BUILD, en);
  }

  public static boolean needsCleanBuild(@NotNull ExecutionEnvironment env) {
    return Boolean.TRUE.equals(env.getCopyableUserData(CLEAN_BUILD));
  }

  public static void setNeedsCleanBuild(@NotNull ExecutionEnvironment env, boolean en) {
    env.putCopyableUserData(CLEAN_BUILD, en);
  }

  public static boolean isAppRunning(@NotNull ExecutionEnvironment env) {
    return Boolean.TRUE.equals(env.getCopyableUserData(APP_RUNNING));
  }

  public static void setAppRunning(@NotNull ExecutionEnvironment env, boolean en) {
    env.putCopyableUserData(APP_RUNNING, en);
  }

  /** Changes user data on the given {@link ExecutionEnvironment} to indicate that the build should be rerun targeting the given device. */
  public static void setRestartSession(@NotNull ExecutionEnvironment env, @NotNull IDevice device) {
    setNeedsFullBuild(env, true);
    setAppRunning(env, false);
    env.putCopyableUserData(RESTART_ON_DEVICE, device);
  }

  @Nullable
  public static IDevice getRestartDevice(@NotNull ExecutionEnvironment env) {
    return env.getCopyableUserData(RESTART_ON_DEVICE);
  }

  public static void setReRun(@NotNull ExecutionEnvironment env, boolean en) {
    env.putCopyableUserData(RERUN, en);
  }

  public static boolean isReRun(@NotNull ExecutionEnvironment env) {
    return Boolean.TRUE.equals(env.getCopyableUserData(RERUN));
  }

  public static void setCleanReRun(@NotNull ExecutionEnvironment env, boolean en) {
    env.putCopyableUserData(CLEAN_RERUN, en);
  }

  public static boolean isCleanReRun(@NotNull ExecutionEnvironment env) {
    return Boolean.TRUE.equals(env.getCopyableUserData(CLEAN_RERUN));
  }
}
