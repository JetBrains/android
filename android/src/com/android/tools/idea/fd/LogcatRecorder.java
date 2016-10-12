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
package com.android.tools.idea.fd;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.idea.logcat.AndroidLogcatService;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link LogcatRecorder} records logcat output from the instant run runtime library on a given device into an in memory ring buffer.
 * This class is solely intended for use by the {@link FlightRecorder}, and hence behaves in a form that is useful for later viewing:
 * <ul>
 * <li>Maintains a single log buffer across devices - we are interested in all runs of the project across all devices</li>
 * <li>Adds an entry containing the timestamp on each launch to help correlate with other logs</li>
 * </ul>
 */
public class LogcatRecorder {
  private static final int BUFSIZE = 8192;

  private final Object LOCK = new Object();

  @GuardedBy("LOCK")
  private final EvictingQueue<String> myLogs = EvictingQueue.create(BUFSIZE);

  private final AndroidLogcatService myLogcatService;

  // Device currently being monitored
  private AtomicReference<IDevice> myDeviceRef = new AtomicReference<>();
  private AndroidLogcatService.LogLineListener myLogListener;

  public LogcatRecorder(@NotNull AndroidLogcatService logcatService) {
    myLogcatService = logcatService;
    myLogListener = new MyLogLineListener();
  }

  public void startMonitoring(@NotNull IDevice device, @NotNull LocalDateTime buildTimeStamp) {
    IDevice old = myDeviceRef.getAndSet(device);
    if (old != device) {
      if (old != null) {
        myLogcatService.removeListener(old, myLogListener);
      }
      myLogcatService.addListener(device, myLogListener);

      enableInstantRunLog(device);
    }

    addLog("------------Launch on " + device.getName() + " @ " + buildTimeStamp.toString());
  }

  private static void enableInstantRunLog(IDevice device) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        device.executeShellCommand("setprop log.tag.InstantRun VERBOSE", new NullOutputReceiver());
      }
      catch (Exception ignored) {
      }
    });
  }

  public List<String> getLogs() {
    synchronized (LOCK) {
      return ImmutableList.copyOf(myLogs);
    }
  }

  private void addLog(@NotNull String s) {
    synchronized (LOCK) {
      myLogs.add(s);
    }
  }

  private class MyLogLineListener implements AndroidLogcatService.LogLineListener {
    @Override
    public void receiveLogLine(@NotNull LogCatMessage line) {
      if ("InstantRun".equals(line.getTag())) { // only save logs from the instant run library
        addLog(line.toString());
      }
    }
  }
}
