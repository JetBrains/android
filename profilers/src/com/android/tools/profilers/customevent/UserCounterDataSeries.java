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
package com.android.tools.profilers.customevent;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.profilers.StudioProfilers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class UserCounterDataSeries implements DataSeries<Long> {

  @NotNull private final TransportServiceGrpc.TransportServiceBlockingStub myClient;
  @NotNull private final StudioProfilers myStudioProfilers;


  public UserCounterDataSeries(@NotNull TransportServiceGrpc.TransportServiceBlockingStub client,
                               @NotNull StudioProfilers profilers) {
    myClient = client;
    myStudioProfilers = profilers;
  }

  @Override
  public List<SeriesData<Long>> getDataForRange(Range range) {
    if (range.isEmpty()) {
      return new ArrayList<>();
    }

    //TODO (b/140521019) Create series for displaying event count.
    return Arrays.asList(new SeriesData<>((long)range.getMin(), 0l),
                         new SeriesData<>((long)range.getMax(), 0l));
  }
}
