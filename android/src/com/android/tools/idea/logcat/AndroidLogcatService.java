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
package com.android.tools.idea.logcat;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.logcat.LogCatTimestamp;
import com.intellij.execution.impl.ConsoleBuffer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * {@link AndroidLogcatService} is the class that manages logs in all connected devices and emulators.
 * Other classes can call {@link AndroidLogcatService#addListener(IDevice, LogLineListener)} to listen for logs of specific device/emulator.
 * Listeners invoked in a pooled thread and this class is thread safe.
 */
@ThreadSafe
public final class AndroidLogcatService implements AndroidDebugBridge.IDeviceChangeListener, Disposable {
  private static Logger getLog() {
    return Logger.getInstance(AndroidLogcatService.class);
  }

  private static class LogcatBuffer {
    private int myBufferSize;
    private final LinkedList<LogCatMessage> myMessages = new LinkedList<>();

    public void addMessage(@NotNull LogCatMessage message) {
      myMessages.add(message);
      myBufferSize += message.getMessage().length();
      if (ConsoleBuffer.useCycleBuffer()) {
        while (myBufferSize > ConsoleBuffer.getCycleBufferSize()) {
          myBufferSize -= myMessages.removeFirst().getMessage().length();
        }
      }
    }

    @NotNull
    public List<LogCatMessage> getMessages() {
      return myMessages;
    }
  }

  interface LogcatRunner {
    void start(@NotNull IDevice device, @NotNull AndroidLogcatReceiver receiver);
  }

  public interface LogLineListener {
    void receiveLogLine(@NotNull LogCatMessage line);
  }

  private final Object myLock = new Object();

  @GuardedBy("myLock")
  private final Map<IDevice, LogcatBuffer> myLogBuffers = new HashMap<>();

  @GuardedBy("myLock")
  private final Map<IDevice, AndroidLogcatReceiver> myLogReceivers = new HashMap<>();

  @GuardedBy("myLock")
  private final Map<IDevice, List<LogLineListener>> myListeners = new HashMap<>();

  /**
   * A logcat connection does not cancel immediately, so we use a countdown latch to inform us when
   * a channel actually is done closing. A latch should be set to 1 (active) or 0 (closed) at any
   * given time. See also {@link #stopReceiving(IDevice)}.
   */
  @VisibleForTesting
  final Map<IDevice, CountDownLatch> myDeviceLatches = new ConcurrentHashMap<>();

  private final LogcatRunner myLogcatRunner;

  @NotNull
  public static AndroidLogcatService getInstance() {
    return ServiceManager.getService(AndroidLogcatService.class);
  }

  private AndroidLogcatService() {
    AndroidDebugBridge.addDeviceChangeListener(this);
    myLogcatRunner = new LogcatRunner() {
      @Override
      public void start(@NotNull IDevice device, @NotNull AndroidLogcatReceiver receiver) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            try {
              AndroidUtils.executeCommandOnDevice(device, "logcat -v long", receiver, true);
              myDeviceLatches.get(device).countDown();
            }
            catch (Exception e) {
              getLog().info(String.format(
                "Caught exception when capturing logcat output from the device %1$s. Receiving logcat output from this device will be " +
                "stopped, and the listeners will be notified with this exception as the last message", device.getName()), e);
              LogCatHeader dummyHeader = new LogCatHeader(Log.LogLevel.ERROR, 0, 0, "?", "Internal", LogCatTimestamp.ZERO);
              receiver.notifyLine(dummyHeader, e.getMessage());
            }
            stopReceiving(device);
          }
        });
      }
    };
  }

  @VisibleForTesting
  AndroidLogcatService(@NotNull LogcatRunner logcatRunner) {
    myLogcatRunner = logcatRunner;
  }

  private void startReceiving(@NotNull final IDevice device) {
    final LogLineListener logLineListener = new LogLineListener() {
      @Override
      public void receiveLogLine(@NotNull LogCatMessage line) {
        synchronized (myLock) {
          if (myListeners.containsKey(device)) {
            for (LogLineListener listener : myListeners.get(device)) {
              listener.receiveLogLine(line);
            }
          }
          if (myLogBuffers.containsKey(device)) {
            myLogBuffers.get(device).addMessage(line);
          }
        }
      }
    };
    final AndroidLogcatReceiver receiver = new AndroidLogcatReceiver(device, logLineListener);

    myDeviceLatches.put(device, new CountDownLatch(1));
    synchronized (myLock) {
      myLogBuffers.put(device, new LogcatBuffer());
      myLogReceivers.put(device, receiver);
    }
    myLogcatRunner.start(device, receiver);
  }

  private void stopReceiving(@NotNull IDevice device) {
    synchronized (myLock) {
      if (!isReceivingFrom(device)) {
        return;
      }

      if (myLogReceivers.containsKey(device)) {
        myLogReceivers.get(device).cancel();
      }
      myLogReceivers.remove(device);
      myLogBuffers.remove(device);
    }
    CountDownLatch latch = myDeviceLatches.get(device);
    if (latch != null) {
      try {
        latch.await();
      }
      catch (InterruptedException ignored) {
      }

      // This must happen after the await call above, or else the countdown logic elsewhere in
      // this class will throw an NPE if we remove this too soon.
      myDeviceLatches.remove(device);
    }
  }

  private boolean isReceivingFrom(@NotNull IDevice device) {
    synchronized (myLock) {
      return myLogBuffers.containsKey(device);
    }
  }

  /**
   * Clears logs for the current device.
   */
  public void clearLogcat(@NotNull IDevice device, @NotNull Project project) {
    // In theory, we only need to clear the buffer. However, due to issues in the platform, clearing logcat via "logcat -c" could
    // end up blocking the current logcat readers. As a result, we need to issue a restart of the logging to work around the platform bug.
    // See https://code.google.com/p/android/issues/detail?id=81164 and https://android-review.googlesource.com/#/c/119673
    // NOTE: We can avoid this and just clear the console if we ever decide to stop issuing a "logcat -c" to the device or if we are
    // confident that https://android-review.googlesource.com/#/c/119673 doesn't happen anymore.
    stopReceiving(device);
    AndroidLogcatUtils.clearLogcat(project, device);
    startReceiving(device);
  }

  /**
   * Add a listener which receives each line, unfiltered, that comes from the specified device. If {@code addOldLogs} is true,
   * this will also notify the listener of every log message received so far.
   * Multi-line messages will be parsed into single lines and sent with the same header.
   * For example, Log.d(tag, "Line1\nLine2") will be sent to listeners in two iterations,
   * first: "Line1" with a header, second: "Line2" with the same header.
   * Listeners are invoked in a pooled thread, and they are triggered A LOT. You should be very careful if delegating this text
   * to a UI thread. For example, don't directly invoke a runnable on the UI thread per line, but consider batching many log lines first.
   */
  public void addListener(@NotNull IDevice device, @NotNull LogLineListener listener, boolean addOldLogs) {
    synchronized (myLock) {
      if (addOldLogs && myLogBuffers.containsKey(device)) {
        for (LogCatMessage line : myLogBuffers.get(device).getMessages()) {
          listener.receiveLogLine(line);
        }
      }

      if (!myListeners.containsKey(device)) {
        myListeners.put(device, new ArrayList<>());
      }

      myListeners.get(device).add(listener);

      if (device.isOnline() && !isReceivingFrom(device)) {
        startReceiving(device);
      }
    }
  }

  /**
   * @see #addListener(IDevice, LogLineListener, boolean)
   */
  public void addListener(@NotNull IDevice device, @NotNull LogLineListener listener) {
    addListener(device, listener, false);
  }

  public void removeListener(@NotNull IDevice device, @NotNull LogLineListener listener) {
    synchronized (myLock) {
      if (myListeners.containsKey(device)) {
        myListeners.get(device).remove(listener);

        if (myListeners.get(device).isEmpty() && isReceivingFrom(device)) {
          stopReceiving(device);
        }
      }
    }
  }

  @Override
  public void deviceConnected(@NotNull IDevice device) {
    if (device.isOnline() && !isReceivingFrom(device)) {
      startReceiving(device);
    }
  }

  @Override
  public void deviceDisconnected(@NotNull IDevice device) {
    if (isReceivingFrom(device)) {
      stopReceiving(device);
    }
  }

  @Override
  public void deviceChanged(@NotNull IDevice device, int changeMask) {
    if (!isReceivingFrom(device) && device.isOnline()) {
      startReceiving(device);
    }
    else if (isReceivingFrom(device) && !device.isOnline()) {
      stopReceiving(device);
    }
  }

  @Override
  public void dispose() {
    AndroidDebugBridge.removeDeviceChangeListener(this);
    synchronized (myLock) {
      for (AndroidLogcatReceiver receiver : myLogReceivers.values()) {
        receiver.cancel();
      }
    }
  }
}
