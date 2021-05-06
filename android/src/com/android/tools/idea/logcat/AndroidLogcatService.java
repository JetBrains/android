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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellEnabledDevice;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.run.LoggingReceiver;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.intellij.execution.impl.ConsoleBuffer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import java.io.EOFException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

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

  private static class ListenerConnector implements LogcatListener {
    @GuardedBy("myListenerLock")
    @Nullable private LogcatListener myListener; // Initially not null, set to null when disconnected.
    @GuardedBy("myBacklogLock")
    @Nullable private Queue<LogCatMessage> myBacklog; // myBacklog is either null or not empty.
    // The two locks bellow should never be held simultaneously or for a prolonged period of time.
    @NotNull private final Object myListenerLock = new Object();
    @NotNull private final Object myBacklogLock = new Object();

    ListenerConnector(@NotNull LogcatListener listener, @NotNull Collection<LogCatMessage> messageBacklog) {
      myListener = listener;
      myBacklog = messageBacklog.isEmpty() ? null : new ArrayDeque<>(messageBacklog);
    }

    @Override
    public void onLogLineReceived(@NotNull LogCatMessage message) {
      processBacklog(); // Make sure that the backlog is processed before the new message.
      dispatchMessage(message);
    }

    @Override
    public void onCleared() {
      synchronized (myBacklogLock) {
        myBacklog = null;
      }
      synchronized (myListenerLock) {
        if (myListener != null) {
          myListener.onCleared();
        }
      }
    }

    boolean isConnectedTo(@NotNull LogcatListener listener) {
      synchronized (myListenerLock) {
        return listener == myListener;
      }
    }

    void disconnectListener() {
      synchronized (myListenerLock) {
        myListener = null;
      }
      synchronized (myBacklogLock) {
        myBacklog = null;
      }
    }

    void processBacklog() {
      LogCatMessage message;
      while ((message = getMessageFromBacklog()) != null) {
        dispatchMessage(message);
      }
    }

    private void dispatchMessage(@NotNull LogCatMessage message) {
      synchronized (myListenerLock) {
        if (myListener != null) {
          myListener.onLogLineReceived(message);
        }
      }
    }

    @Nullable
    private LogCatMessage getMessageFromBacklog() {
      synchronized (myBacklogLock) {
        if (myBacklog == null) {
          return null;
        }
        LogCatMessage message = myBacklog.remove();
        if (myBacklog.isEmpty()) {
          myBacklog = null;
        }
        return message;
      }
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
  private final Multimap<IDevice, ListenerConnector> myDeviceToListenerMultimap;

  @NotNull
  public static AndroidLogcatService getInstance() {
    return ApplicationManager.getApplication().getService(AndroidLogcatService.class);
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
        Iterable<ListenerConnector> connectors;
        synchronized (myLock) {
          connectors = ImmutableList.copyOf(myDeviceToListenerMultimap.get(device));
          LogcatBuffer buffer = myLogBuffers.get(device);

          if (buffer != null) {
            buffer.addMessage(line);
          }
        }

        connectors.forEach(connector -> connector.onLogLineReceived(line));
      }
    });
  }

  private static void executeLogcat(@NotNull IShellEnabledDevice device, @NotNull AndroidLogcatReceiver receiver) {
    try {
      execute(device, supportsEpochFormatModifier(device) ? "logcat -v long -v epoch" : "logcat -v long", receiver, Duration.ZERO);
    }
    catch (EOFException e) {
      getLog().info("Logcat process terminated");
    }
    catch (Throwable throwable) {
      getLog().warn(throwable);

      String app = IdeInfo.getInstance().isAndroidStudio() ? "com.android.studio" : "com.jetbrains.idea";
      receiver.notifyLine(new LogCatHeader(LogLevel.ERROR, 0, 0, app, "AndroidLogcatService", Instant.now()), throwable.toString());
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
          .setNameFormat("Android Logcat Service Thread %s for Device Serial Number " + device)
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
        catch (Exception exception) {
          getLog().warn(exception);

          ApplicationManager.getApplication().invokeLater(() -> {
            String title = AndroidBundle.message("android.logcat.error.dialog.title");
            Messages.showErrorDialog(project, exception.toString(), title);
          });
        }

        notifyThatLogcatWasCleared(device);
      });

      startReceiving(device);
    }
  }

  private void notifyThatLogcatWasCleared(@NotNull IDevice device) {
    Iterable<ListenerConnector> connectors;
    synchronized (myLock) {
      connectors = ImmutableList.copyOf(myDeviceToListenerMultimap.get(device));
    }

    connectors.forEach(ListenerConnector::onCleared);
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
      List<LogCatMessage> oldMessages =
          addOldLogs && myLogBuffers.containsKey(device) ? myLogBuffers.get(device).getMessages() : ImmutableList.of();

      ListenerConnector listenerConnector = new ListenerConnector(listener, oldMessages);
      myDeviceToListenerMultimap.put(device, listenerConnector);

      if (device.isOnline()) {
        startReceiving(device);
      }

      if (!oldMessages.isEmpty()) {
        ExecutorService executor = myExecutors.get(device);
        assert executor != null;
        executor.submit(() -> listenerConnector.processBacklog());
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
      Collection<ListenerConnector> connectors = myDeviceToListenerMultimap.get(device);

      if (connectors.isEmpty()) {
        return;
      }

      for (Iterator<ListenerConnector> iter = connectors.iterator(); iter.hasNext();) {
        ListenerConnector connector = iter.next();
        if (connector.isConnectedTo(listener)) {
          connector.disconnectListener();
          iter.remove();
          break;
        }
      }

      if (connectors.isEmpty()) {
        stopReceiving(device);
        myDeviceToListenerMultimap.removeAll(device);
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
