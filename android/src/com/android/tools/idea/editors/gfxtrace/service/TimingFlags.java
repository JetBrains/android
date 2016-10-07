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
package com.android.tools.idea.editors.gfxtrace.service;

import com.android.tools.rpclib.binary.BitField;
import com.google.common.collect.ImmutableList;

import java.util.Arrays;

public final class TimingFlags extends BitField<TimingFlags> {
  public static final Bit<TimingFlags> TimingCPU = new Bit<TimingFlags>(0, "TimingCPU");
  public static final Bit<TimingFlags> TimingGPU = new Bit<TimingFlags>(1, "TimingGPU");
  public static final Bit<TimingFlags> TimingPerCommand = new Bit<TimingFlags>(2, "TimingPerCommand");
  public static final Bit<TimingFlags> TimingPerDrawCall = new Bit<TimingFlags>(4, "TimingPerDrawCall");
  public static final Bit<TimingFlags> TimingPerFrame = new Bit<TimingFlags>(8, "TimingPerFrame");

  private static final ImmutableList<Bit<TimingFlags>> ALL_BITS = ImmutableList.<Bit<TimingFlags>>builder()
    .add(TimingCPU)
    .add(TimingGPU)
    .add(TimingPerCommand)
    .add(TimingPerDrawCall)
    .add(TimingPerFrame)
    .build();

  private TimingFlags(int value) {
    super(value);
  }

  private TimingFlags(Iterable<Bit<TimingFlags>> bits) {
    super(bits);
  }

  public static TimingFlags of(Bit<TimingFlags>... bits) {
    return new TimingFlags(Arrays.asList(bits));
  }

  public int getNumber() {
    return bits;
  }

  public static TimingFlags valueOf(int value) {
    return new TimingFlags(value);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Bit<TimingFlags> bit : ALL_BITS) {
      if (isSet(bit)) {
        sb.append('|').append(bit.name);
      }
    }
    return (sb.length() == 0) ? "<none>" : sb.substring(1);
  }
}
