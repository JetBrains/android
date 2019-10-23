/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers.network;

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profilers.network.httpdata.HttpData;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A model class which allows querying captured network data requests.
 */
public interface NetworkConnectionsModel {
  /**
   * This method will be invoked in each animation cycle of {@link NetworkCaptureView}.
   *
   * @param timeCurrentRangeUs the current visible range in {NetworkCaptureView}
   * @return List of visible {@link HttpData} in the {@code timeCurrentRangeUs} range, in other
   * words list of {@link HttpData}'s [getRequestStartTimeUs()..getConnectionEndTimeUs()] (all
   * inclusive) intersects with {@code timeCurrentRangeUs}'s [getMin()..getMax()] (all inclusive).
   */
  @NotNull
  List<HttpData> getData(@NotNull Range timeCurrentRangeUs);

  /**
   * Returns the byte string associated with the given {@code id}. This is used for network request/response payloads.
   * <p>
   * If there is no such content associated with the data, or if it can't be fetched for any
   * reason, {@link ByteString#EMPTY} will be returned.
   */
  @NotNull
  ByteString requestBytes(@NotNull String id);
}
