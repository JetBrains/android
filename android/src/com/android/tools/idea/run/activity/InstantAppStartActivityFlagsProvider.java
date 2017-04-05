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
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.instantapp.InstantApps.isPostO;

public class InstantAppStartActivityFlagsProvider implements StartActivityFlagsProvider {

  public InstantAppStartActivityFlagsProvider() {
  }

  @Override
  @NotNull
  public String getFlags(@NotNull IDevice device) {
    // currently no way to add extra flags to instant apps or to get instant apps to wait for debugger.
    return isPostO(device) ? "" : "-n \"com.google.android.instantapps.supervisor/.UrlHandler\"";
  }
}
