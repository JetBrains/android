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

import com.android.tools.nativeSymbolizer.NativeSymbolizer;
import com.android.tools.nativeSymbolizer.Symbol;
import com.android.tools.profiler.proto.Memory.NativeCallStack;
import com.android.tools.profilers.stacktrace.NativeFrameSymbolizer;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

/**
 * Wrapper for {@link NativeSymbolizer} to return a NativeCallStack.NativeFrame instead of the Symbol class which profilers don't have a
 * dependency on.
 */
public class IntelliJNativeFrameSymbolizer implements NativeFrameSymbolizer {

  static final int PREVIOUS_INSTRUCTION_OFFSET = -1;

  private static Logger getLogger() {
    return Logger.getInstance(IntelliJNativeFrameSymbolizer.class);
  }

  @NotNull private final NativeSymbolizer mySymbolizer;

  public IntelliJNativeFrameSymbolizer(@NotNull NativeSymbolizer symbolizer) {
    mySymbolizer = symbolizer;
  }

  @NotNull
  @Override
  public NativeCallStack.NativeFrame symbolize(String abi, NativeCallStack.NativeFrame unsymbolizedFrame) {
    Symbol symbol = null;
    long instructionOffset = getOffsetOfPreviousInstruction(unsymbolizedFrame.getModuleOffset());
    try {
      symbol = mySymbolizer.symbolize(abi, unsymbolizedFrame.getModuleName(), instructionOffset);
    }
    catch (IOException | RuntimeException e) {
      getLogger().warn(e);
    }

    NativeCallStack.NativeFrame.Builder builder = unsymbolizedFrame.toBuilder();
    if (symbol == null) {
      String unfoundSymbolName = String.format("0x%x", instructionOffset);
      builder.setSymbolName(unfoundSymbolName);
    }
    else {
      builder.setSymbolName(symbol.getName())
        .setModuleName(symbol.getModule())
        .setFileName(symbol.getSourceFile())
        .setLineNumber(symbol.getLineNumber());
    }
    return builder.build();
  }

  @Override
  public void stop() {
    // When stop is called we call stop on the native symbolizer indicating it is optimal to shutdown the process.
    mySymbolizer.stop();
  }

  private long getOffsetOfPreviousInstruction(long offset) {
    // In non-bottom frames native backtrace contains addresses where the execution will
    // continue after a function call. After symbolization such addresses often resolved
    // to the source line immediately following the function call.
    // That's why offset needs to be adjusted (by -1) to actually get into a source range
    // of the function call. The bottom stack frame belong to perfa itself and never being
    // sent to the data store, that's why there is no need for handling of this special case.
    return offset + PREVIOUS_INSTRUCTION_OFFSET;
  }
}
