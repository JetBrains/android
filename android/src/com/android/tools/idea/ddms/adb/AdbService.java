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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.actions.AndroidEnableAdbServiceAction;
import com.android.tools.idea.logcat.AdbErrors;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.TimeoutException;

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
public class AdbService implements Disposable {
  private static final Logger LOG = Logger.getInstance(AdbService.class);

  @GuardedBy("this")
  @Nullable private ListenableFuture<AndroidDebugBridge> myFuture;

  /**
   * adb initialization and termination could occur in separate threads (see {@link #terminateDdmlib()} and {@link CreateBridgeTask}.
   * This lock is used to synchronize between the two.
   * */
  private static final Object ADB_INIT_LOCK = new Object();

  public static AdbService getInstance() {
    return ServiceManager.getService(AdbService.class);
  }

  private AdbService() {
    DdmPreferences.setLogLevel(Log.LogLevel.INFO.getStringValue());
    DdmPreferences.setTimeOut(AndroidUtils.TIMEOUT);

    Log.addLogger(new AdbLogOutput.SystemLogRedirecter());
  }

  @Override
  public void dispose() {
    terminateDdmlib();
  }

  public synchronized ListenableFuture<AndroidDebugBridge> getDebugBridge(@NotNull File adb) {
    // Cancel previous requests if they were unsuccessful
    if (myFuture != null && myFuture.isDone() && !wasSuccessful(myFuture)) {
      terminateDdmlib();
    }

    if (myFuture == null) {
      Future<BridgeConnectionResult> future = ApplicationManager.getApplication().executeOnPooledThread(new CreateBridgeTask(adb));
      // TODO: expose connection timeout in some settings UI? Also see AndroidUtils.TIMEOUT which is way too long
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

  public static boolean canDdmsBeCorrupted(@NotNull AndroidDebugBridge bridge) {
    return isDdmsCorrupted(bridge) || allDevicesAreEmpty(bridge);
  }

  private static boolean allDevicesAreEmpty(@NotNull AndroidDebugBridge bridge) {
    for (IDevice device : bridge.getDevices()) {
      if (device.getClients().length > 0) {
        return false;
      }
    }
    return true;
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

  public synchronized ListenableFuture<AndroidDebugBridge> restartDdmlib(@NotNull Project project) {
    terminateDdmlib();
    File adb = AndroidSdkUtils.getAdb(project);
    if (adb == null) {
      throw new RuntimeException("Unable to locate Android SDK used by project: " + project.getName());
    }
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

  private static class CreateBridgeTask implements Callable<BridgeConnectionResult> {
    private final File myAdb;

    public CreateBridgeTask(@NotNull File adb) {
      myAdb = adb;
    }

    @Override
    public BridgeConnectionResult call() throws Exception {
      AdbErrors.clear();
      boolean clientSupport = AndroidEnableAdbServiceAction.isAdbServiceEnabled();
      LOG.info("Initializing adb using: " + myAdb.getAbsolutePath() + ", client support = " + clientSupport);

      AndroidDebugBridge bridge;
      AdbLogOutput.ToStringLogger toStringLogger = new AdbLogOutput.ToStringLogger();
      Log.addLogger(toStringLogger);
      try {
        synchronized (ADB_INIT_LOCK) {
          AndroidDebugBridge.init(clientSupport);
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

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
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
        catch (InterruptedException e) {
          delegate.cancel(true);
          future.setException(e);
        }
        catch (TimeoutException e) {
          delegate.cancel(true);
          future.setException(e);
        }
      }
    });

    return future;
  }
}
