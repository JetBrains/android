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
package com.android.tools.idea.transport.poller;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates most of the polling functionality that Transport Pipeline subscribers would need to implement
 * to listen for updates and Events coming in from the pipeline
 */
public final class TransportEventPoller {
  @NotNull private TransportServiceGrpc.TransportServiceBlockingStub myTransportClient;
  @NotNull private List<TransportEventListener> myEventListeners;
  @NotNull private ConcurrentMap<TransportEventListener, Long> myListenersToLastTimestamp;

  TransportEventPoller(@NotNull TransportServiceGrpc.TransportServiceBlockingStub transportClient) {
    myTransportClient = transportClient;
    myEventListeners = new CopyOnWriteArrayList<>(); // Used to preserve insertion order
    myListenersToLastTimestamp = new ConcurrentHashMap<>();
  }

  /**
   * Adds a listener to the list to poll for and be notified of changes. Listeners are polled in insertion order.
   */
  public void registerListener(@NotNull TransportEventListener listener) {
    myEventListeners.add(listener);
    myListenersToLastTimestamp.put(listener, Long.MIN_VALUE);
  }

  /**
   * Removes a listener from the list
   */
  public void unregisterListener(@NotNull TransportEventListener listener) {
    myEventListeners.remove(listener);
    myListenersToLastTimestamp.remove(listener);
  }

  public void poll() {
    // Poll for each listener
    for (TransportEventListener eventListener : myEventListeners) {
      // Use start/end time if available
      long startTimestamp;
      long endTimestamp;
      if (eventListener.getStartTime() != null && eventListener.getEndTime() != null) {
        startTimestamp = eventListener.getStartTime().get();
        endTimestamp = eventListener.getEndTime().get();
      }
      else {
        startTimestamp = myListenersToLastTimestamp.get(eventListener);
        endTimestamp = Long.MAX_VALUE;
      }

      Transport.GetEventGroupsRequest.Builder builder = Transport.GetEventGroupsRequest.newBuilder()
        .setKind(eventListener.getEventKind())
        .setFromTimestamp(startTimestamp)
        .setToTimestamp(endTimestamp);
      if (eventListener.getStreamId() != null)
        builder.setStreamId(eventListener.getStreamId().get());
      if (eventListener.getProcessId() != null)
        builder.setPid(eventListener.getProcessId().get());

      Transport.GetEventGroupsRequest request = builder.build();

      // Order by timestamp
      Transport.GetEventGroupsResponse response = myTransportClient.getEventGroups(request);
      if (!response.equals(Transport.GetEventGroupsResponse.getDefaultInstance())) {
        List<Common.Event> events = new ArrayList<>();
        response.getGroupsList().forEach(group -> events.addAll(group.getEventsList()));
        Collections.sort(events, Comparator.comparingLong(Common.Event::getTimestamp));
        if (!events.isEmpty()) {
          events.forEach(event -> {
            if (event.getTimestamp() >= startTimestamp && eventListener.getFilter().test(event)) {
              // Dispatch events to listeners
              eventListener.getExecutor().execute(() -> eventListener.getCallback().accept(event));
            }
          });
          // Update last timestamp per listener
          myListenersToLastTimestamp.put(eventListener,
                                         Math.max(startTimestamp, events.get(events.size() - 1).getTimestamp() + 1));
        }
      }
    }
  }
}
