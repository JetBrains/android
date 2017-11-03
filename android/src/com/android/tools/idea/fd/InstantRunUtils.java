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
  private static final Key<Boolean> IR_ENABLED = Key.create("android.instant.run.enabled.for.run.config");
  private static final Key<Boolean> INVOKED_VIA_HOTSWAP_ACTION = Key.create("android.instant.run.invoke.update");

  public static void setInstantRunEnabled(@NotNull ExecutionEnvironment env, boolean en) {
    env.putCopyableUserData(IR_ENABLED, en);
  }

  public static boolean isInstantRunEnabled(@NotNull ExecutionEnvironment env) {
    return Boolean.TRUE.equals(env.getCopyableUserData(IR_ENABLED));
  }

  // Set that this launch was invoked via the hotswap action
  public static void setInvokedViaHotswapAction(@NotNull ExecutionEnvironment env, boolean en) {
    env.putCopyableUserData(INVOKED_VIA_HOTSWAP_ACTION, en);
  }

  public static boolean isInvokedViaHotswapAction(@NotNull ExecutionEnvironment env) {
    return Boolean.TRUE.equals(env.getCopyableUserData(INVOKED_VIA_HOTSWAP_ACTION));
  }
}
