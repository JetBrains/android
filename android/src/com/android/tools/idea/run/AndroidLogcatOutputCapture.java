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
package com.android.tools.idea.run;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellEnabledDevice;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.logcat.AndroidLogcatFormatter;
import com.android.tools.idea.logcat.AndroidLogcatPreferences;
import com.android.tools.idea.logcat.AndroidLogcatService;
import com.android.tools.idea.logcat.output.LogcatOutputConfigurableProvider;
import com.android.tools.idea.logcat.output.LogcatOutputSettings;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import javax.annotation.concurrent.GuardedBy;
import org.jetbrains.annotations.NotNull;

/**
 * A thread-safe implementation to capture logcat messages of all known client processes and dispatch them so that
 * they are shown in the Run Console window.
 */
class AndroidLogcatOutputCapture {
  private static final String SIMPLE_FORMAT = AndroidLogcatFormatter.createCustomFormat(false, false, false, true);
  private static final Logger LOG = Logger.getInstance(AndroidLogcatOutputCapture.class);

  @NotNull private final String myApplicationId;
  /**
   * Keeps track of the registered listener associated to each device running the application.
   */
  @GuardedBy("myLock")
  @NotNull private final Map<IDevice, AndroidLogcatService.LogcatListener> myLogListeners = new HashMap<>();
  @NotNull private final Object myLock = new Object();

  AndroidLogcatOutputCapture(@NotNull String applicationId) {
    myApplicationId = applicationId;
  }

  public void startCapture(@NotNull final IDevice device, int pid, @NotNull BiConsumer<String, Key> consumer) {
    if (!StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.get()) {
      return;
    }
    if (!LogcatOutputSettings.getInstance().isRunOutputEnabled()) {
      return;
    }

    LOG.info(String.format("startCapture(\"%s\")", device.getName()));
    AndroidLogcatService.LogcatListener
      logListener = new MyLogcatListener(myApplicationId, pid, device, consumer);

    AndroidLogcatService.getInstance().addListener(device, logListener, true);

    // Remember the listener for later cleanup
    AndroidLogcatService.LogcatListener previousListener;
    synchronized (myLock) {
      previousListener = myLogListeners.put(device, logListener);
    }

    // Outside of lock to avoid deadlock with AndroidLogcatService internal lock
    if (previousListener != null) {
      // This should not happen (and we have never seen it happening), but removing the existing listener
      // ensures there are no memory leaks.
      LOG.warn(String.format("The device \"%s\" already has a registered logcat listener for application \"%s\". Removing it",
                                                   device.getName(), myApplicationId));
      AndroidLogcatService.getInstance().removeListener(device, previousListener);
    }
  }

  public void stopCapture(@NotNull IDevice device) {
    LOG.info(String.format("stopCapture(\"%s\")", device.getName()));

    AndroidLogcatService.LogcatListener previousListener;
    synchronized (myLock) {
      previousListener = myLogListeners.remove(device);
    }

    // Outside of lock to avoid deadlock with AndroidLogcatService internal lock
    if (previousListener != null) {
      AndroidLogcatService.getInstance().removeListener(device, previousListener);
    }
  }

  public void stopAll() {
    LOG.info("stopAll()");

    List<Map.Entry<IDevice, AndroidLogcatService.LogcatListener>> listeners;
    synchronized (myLock) {
      listeners = new ArrayList<>(myLogListeners.entrySet());
      myLogListeners.clear();
    }

    // Outside of lock to avoid deadlock with AndroidLogcatService internal lock
    for (Map.Entry<IDevice, AndroidLogcatService.LogcatListener> entry : listeners) {
      AndroidLogcatService.getInstance().removeListener(entry.getKey(), entry.getValue());
    }
  }

  private final class MyLogcatListener extends ApplicationLogListener {
    private final AndroidLogcatFormatter myFormatter;
    private final IShellEnabledDevice myDevice;
    private final AtomicBoolean myIsFirstMessage;
    private final BiConsumer<String, Key> myConsumer;

    private MyLogcatListener(@NotNull String packageName, int pid, @NotNull IDevice device, @NotNull BiConsumer<String, Key> consumer) {
      super(packageName, pid);

      myFormatter = new AndroidLogcatFormatter(ZoneId.systemDefault(), new AndroidLogcatPreferences());
      myDevice = device;
      myIsFirstMessage = new AtomicBoolean(true);
      myConsumer = consumer;
    }

    @Override
    protected String formatLogLine(@NotNull LogCatMessage line) {
      String message = myFormatter.formatMessage(SIMPLE_FORMAT, line.getHeader(), line.getMessage());

      synchronized (myLock) {
        switch (myLogListeners.size()) {
          case 0:
          case 1:
            return message;
          default:
            return '[' + myDevice.getName() + "]: " + message;
        }
      }
    }

    @Override
    protected void notifyTextAvailable(@NotNull String message, @NotNull Key key) {
      if (myIsFirstMessage.compareAndSet(true, false)) {
        myConsumer.accept(LogcatOutputConfigurableProvider.BANNER_MESSAGE + '\n', ProcessOutputTypes.STDOUT);
      }

      myConsumer.accept(message, key);
    }
  }
}
