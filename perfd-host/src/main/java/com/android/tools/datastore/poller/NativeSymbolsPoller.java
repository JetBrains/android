/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.datastore.poller;

import com.android.tools.datastore.database.MemoryLiveAllocationTable;
import com.android.tools.datastore.database.ProfilerTable;
import com.android.tools.nativeSymbolizer.NativeSymbolizer;
import com.android.tools.nativeSymbolizer.Symbol;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.Profiler;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NativeSymbolsPoller extends PollRunner {
  @NotNull
  private final MemoryLiveAllocationTable myLiveAllocationTable;
  @NotNull
  private final ProfilerTable myProfilerTable;
  @NotNull
  private final Common.Session mySession;
  @NotNull
  private final NativeSymbolizer mySymbolizer;
  private final int MAX_SYMBOLS_PER_REQUEST = 1000;

  @Nullable
  private Common.Process myProcess = null;

  public NativeSymbolsPoller(@NotNull Common.Session session,
                             @NotNull MemoryLiveAllocationTable liveAllocationTable,
                             @NotNull ProfilerTable profilerTable,
                             @NotNull NativeSymbolizer symbolizer) {
    super(POLLING_DELAY_NS);
    mySession = session;
    myLiveAllocationTable = liveAllocationTable;
    myProfilerTable = profilerTable;
    mySymbolizer = symbolizer;
  }

  @Override
  public void poll() {
    if (mySymbolizer == null)
      return;

    if (myProcess == null) {
      myProcess = findProcess();
      if (myProcess == null) {
        // Can't find a process in the database. Nothing to do.
        return;
      }
    }

    List<NativeCallStack.NativeFrame> framesToSymbolize =
      myLiveAllocationTable.queryNotsymbolizedNativeFrames(mySession, MAX_SYMBOLS_PER_REQUEST);
    ArrayList<NativeCallStack.NativeFrame> symbolizedFrames = new ArrayList<>(framesToSymbolize.size());

    for (NativeCallStack.NativeFrame frame : framesToSymbolize) {
      NativeCallStack.NativeFrame symbolizedFrame = symbolize(frame);
      if (symbolizedFrame != null) {
        symbolizedFrames.add(symbolizedFrame);
      }
    }

    if (!symbolizedFrames.isEmpty()) {
      myLiveAllocationTable.updateSymbolizedNativeFrames(mySession, symbolizedFrames);
    }
  }

  @Nullable
  private Common.Process findProcess() {
    Profiler.GetProcessesRequest request = Profiler.GetProcessesRequest.newBuilder().setDeviceId(mySession.getDeviceId()).build();
    Profiler.GetProcessesResponse resonse = myProfilerTable.getProcesses(request);
    for (Common.Process process : resonse.getProcessList()) {
      if (process.getPid() == mySession.getPid()) {
        return  process;
      }
    }
    return null;
  }

  @NotNull
  private NativeCallStack.NativeFrame symbolize(NativeCallStack.NativeFrame frame) {
    long offset = frame.getModuleOffset();
    Symbol symbol = null;
    try {
      symbol = mySymbolizer.symbolize(myProcess.getAbiCpuArch(), frame.getModuleName(), offset);
    } catch (IOException | RuntimeException e) {
      getLogger().error(e);
    }
    if (symbol == null) {
      String unfoundSymbolName = String.format("unknown_symbol_at_0x%x()", offset);
      return frame.toBuilder().setSymbolName(unfoundSymbolName).build();
    }
    return frame.toBuilder().setSymbolName(symbol.getName())
      .setModuleName(symbol.getModule())
      .setFileName(symbol.getSourceFile())
      .setLineNumber(symbol.getLineNumber()).build();
  }

  @NotNull
  private final static Logger getLogger() {
    return Logger.getInstance(NativeSymbolsPoller.class.getCanonicalName());
  }
}
