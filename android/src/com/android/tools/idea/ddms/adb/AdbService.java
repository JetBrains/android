/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.ddms.adb;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ddmlib.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.actions.AndroidEnableAdbServiceAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link AdbService} is the main entry point to initializing and obtaining the {@link AndroidDebugBridge}.
 *
 * <p>Actions that require a handle to the debug bridge should invoke {@link #getDebugBridge(File)} to obtain the debug bridge.
 * This bridge is only valid at the time it is obtained, and could go stale in the future (e.g. user disables
 * adb integration via {@link AndroidEnableAdbServiceAction}, or launches monitor via
 * {@link org.jetbrains.android.actions.AndroidRunDdmsAction}).
 *
 * <p>Components that need to keep a handle to the bridge for longer durations (such as tool windows that monitor device state) should do so
 * by first invoking {@link #getDebugBridge(File)} to obtain the bridge, and implementing
 * {@link AndroidDebugBridge.IDebugBridgeChangeListener} to ensure that they get updates to the status of the bridge.
 */
public class AdbService implements Disposable, AdbOptionsService.AdbOptionsListener {
  private static final Logger LOG = Logger.getInstance(AdbService.class);
  public static final int TIMEOUT = 3000000;

  @GuardedBy("this")
  @Nullable private ListenableFuture<AndroidDebugBridge> myFuture;

  private final AtomicReference<File> myAdb = new AtomicReference<>();

  /**
   * adb initialization and termination could occur in separate threads (see {@link #terminateDdmlib()} and {@link CreateBridgeTask}.
   * This lock is used to synchronize between the two.
   * */
  private static final Object ADB_INIT_LOCK = new Object();

  public static AdbService getInstance() {
    return ServiceManager.getService(AdbService.class);
  }

  private AdbService() {
    // Synchronize ddmlib log level with the corresponding IDEA log level
    String defaultLogLevel = AdbLogOutput.SystemLogRedirecter.getLogger().isTraceEnabled()
                   ? Log.LogLevel.VERBOSE.getStringValue()
                   : AdbLogOutput.SystemLogRedirecter.getLogger().isDebugEnabled()
                     ? Log.LogLevel.DEBUG.getStringValue()
                     : Log.LogLevel.INFO.getStringValue();
    DdmPreferences.setLogLevel(defaultLogLevel);
    DdmPreferences.setTimeOut(TIMEOUT);

    Log.addLogger(new AdbLogOutput.SystemLogRedirecter());

    AdbOptionsService.getInstance().addListener(this);
  }

  @Override
  public void dispose() {
    terminateDdmlib();
    AdbOptionsService.getInstance().removeListener(this);
  }

  public synchronized ListenableFuture<AndroidDebugBridge> getDebugBridge(@NotNull File adb) {
    myAdb.set(adb);

    // Cancel previous requests if they were unsuccessful
    if (myFuture != null && myFuture.isDone() && !wasSuccessful(myFuture)) {
      terminateDdmlib();
    }

    if (myFuture == null) {
      Future<BridgeConnectionResult> future = ApplicationManager.getApplication().executeOnPooledThread(new CreateBridgeTask(adb));
      // TODO: expose connection timeout in some settings UI? Also see TIMEOUT which is way too long
      myFuture = makeTimedFuture(future, 20, TimeUnit.SECONDS);
    }

    return myFuture;
  }

  public synchronized void terminateDdmlib() {
    if (myFuture != null) {
      myFuture.cancel(true);
      myFuture = null;
    }

    synchronized (ADB_INIT_LOCK) {
      AndroidDebugBridge.disconnectBridge();
      AndroidDebugBridge.terminate();
    }
  }

  public static boolean isDdmsCorrupted(@NotNull AndroidDebugBridge bridge) {
    // TODO: find other way to check if debug service is available

    IDevice[] devices = bridge.getDevices();
    if (devices.length > 0) {
      for (IDevice device : devices) {
        Client[] clients = device.getClients();

        if (clients.length > 0) {
          ClientData clientData = clients[0].getClientData();
          return clientData.getVmIdentifier() == null;
        }
      }
    }
    return false;
  }

  @NotNull
  public synchronized ListenableFuture<AndroidDebugBridge> restartDdmlib(@NotNull File adb) {
    terminateDdmlib();
    return getDebugBridge(adb);
  }

  /** Returns whether the future has completed successfully. */
  private static boolean wasSuccessful(Future<AndroidDebugBridge> future) {
    if (!future.isDone()) {
      return false;
    }

    try {
      AndroidDebugBridge bridge = future.get();
      return bridge != null && bridge.isConnected();
    }
    catch (Exception e) {
      return false;
    }
  }

  @Override
  public void optionsChanged() {
    File adb = myAdb.get();
    // we use the presence of myAdb as an indication that adb was started earlier
    if (adb != null) {
      LOG.info("Terminating adb server");
      terminateDdmlib();

      LOG.info("Restart adb server");
      getDebugBridge(adb);
    }
  }

  private static class CreateBridgeTask implements Callable<BridgeConnectionResult> {
    private final File myAdb;

    public CreateBridgeTask(@NotNull File adb) {
      myAdb = adb;
    }

    @Override
    public BridgeConnectionResult call() throws Exception {
      boolean clientSupport = AndroidEnableAdbServiceAction.isAdbServiceEnabled();
      LOG.info("Initializing adb using: " + myAdb.getAbsolutePath() + ", client support = " + clientSupport);

      ImmutableMap<String, String> env;
      if (ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode()) {
        // adb accesses $HOME/.android, which isn't allowed when running in the bazel sandbox
        env = ImmutableMap.of("HOME", Files.createTempDir().getAbsolutePath());
      }
      else {
        env = ImmutableMap.of();
      }

      AndroidDebugBridge bridge;
      AdbLogOutput.ToStringLogger toStringLogger = new AdbLogOutput.ToStringLogger();
      Log.addLogger(toStringLogger);
      try {
        synchronized (ADB_INIT_LOCK) {
          AndroidDebugBridge.init(clientSupport, AdbOptionsService.getInstance().shouldUseLibusb(), env);
          bridge = AndroidDebugBridge.createBridge(myAdb.getPath(), false);
        }

        if (bridge == null) {
          return BridgeConnectionResult.make("Unable to start adb server: " + toStringLogger.getOutput());
        }

        while (!bridge.isConnected()) {
          try {
            TimeUnit.MILLISECONDS.sleep(200);
          }
          catch (InterruptedException e) {
            // if cancelled, don't wait for connection and return immediately
            return BridgeConnectionResult.make("Timed out attempting to connect to adb: " + toStringLogger.getOutput());
          }
        }

        LOG.info("Successfully connected to adb");
        return BridgeConnectionResult.make(bridge);
      } finally {
        Log.removeLogger(toStringLogger);
      }
    }
  }

  // It turns out that IntelliJ's invokeOnPooledThread will capture exceptions thrown from the callable, log them,
  // and not pass them on via the future. As a result, the callable has to pass the error status back inline. Hence we have
  // this simple wrapper class around either an error result or a correct result.
  private static class BridgeConnectionResult {
    @Nullable public final AndroidDebugBridge bridge;
    @Nullable public final String error;

    private BridgeConnectionResult(@Nullable AndroidDebugBridge bridge, @Nullable String error) {
      this.bridge = bridge;
      this.error = error;
    }

    public static BridgeConnectionResult make(@NotNull AndroidDebugBridge bridge) {
      return new BridgeConnectionResult(bridge, null);
    }

    public static BridgeConnectionResult make(@NotNull String error) {
      return new BridgeConnectionResult(null, error);
    }
  }

  /** Returns a future that wraps the given future for obtaining the debug bridge with a timeout. */
  private static ListenableFuture<AndroidDebugBridge> makeTimedFuture(@NotNull final Future<BridgeConnectionResult> delegate,
                                                         final long timeout,
                                                         @NotNull final TimeUnit unit) {
    final SettableFuture<AndroidDebugBridge> future = SettableFuture.create();

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          BridgeConnectionResult value = delegate.get(timeout, unit);
          if (value.error != null) {
            future.setException(new RuntimeException("Unable to create Debug Bridge: " + value.error));
          }
          else {
            future.set(value.bridge);
          }
        }
        catch (ExecutionException e) {
          future.setException(e.getCause());
        }
      catch (InterruptedException | TimeoutException e) {
          delegate.cancel(true);
          future.setException(e);
        }
    });

    return future;
  }

  public static String getDebugBridgeDiagnosticErrorMessage(@NotNull Throwable t, @NotNull File adb) {
    // If we cannot connect to ADB in a reasonable amount of time (10 seconds timeout in AdbService), then something is seriously
    // wrong. The only identified reason so far is that some machines have incompatible versions of adb that were already running.
    // e.g. Genymotion, some HTC flashing software, Ubuntu's adb package may all conflict with the version of adb in the SDK.
    String msg;
    if (t.getMessage() != null) {
      msg = t.getMessage();
    }
    else {
      msg = String.format("Unable to establish a connection to adb.\n\n" +
                          "Check the Event Log for possible issues.\n" +
                          "This can happen if you have an incompatible version of adb running already.\n" +
                          "Try re-opening %1$s after killing any existing adb daemons.\n\n" +
                          "If this happens repeatedly, please file a bug at http://b.android.com including the following:\n" +
                          "  1. Output of the command: '%2$s devices'\n" +
                          "  2. Your idea.log file (Help | Show Log in Explorer)\n",
                          ApplicationNamesInfo.getInstance().getProductName(), adb.getAbsolutePath());
    }
    return msg;
  }
}
