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
package com.android.tools.idea.monitor.ui.memory.model;

import com.android.tools.idea.monitor.datastore.Poller;
import com.android.tools.idea.monitor.profilerclient.DeviceProfilerService;
import com.android.tools.profiler.proto.MemoryProfilerService;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class MemoryPoller extends Poller {
  private static final Logger LOG = Logger.getInstance(MemoryPoller.class.getCanonicalName());

  private static final long POLL_PERIOD_NS = TimeUnit.MILLISECONDS.toNanos(250);

  private long myStartTimestamp;
  private int myAppId;

  public MemoryPoller(@NotNull DeviceProfilerService service, int appId) {
    super(service, POLL_PERIOD_NS);
    myAppId = appId;
  }

  @Override
  protected void asyncInit() {
    MemoryProfilerService.MemoryConfig config = MemoryProfilerService.MemoryConfig.newBuilder().setAppId(myAppId).addOptions(
      MemoryProfilerService.MemoryConfig.Option.newBuilder().setFeature(MemoryProfilerService.MemoryFeature.MEMORY_LEVELS).setEnabled(true)
        .build()
    ).build();
    MemoryProfilerService.MemoryStatus status = myService.getMemoryService().setMemoryConfig(config);
    myStartTimestamp = status.getStatusTimestamp();
  }

  @Override
  protected void asyncShutdown() {
    MemoryProfilerService.MemoryConfig config = MemoryProfilerService.MemoryConfig.newBuilder().setAppId(myAppId).addOptions(
      MemoryProfilerService.MemoryConfig.Option.newBuilder().setFeature(MemoryProfilerService.MemoryFeature.MEMORY_LEVELS).setEnabled(false)
        .build()
    ).build();
    myService.getMemoryService().setMemoryConfig(config);
  }

  @Override
  protected void poll() {
    MemoryProfilerService.MemoryRequest request =
      MemoryProfilerService.MemoryRequest.newBuilder().setAppId(myAppId).setStartTime(myStartTimestamp).setEndTime(Long.MAX_VALUE).build();
    MemoryProfilerService.MemoryData result;
    try {
      result = myService.getMemoryService().getData(request);
    }
    catch (StatusRuntimeException e) {
      LOG.info("Server most likely unreachable.");
      cancel(true);
      return;
    }

    myStartTimestamp = result.getEndTimestamp();
    // TODO connect this to the data store
  }
}
