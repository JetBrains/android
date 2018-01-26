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
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.*;

import java.util.ArrayList;
import java.util.List;

public class NativeSymbolsPoller extends PollRunner {
  private final MemoryLiveAllocationTable myLiveAllocationTable;
  private final Common.Session mySession;
  private final int MAX_SYMBOLS_PER_REQUEST = 1000;

  public NativeSymbolsPoller(Common.Session session,
                             MemoryLiveAllocationTable liveAllocationTable) {
    super(POLLING_DELAY_NS);
    mySession = session;
    myLiveAllocationTable = liveAllocationTable;
  }

  private NativeCallStack.NativeFrame symbolize(NativeCallStack.NativeFrame frame) {
    long address = frame.getModuleOffset();
    //TODO: call real symbolizer here.
    return frame.toBuilder().setSymbolName(String.format("class::func_%x()", address)).build();
  }

  @Override
  public void poll() {
    List<NativeCallStack.NativeFrame> framesToSymbolize =
      myLiveAllocationTable.queryNotsymbolizedNativeFrames(mySession, MAX_SYMBOLS_PER_REQUEST);
    ArrayList<NativeCallStack.NativeFrame> symbolizedFrames = new ArrayList<>(framesToSymbolize.size());

    for (NativeCallStack.NativeFrame frame : framesToSymbolize) {
      symbolizedFrames.add(symbolize(frame));
    }

    if (!symbolizedFrames.isEmpty()) {
      myLiveAllocationTable.updateSymbolizedNativeFrames(mySession, symbolizedFrames);
    }
  }
}
