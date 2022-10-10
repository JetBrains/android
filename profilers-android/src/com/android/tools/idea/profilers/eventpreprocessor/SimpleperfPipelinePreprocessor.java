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
package com.android.tools.idea.profilers.eventpreprocessor;

import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.TransportBytesPreprocessor;
import com.android.tools.idea.transport.TransportEventPreprocessor;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.cpu.TracePreProcessor;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Class to preprocess simple perf traces before they are inserted into the datastore.
 */
public class SimpleperfPipelinePreprocessor implements TransportEventPreprocessor, TransportBytesPreprocessor {

  private final Map<String, List<String>> myTraceIdsToSymbols = new HashMap<>();
  private final TracePreProcessor myPreProcessor;

  public SimpleperfPipelinePreprocessor(@NotNull TracePreProcessor preProcessor) {
    myPreProcessor = preProcessor;
  }

  /**
   * Only run the preprocessor on CpuTraceEvents that are of type simple perf.
   */
  @Override
  public boolean shouldPreprocess(Common.Event event) {
    return event.hasCpuTrace() &&
           event.getCpuTrace().hasTraceStarted() &&
           event.getCpuTrace().getTraceStarted().getTraceInfo().getConfiguration().getUserOptions().getTraceType() ==
           Trace.UserOptions.TraceType.SIMPLEPERF;
  }

  /**
   * The preprocessor does nothing but store the trace id. The trace id is used to determine which byte request we need to run on.
   */
  @Override
  @NotNull
  public Iterable<Common.Event> preprocessEvent(Common.Event event) {
    myTraceIdsToSymbols.putIfAbsent(String.valueOf(event.getCpuTrace().getTraceStarted().getTraceInfo().getTraceId()),
                                    event.getCpuTrace().getTraceStarted().getTraceInfo().getConfiguration().getSymbolDirsList());
    return Collections.emptyList();
  }

  @Override
  public boolean shouldPreprocess(Transport.BytesRequest request) {
    return myTraceIdsToSymbols.containsKey(request.getId());
  }

  @Override
  @NotNull
  public ByteString preprocessBytes(String id, ByteString data) {
    assert myTraceIdsToSymbols.containsKey(id); // Was "preprocessEvent / shouldPreprocess" called before calling this?
    return myPreProcessor.preProcessTrace(data, myTraceIdsToSymbols.remove(id));
  }
}
