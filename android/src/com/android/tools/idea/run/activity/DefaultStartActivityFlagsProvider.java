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
package com.android.tools.idea.run.activity;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DefaultStartActivityFlagsProvider implements StartActivityFlagsProvider {

  @Nullable private final AndroidDebugger myAndroidDebugger;
  @Nullable private final AndroidDebuggerState myAndroidDebuggerState;
  private final boolean myWaitForDebugger;
  @NotNull private final String myExtraFlags;

  public DefaultStartActivityFlagsProvider(@Nullable AndroidDebugger androidDebugger,
                                           @Nullable AndroidDebuggerState androidDebuggerState,
                                           boolean waitForDebugger,
                                           @NotNull String extraFlags) {
    myAndroidDebugger = androidDebugger;
    myAndroidDebuggerState = androidDebuggerState;
    myWaitForDebugger = waitForDebugger;
    myExtraFlags = extraFlags;
  }

  @Override
  @NotNull
  public String getFlags(@NotNull IDevice device) {
    List<String> flags = Lists.newLinkedList();
    if (myWaitForDebugger) {
      flags.add("-D");
    }
    if (!myExtraFlags.isEmpty()) {
      flags.add(myExtraFlags);
    }
    if (myWaitForDebugger && myAndroidDebugger != null) {
      String extraOptions = myAndroidDebugger.getAmStartOptions(myAndroidDebuggerState, device.getVersion());
      if (!extraOptions.isEmpty()) {
        flags.add(extraOptions);
      }
    }

    return StringUtil.join(flags, " ");
  }
}
