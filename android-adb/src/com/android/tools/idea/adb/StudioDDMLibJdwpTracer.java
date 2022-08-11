/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.ddmlib.DDMLibJdwpTracer;

import com.android.jdwptracer.JDWPTracer;
import java.nio.ByteBuffer;

public class StudioDDMLibJdwpTracer implements DDMLibJdwpTracer {

    private final JDWPTracer tracer;

    StudioDDMLibJdwpTracer(boolean enabled) {
       this.tracer = new JDWPTracer(enabled);
    }

    @Override
    public void onEvent(@NonNull String event) {
      tracer.addEvent(event);
    }

    @Override
    public void onPacket(@NonNull ByteBuffer packet) {
      tracer.addPacket(packet);
    }
}
