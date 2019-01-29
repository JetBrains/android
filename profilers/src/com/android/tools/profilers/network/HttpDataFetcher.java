/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.network.httpdata.HttpData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A class which handles querying for a list of {@link HttpData} requests within a specified range.
 * When the range changes, the list will automatically be updated, and this class will notify any
 * listeners.
 */
public final class HttpDataFetcher {
  // myAspectObserver cannot be local to prevent early GC
  @SuppressWarnings("FieldCanBeLocal") private final AspectObserver myAspectObserver = new AspectObserver();

  @NotNull private final NetworkConnectionsModel myConnectionsModel;
  @NotNull private final Range myRange;
  @NotNull private final List<Listener> myListeners = new ArrayList<>();

  /**
   * The last list of requests polled from the user's device. Initialized to {@code null} to
   * distinguish that case from the case where a range returns no requests.
   */
  @Nullable private List<HttpData> myDataList;

  public HttpDataFetcher(@NotNull NetworkConnectionsModel connectionsModel, @NotNull Range range) {
    myConnectionsModel = connectionsModel;
    myRange = range;

    myRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::handleRangeUpdated);
    handleRangeUpdated();
  }

  public void addListener(@NotNull Listener listener) {
    myListeners.add(listener);
    if (myDataList != null) {
      fireListeners(myDataList);
    }
  }

  private void handleRangeUpdated() {
    List<HttpData> dataList = !myRange.isEmpty() ? myConnectionsModel.getData(myRange) : new ArrayList<>();
    if (myDataList != null && myDataList.equals(dataList)) {
      return;
    }

    myDataList = dataList;
    fireListeners(myDataList);
  }

  private void fireListeners(@NotNull List<HttpData> dataList) {
    for (Listener l : myListeners) {
      l.onUpdated(dataList);
    }
  }

  public interface Listener {
    void onUpdated(@NotNull List<HttpData> httpDataList);
  }
}