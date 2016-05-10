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
package com.android.tools.idea.run;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidRunConfigContext {
  public static final Key<AndroidRunConfigContext> KEY = Key.create("android.run.config.context");

  // state common to all launch configuration types
  private DeviceFutures myTargetDevices;

  // instant run specific context
  private boolean isSameExecutorAsPreviousSession;
  private boolean myCleanRerun;

  @Nullable
  public DeviceFutures getTargetDevices() {
    return myTargetDevices;
  }

  public void setTargetDevices(@NotNull DeviceFutures targetDevices) {
    myTargetDevices = targetDevices;
  }

  public boolean isSameExecutorAsPreviousSession() {
    return isSameExecutorAsPreviousSession;
  }

  public void setSameExecutorAsPreviousSession(boolean sameExecutorAsPreviousSession) {
    isSameExecutorAsPreviousSession = sameExecutorAsPreviousSession;
  }

  public boolean isCleanRerun() {
    return myCleanRerun;
  }

  public void setCleanRerun(boolean cleanRerun) {
    myCleanRerun = cleanRerun;
  }
}
