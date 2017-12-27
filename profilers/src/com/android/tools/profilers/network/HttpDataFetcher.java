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
import com.android.tools.adtui.model.updater.Updatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An {@link Updatable} which will regularly run and, on an interval, poll for an up-to-date list
 * of {@link HttpData} requests within a specified range. Once we see that all requests have been
 * completed, the update loop will stop making the expensive polling calls.
 */
public final class HttpDataFetcher implements Updatable {
  private static final long FETCH_FREQUENCY = TimeUnit.MILLISECONDS.toNanos(250);

  // myAspectObserver cannot be local to prevent early GC
  @SuppressWarnings("FieldCanBeLocal") private final AspectObserver myAspectObserver = new AspectObserver();

  @NotNull private final NetworkConnectionsModel myConnectionsModel;
  @NotNull private final Range myRange;
  @NotNull private final List<Listener> myListeners = new ArrayList<>();

  /**
   * The last list of requests polled from the user's device. If {@code null}, it means the update
   * loop should always poll immediately for a new list.
   */
  @Nullable private List<HttpData> myDataList;

  /**
   * Time accumulated since the last poll.
   */
  private long myAccumNs;

  public HttpDataFetcher(@NotNull NetworkConnectionsModel connectionsModel, @NotNull Range range) {
    myConnectionsModel = connectionsModel;
    myRange = range;

    myRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::pollImmediately);
    pollImmediately();
  }

  public void addListener(@NotNull Listener listener) {
    myListeners.add(listener);
    if (myDataList != null) {
      fireListeners(myDataList);
    }
  }

  @Override
  public void update(long elapsedNs) {
    myAccumNs += elapsedNs;
    // If data list is not set yet, we always want to fetch regardless of accumulated time
    if (myAccumNs < FETCH_FREQUENCY && myDataList != null) {
      return;
    }

    myAccumNs = 0;
    if (myDataList == null || stillDownloading(myDataList)) {
      if (!myRange.isEmpty()) {
        myDataList = myConnectionsModel.getData(myRange);
      }
      else {
        myDataList = new ArrayList<>();
      }
      fireListeners(myDataList);
    }
  }

  private void pollImmediately() {
    myDataList = null;
    update(0);
  }

  private void fireListeners(@NotNull List<HttpData> dataList) {
    for (Listener l : myListeners) {
      l.onUpdated(dataList);
    }
  }

  private boolean stillDownloading(@NotNull List<HttpData> dataList) {
    for (HttpData data : dataList) {
      if (data.getEndTimeUs() == 0) {
        return true;
      }
    }
    return false;
  }

  public interface Listener {
    void onUpdated(@NotNull List<HttpData> httpDataList);
  }
}