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

import com.android.annotations.NonNull;
import com.android.ddmlib.JdwpTraffic;
import java.nio.ByteBuffer;
import java.util.List;

public class StudioDDMLibJdwpTraffic implements JdwpTraffic {

  @NonNull
  private final com.android.jdwpscache.JdwpTraffic traffic;

  public StudioDDMLibJdwpTraffic(@NonNull com.android.jdwpscache.JdwpTraffic traffic) {
    this.traffic = traffic;
  }

  @Override
  @NonNull
  public List<ByteBuffer> getToUpstream() {
    return traffic.getToUpstream();
  }

  @Override
  @NonNull
  public List<ByteBuffer> getToDownstream() {
    return traffic.getToDownstream();
  }
}
