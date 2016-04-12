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

import com.android.annotations.concurrency.GuardedBy;
import com.android.ddmlib.*;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.logcat.LogCatTimestamp;
import com.intellij.execution.impl.ConsoleBuffer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.hash.HashMap;
import net.jcip.annotations.ThreadSafe;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
    private LinkedList<LogCatMessage> myMessages = new LinkedList<LogCatMessage>();

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

  public interface LogLineListener {
    void receiveLogLine(@NotNull LogCatMessage line);
  }

  @GuardedBy("myLock")
  private final Map<IDevice, LogcatBuffer> myLogBuffers = new HashMap<IDevice, LogcatBuffer>();
  private final Map<IDevice, AndroidLogcatReceiver> myLogReceivers = new HashMap<IDevice, AndroidLogcatReceiver>();
  private final Map<IDevice, List<LogLineListener>> myListeners = new HashMap<IDevice, List<LogLineListener>>();
  private final Object myLock = new Object();

  @NotNull
  public static AndroidLogcatService getInstance() {
    return ServiceManager.getService(AndroidLogcatService.class);
  }

  private AndroidLogcatService() {
    AndroidDebugBridge.addDeviceChangeListener(this);
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
          myLogBuffers.get(device).addMessage(line);
        }
      }
    };

    final AndroidLogcatReceiver receiver = new AndroidLogcatReceiver(device, logLineListener);

    myLogBuffers.put(device, new LogcatBuffer());
    myLogReceivers.put(device, receiver);

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          AndroidUtils.executeCommandOnDevice(device, "logcat -v long", receiver, true);
        }
        catch (Exception e) {
          getLog().info(e);
          LogCatHeader dummyHeader = new LogCatHeader(Log.LogLevel.ERROR, 0, 0, "?", "Internal", LogCatTimestamp.ZERO);
          logLineListener.receiveLogLine(new LogCatMessage(dummyHeader, e.getMessage()));
        }
      }
    });
  }

  private void stopReceiving(@NotNull IDevice device) {
    synchronized (myLock) {
      myLogReceivers.get(device).cancel();
      myLogReceivers.remove(device);
      myLogBuffers.remove(device);
    }
  }

  private boolean isReceivingFrom(@NotNull IDevice device) {
    synchronized (myLock) {
      return myLogBuffers.containsKey(device);
    }
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
      if (addOldLogs) {
        if (myLogBuffers.containsKey(device)) {
          for (LogCatMessage line : myLogBuffers.get(device).getMessages()) {
            listener.receiveLogLine(line);
          }
        }
      }
      if (!myListeners.containsKey(device)) {
        myListeners.put(device, new ArrayList<LogLineListener>());
      }
      myListeners.get(device).add(listener);
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
    else if (isReceivingFrom(device) && device.isOffline()) {
      stopReceiving(device);
    }
  }

  @Override
  public void dispose() {
    AndroidDebugBridge.removeDeviceChangeListener(this);
    for (AndroidLogcatReceiver receiver : myLogReceivers.values()) {
      receiver.cancel();
    }
  }
}
