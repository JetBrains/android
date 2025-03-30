/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.adb;

import com.android.ddmlib.JdwpTraffic;
import com.android.ddmlib.JdwpTrafficResponse;
import com.android.jdwpscache.SCacheResponse;
import org.jetbrains.annotations.NotNull;

public class StudioDDMLibJdwpTrafficResponse implements JdwpTrafficResponse {

  @NotNull
  private final SCacheResponse response;

  public StudioDDMLibJdwpTrafficResponse(@NotNull SCacheResponse response) {
    this.response = response;
  }

  @NotNull
  @Override
  public JdwpTraffic getEdict() {
    return new StudioDDMLibJdwpTraffic(response.getEdict());
  }

  @NotNull
  @Override
  public JdwpTraffic getJournal() {
    return new StudioDDMLibJdwpTraffic(response.getJournal());
  }
}
