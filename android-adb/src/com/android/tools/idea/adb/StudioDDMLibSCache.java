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

import com.android.ddmlib.JdwpProcessor;
import com.android.ddmlib.JdwpTrafficResponse;
import com.android.jdwpscache.SCacheJava;
import com.android.jdwpscache.SCacheLogger;
import java.nio.ByteBuffer;
import org.jetbrains.annotations.NotNull;

public class StudioDDMLibSCache implements JdwpProcessor {

  @NotNull
  private final SCacheJava scache;

  public StudioDDMLibSCache(Boolean enabled, SCacheLogger logger) {
    scache = new SCacheJava(enabled, logger);
  }

  @Override
  @NotNull
  public JdwpTrafficResponse onUpstreamPacket(@NotNull ByteBuffer buffer) {
    return new StudioDDMLibJdwpTrafficResponse(scache.onUpstreamPacket(buffer));
  }

  @Override
  @NotNull
  public JdwpTrafficResponse onDownstreamPacket(@NotNull ByteBuffer buffer) {
    return new StudioDDMLibJdwpTrafficResponse(scache.onDownstreamPacket(buffer));
  }

  @Override
  public void close() {
  }
}
