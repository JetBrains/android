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
package com.android.tools.idea.profilers.stacktrace;

import static com.android.tools.idea.profilers.stacktrace.IntelliJNativeFrameSymbolizer.PREVIOUS_INSTRUCTION_OFFSET;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.nativeSymbolizer.NativeSymbolizer;
import com.android.tools.nativeSymbolizer.Symbol;
import com.android.tools.profiler.proto.Memory.NativeCallStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

public class IntelliJNativeFrameSymbolizerTest {

  @Test
  public void testSymbolize() {
    IntelliJNativeFrameSymbolizer frameSymbolizer = new IntelliJNativeFrameSymbolizer(new FakeNativeSymbolizer());

    // verify unfound symbols
    NativeCallStack.NativeFrame unsymbolizedFrame = frameSymbolizer
      .symbolize("arm64", NativeCallStack.NativeFrame.newBuilder().setModuleName("test").setModuleOffset(100).build());
    assertThat(unsymbolizedFrame.getSymbolName()).isEqualTo(String.format("0x%x", 100 - 1));

    // verify found symbols
    NativeCallStack.NativeFrame.Builder frameToSymbolize1 =
      NativeCallStack.NativeFrame.newBuilder().setModuleName("test2").setModuleOffset(100);
    NativeCallStack.NativeFrame symbolizedFrame1 = frameSymbolizer.symbolize("arm", frameToSymbolize1.build());
    NativeCallStack.NativeFrame expectedSymbolizedFrame1 = frameToSymbolize1
      .setSymbolName("arm_frame")
      .setModuleName("test2_symbolized")
      .setFileName("symbols.java")
      .setLineNumber(1000 + 100 + PREVIOUS_INSTRUCTION_OFFSET).build();
    assertThat(symbolizedFrame1).isEqualTo(expectedSymbolizedFrame1);

    NativeCallStack.NativeFrame.Builder frameToSymbolize2 =
      NativeCallStack.NativeFrame.newBuilder().setModuleName("test3").setModuleOffset(200);
    NativeCallStack.NativeFrame symbolizedFrame2 = frameSymbolizer.symbolize("x86", frameToSymbolize2.build());
    NativeCallStack.NativeFrame expectedSymbolizedFrame2 = frameToSymbolize2
      .setSymbolName("x86_frame")
      .setModuleName("test3_symbolized")
      .setFileName("symbols.java")
      .setLineNumber(1000 + 200 + PREVIOUS_INSTRUCTION_OFFSET).build();
    assertThat(symbolizedFrame2).isEqualTo(expectedSymbolizedFrame2);
  }

  private static class FakeNativeSymbolizer implements NativeSymbolizer {
    @Nullable
    @Override
    public Symbol symbolize(@NotNull String abiArch, @NotNull String module, long offset) {
      switch (abiArch) {
        case "arm":
        case "x86":
          return new Symbol(abiArch + "_frame", module + "_symbolized", "symbols.java", 1000 + (int)offset);
        default:
          return null;
      }
    }

    @Override
    public void stop() {
    }
  }
}