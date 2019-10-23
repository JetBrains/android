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
package com.android.tools.profilers;

import static com.android.tools.profiler.proto.Common.Event.EventGroupIds.NETWORK_RX_VALUE;
import static com.android.tools.profiler.proto.Common.Event.EventGroupIds.NETWORK_TX_VALUE;

import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Network;
import com.android.tools.profiler.proto.Transport.EventGroup;
import com.android.tools.profilers.network.httpdata.HttpData;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * Profiler test data holder class.
 */
public final class ProfilersTestData {

  // Un-initializable.
  private ProfilersTestData() {
  }

  public static final Common.Session SESSION_DATA = Common.Session.newBuilder()
    .setSessionId(4321)
    .setStreamId(1234)
    .setPid(5678)
    .build();

  public static final Common.AgentData DEFAULT_AGENT_ATTACHED_RESPONSE =
    Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build();

  public static final Common.AgentData DEFAULT_AGENT_DETACHED_RESPONSE =
    Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.UNATTACHABLE).build();

  @NotNull
  public static Common.Event.Builder generateNetworkTxEvent(long timestampUs, int throughput) {
    return Common.Event.newBuilder()
      .setTimestamp(TimeUnit.MICROSECONDS.toNanos(timestampUs))
      .setKind(Common.Event.Kind.NETWORK_SPEED)
      .setGroupId(NETWORK_TX_VALUE)
      .setNetworkSpeed(Network.NetworkSpeedData.newBuilder().setThroughput(throughput));
  }

  @NotNull
  public static Common.Event.Builder generateNetworkRxEvent(long timestampUs, int throughput) {
    return Common.Event.newBuilder()
      .setTimestamp(TimeUnit.MICROSECONDS.toNanos(timestampUs))
      .setKind(Common.Event.Kind.NETWORK_SPEED)
      .setGroupId(NETWORK_RX_VALUE)
      .setNetworkSpeed(Network.NetworkSpeedData.newBuilder().setThroughput(throughput));
  }

  @NotNull
  public static EventGroup.Builder generateNetworkConnectionData(@NotNull HttpData data) {
    long connectionId = data.getId();
    EventGroup.Builder builder = EventGroup.newBuilder().setGroupId(connectionId);
    long requestStartNs = TimeUnit.MICROSECONDS.toNanos(data.getRequestStartTimeUs());
    long requestCompleteNs = TimeUnit.MICROSECONDS.toNanos(data.getRequestCompleteTimeUs());
    long responseStartNs = TimeUnit.MICROSECONDS.toNanos(data.getResponseStartTimeUs());
    long responseCompleteNs = TimeUnit.MICROSECONDS.toNanos(data.getResponseCompleteTimeUs());
    long connectionEndNs = TimeUnit.MICROSECONDS.toNanos(data.getConnectionEndTimeUs());
    if (requestStartNs > 0) {
      builder.addEvents(
        Common.Event.newBuilder().setGroupId(connectionId).setTimestamp(requestStartNs).setKind(Common.Event.Kind.NETWORK_HTTP_CONNECTION)
          .setNetworkHttpConnection(Network.NetworkHttpConnectionData.newBuilder().setHttpRequestStarted(
            Network.NetworkHttpConnectionData.HttpRequestStarted.newBuilder()
              .setUrl(data.getUrl()).setMethod(data.getMethod()).setFields(data.getRequestHeader().getRawFields())
              .setTrace(data.getTrace()))));
      if (requestCompleteNs > 0) {
        builder.addEvents(Common.Event.newBuilder().setGroupId(connectionId).setTimestamp(requestCompleteNs)
                            .setKind(Common.Event.Kind.NETWORK_HTTP_CONNECTION)
                            .setNetworkHttpConnection(Network.NetworkHttpConnectionData.newBuilder().setHttpRequestCompleted(
                              Network.NetworkHttpConnectionData.HttpRequestCompleted.newBuilder()
                                .setPayloadId(data.getRequestPayloadId()))));
        if (responseStartNs > 0) {
          builder.addEvents(Common.Event.newBuilder().setGroupId(connectionId).setTimestamp(responseStartNs)
                              .setKind(Common.Event.Kind.NETWORK_HTTP_CONNECTION)
                              .setNetworkHttpConnection(Network.NetworkHttpConnectionData.newBuilder().setHttpResponseStarted(
                                Network.NetworkHttpConnectionData.HttpResponseStarted.newBuilder()
                                  .setFields(data.getResponseHeader().getRawFields()))));
          if (responseCompleteNs > 0) {
            builder.addEvents(Common.Event.newBuilder().setGroupId(connectionId).setTimestamp(responseCompleteNs)
                                .setKind(Common.Event.Kind.NETWORK_HTTP_CONNECTION)
                                .setNetworkHttpConnection(Network.NetworkHttpConnectionData.newBuilder().setHttpResponseCompleted(
                                  Network.NetworkHttpConnectionData.HttpResponseCompleted.newBuilder()
                                    .setPayloadId(data.getResponsePayloadId()).setPayloadSize(data.getResponsePayloadSize()))));
          }
        }
      }

      if (connectionEndNs > 0) {
        builder.addEvents(Common.Event.newBuilder().setGroupId(connectionId).setTimestamp(connectionEndNs)
                            .setKind(Common.Event.Kind.NETWORK_HTTP_CONNECTION).setIsEnded(true)
                            .setNetworkHttpConnection(Network.NetworkHttpConnectionData.newBuilder().setHttpClosed(
                              Network.NetworkHttpConnectionData.HttpClosed.newBuilder())));
      }
    }

    return builder;
  }

  @NotNull
  public static Common.Event.Builder generateNetworkThreadData(@NotNull HttpData data) {
    assert !data.getJavaThreads().isEmpty();
    HttpData.JavaThread thread = data.getJavaThreads().get(0);
    long timestampNs = TimeUnit.MICROSECONDS.toNanos(data.getRequestStartTimeUs());
    return Common.Event.newBuilder().setGroupId(data.getId()).setKind(Common.Event.Kind.NETWORK_HTTP_THREAD).setTimestamp(timestampNs)
      .setNetworkHttpThread(Network.NetworkHttpThreadData.newBuilder().setId(thread.getId()).setName(thread.getName()));
  }

  @NotNull
  public static Common.Event.Builder generateMemoryUsageData(long timestampUs, Memory.MemoryUsageData memoryUsageData) {
    long timestampNs = TimeUnit.MICROSECONDS.toNanos(timestampUs);
    return Common.Event.newBuilder().setTimestamp(timestampNs).setKind(Common.Event.Kind.MEMORY_USAGE).setMemoryUsage(memoryUsageData);
  }

  @NotNull
  public static Common.Event.Builder generateCpuThreadEvent(long timestampSeconds, int tid, String name, Cpu.CpuThreadData.State state) {
    return Common.Event.newBuilder()
      .setPid(SESSION_DATA.getPid())
      .setTimestamp(TimeUnit.SECONDS.toNanos(timestampSeconds))
      .setKind(Common.Event.Kind.CPU_THREAD)
      .setGroupId(tid)
      .setIsEnded(state == Cpu.CpuThreadData.State.DEAD)
      .setCpuThread(Cpu.CpuThreadData.newBuilder().setTid(tid).setName(name).setState(state));
  }

  public static void populateThreadData(@NotNull FakeTransportService service, long streamId) {
    service.addEventToEventGroup(streamId,
                                 ProfilersTestData.generateCpuThreadEvent(1, 1, "Thread 1", Cpu.CpuThreadData.State.RUNNING)
                                   .build());
    service.addEventToEventGroup(streamId,
                                 ProfilersTestData.generateCpuThreadEvent(8, 1, "Thread 1", Cpu.CpuThreadData.State.DEAD)
                                   .build());
    service.addEventToEventGroup(streamId,
                                 ProfilersTestData.generateCpuThreadEvent(6, 2, "Thread 2", Cpu.CpuThreadData.State.RUNNING)
                                   .build());
    service.addEventToEventGroup(streamId,
                                 ProfilersTestData.generateCpuThreadEvent(8, 2, "Thread 2", Cpu.CpuThreadData.State.STOPPED)
                                   .build());
    service.addEventToEventGroup(streamId,
                                 ProfilersTestData.generateCpuThreadEvent(10, 2, "Thread 2", Cpu.CpuThreadData.State.SLEEPING)
                                   .build());
    service.addEventToEventGroup(streamId,
                                 ProfilersTestData.generateCpuThreadEvent(12, 2, "Thread 2", Cpu.CpuThreadData.State.WAITING)
                                   .build());
    service.addEventToEventGroup(streamId,
                                 ProfilersTestData.generateCpuThreadEvent(15, 2, "Thread 2", Cpu.CpuThreadData.State.DEAD)
                                   .build());
  }
}
