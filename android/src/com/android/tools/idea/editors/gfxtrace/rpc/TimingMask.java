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
*
* THIS WILL BE REMOVED ONCE THE CODE GENERATOR IS INTEGRATED INTO THE BUILD.
*/
package com.android.tools.idea.editors.gfxtrace.rpc;

import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public enum TimingMask {
  TimingPerCommand(1),
  TimingPerDrawCall(2),
  TimingPerFrame(4);

  final int myValue;

  TimingMask(int value) {
    myValue = value;
  }

  public static TimingMask decode(@NotNull Decoder d) throws IOException {
    int id = d.int32();
    switch (id) {
      case 1:
        return TimingPerCommand;
      case 2:
        return TimingPerDrawCall;
      case 4:
        return TimingPerFrame;
      default:
        throw new RuntimeException("Unknown TimingMask " + id);
    }
  }

  public void encode(@NotNull Encoder e) throws IOException {
    e.int32(myValue);
  }
}