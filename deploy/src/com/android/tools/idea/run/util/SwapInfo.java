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
package com.android.tools.idea.run.util;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SwapInfo {
  public static final Key<SwapInfo> SWAP_INFO_KEY = Key.create("android.deploy.SwapInfo");

  @NotNull private final SwapType myType;
  @Nullable private final ProcessHandler myHandler;

  public enum SwapType {
    APPLY_CHANGES,
    APPLY_CODE_CHANGES
  }

  /**
   * @param existingHandler An already-attached {@link ProcessHandler} that is responsible for the {@link com.android.ddmlib.Client}
   *                        that is being swapped into. Or {@code null} if it is running on the device but not monitored by Android Studio.
   */
  public SwapInfo(@NotNull SwapType swapType, @Nullable ProcessHandler existingHandler) {
    myType = swapType;
    myHandler = existingHandler;
  }

  @NotNull
  public SwapType getType() {
    return myType;
  }

  @Nullable
  public ProcessHandler getHandler() {
    return myHandler;
  }
}
