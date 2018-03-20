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

import com.android.ddmlib.*;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.idea.run.LoggingReceiver;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.intellij.execution.impl.ConsoleBuffer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * {@link AndroidLogcatService} is the class that manages logs in all connected devices and emulators.
 * Other classes can call {@link AndroidLogcatService#addListener(IDevice, LogcatListener)} to listen for logs of specific device/emulator.
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

  public interface LogcatListener {
    default void onLogLineReceived(@NotNull LogCatMessage line) {
    }

    default void onCleared() {
    }
  }

  private final Object myLock;

  // TODO Change these maps into a set of LogcatDevices that each maintain their receivers, buffers, executors, etc

  @GuardedBy("myLock")
  private final Map<IDevice, AndroidLogcatReceiver> myLogReceivers;

  @GuardedBy("myLock")
  private final Map<IDevice, LogcatBuffer> myLogBuffers;

  /**
   * This is a list of commands to execute per device. We use a newSingleThreadExecutor
   * to model a single queue of tasks to run, but that is poorly reflected in the
   * type of the variable.
   */
  @GuardedBy("myLock")
  private final Map<IDevice, ExecutorService> myExecutors;

  @GuardedBy("myLock")
  private final Multimap<IDevice, LogcatListener> myDeviceToListenerMultimap;

  @NotNull
  public static AndroidLogcatService getInstance() {
    return ServiceManager.getService(AndroidLogcatService.class);
  }

  @TestOnly
  AndroidLogcatService() {
    myLock = new Object();
    myLogReceivers = new HashMap<>();
    myLogBuffers = new HashMap<>();
    myExecutors = new HashMap<>();
    myDeviceToListenerMultimap = ArrayListMultimap.create();

    AndroidDebugBridge.addDeviceChangeListener(this);
  }

  private void startReceiving(@NotNull final IDevice device) {
    synchronized (myLock) {
      if (myLogReceivers.containsKey(device)) {
        return;
      }

      connect(device);

      AndroidLogcatReceiver receiver = newAndroidLogcatReceiver(device);
      myLogReceivers.put(device, receiver);
      myLogBuffers.put(device, new LogcatBuffer());
      myExecutors.get(device).submit(() -> executeLogcat(device, receiver));
    }
  }

  @NotNull
  private AndroidLogcatReceiver newAndroidLogcatReceiver(@NotNull IDevice device) {
    return new AndroidLogcatReceiver(device, new LogcatListener() {
      @Override
      public void onLogLineReceived(@NotNull LogCatMessage line) {
        synchronized (myLock) {
          myDeviceToListenerMultimap.get(device).forEach(listener -> listener.onLogLineReceived(line));
          LogcatBuffer buffer = myLogBuffers.get(device);

          if (buffer != null) {
            buffer.addMessage(line);
          }
        }
      }
    });
  }

  private static void executeLogcat(@NotNull IShellEnabledDevice device, @NotNull AndroidOutputReceiver receiver) {
    try {
      execute(device, supportsEpochFormatModifier(device) ? "logcat -v long -v epoch" : "logcat -v long", receiver, Duration.ZERO);
    }
    catch (Throwable throwable) {
      getLog().warn(throwable);
    }
  }

  private static boolean supportsEpochFormatModifier(@NotNull IShellEnabledDevice device)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
    LogcatHelpReceiver receiver = new LogcatHelpReceiver();
    device.executeShellCommand("logcat --help", receiver, 10, TimeUnit.SECONDS);

    return receiver.mySupportsEpochFormatModifier;
  }

  private static final class LogcatHelpReceiver extends MultiLineReceiver {
    private boolean mySupportsEpochFormatModifier;
    private boolean myCancelled;

    @Override
    public void processNewLines(@NotNull String[] lines) {
      if (mySupportsEpochFormatModifier) {
        myCancelled = true;
        return;
      }

      mySupportsEpochFormatModifier = Arrays.stream(lines).anyMatch(line -> line.contains("epoch"));
    }

    @Override
    public boolean isCancelled() {
      return myCancelled;
    }
  }

  private void connect(@NotNull IDevice device) {
    synchronized (myLock) {
      if (!myExecutors.containsKey(device)) {
        ThreadFactory factory = new ThreadFactoryBuilder()
          .setNameFormat("logcat-" + device.getName())
          .build();

        myExecutors.put(device, Executors.newSingleThreadExecutor(factory));
      }
    }
  }

  private void disconnect(@NotNull IDevice device) {
    synchronized (myLock) {
      stopReceiving(device);
      myExecutors.remove(device);
    }
  }

  private void stopReceiving(@NotNull IDevice device) {
    synchronized (myLock) {
      if (myLogReceivers.containsKey(device)) {
        myLogReceivers.get(device).cancel();
        myLogReceivers.remove(device);
        myLogBuffers.remove(device);
      }
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
    synchronized (myLock) {
      ExecutorService executor = myExecutors.get(device);
      // If someone keeps a reference to a device that is disconnected, executor will be null.
      if (executor == null) {
        notifyThatLogcatWasCleared(device);
        return;
      }

      stopReceiving(device);

      executor.submit(() -> {
        try {
          execute(device, "logcat -c", new LoggingReceiver(getLog()), Duration.ofSeconds(5));
        }
        catch (final Exception e) {
          getLog().info(e);
          ApplicationManager.getApplication().invokeLater(() -> Messages
            .showErrorDialog(project, "Error: " + e.getMessage(), AndroidBundle.message("android.logcat.error.dialog.title")));
        }

        notifyThatLogcatWasCleared(device);
      });

      startReceiving(device);
    }
  }

  private void notifyThatLogcatWasCleared(@NotNull IDevice device) {
    synchronized (myLock) {
      myDeviceToListenerMultimap.get(device).forEach(LogcatListener::onCleared);
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
  public void addListener(@NotNull IDevice device, @NotNull LogcatListener listener, boolean addOldLogs) {
    synchronized (myLock) {
      if (addOldLogs && myLogBuffers.containsKey(device)) {
        for (LogCatMessage line : myLogBuffers.get(device).getMessages()) {
          listener.onLogLineReceived(line);
        }
      }

      myDeviceToListenerMultimap.put(device, listener);

      if (device.isOnline()) {
        startReceiving(device);
      }
    }
  }

  /**
   * @see #addListener(IDevice, LogcatListener, boolean)
   */
  public void addListener(@NotNull IDevice device, @NotNull LogcatListener listener) {
    addListener(device, listener, false);
  }

  public void removeListener(@NotNull IDevice device, @NotNull LogcatListener listener) {
    synchronized (myLock) {
      Collection<LogcatListener> listeners = myDeviceToListenerMultimap.get(device);

      if (listeners.isEmpty()) {
        return;
      }

      listeners.remove(listener);

      if (listeners.isEmpty()) {
        stopReceiving(device);
      }
    }
  }

  @Override
  public void deviceConnected(@NotNull IDevice device) {
    if (device.isOnline()) {
      // TODO Evaluate if we really need to start getting logs as soon as we connect, or whether a connect would suffice.
      startReceiving(device);
    }
  }

  @Override
  public void deviceDisconnected(@NotNull IDevice device) {
    disconnect(device);
  }

  @Override
  public void deviceChanged(@NotNull IDevice device, int changeMask) {
    if (device.isOnline()) {
      startReceiving(device);
    }
    else {
      disconnect(device);
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

  /**
   * Same as {@link #dispose()} but waits for background threads to terminate
   * before returning to the caller. This is useful to prevent thread leaks
   * when running tests.
   */
  @TestOnly
  public void shutdown() {
    Disposer.dispose(this);

    synchronized (myLock) {
      myExecutors.values().forEach(executor -> {
        try {
          executor.shutdownNow();
          executor.awaitTermination(5_000, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
          getLog().info("Error shutting down executor", e);
        }
      });
    }
  }

  private static void execute(@NotNull IShellEnabledDevice device,
                              @NotNull String command,
                              @NotNull AndroidOutputReceiver receiver,
                              @NotNull Duration duration)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
    device.executeShellCommand(command, receiver, duration.toMillis(), TimeUnit.MILLISECONDS);

    if (receiver.isCancelled()) {
      return;
    }

    receiver.invalidate();
  }
}
