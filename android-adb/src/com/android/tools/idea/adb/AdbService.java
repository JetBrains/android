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
package com.android.tools.idea.adb;

import static com.android.ddmlib.AndroidDebugBridge.DEFAULT_START_ADB_TIMEOUT_MILLIS;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.AdbInitOptions;
import com.android.ddmlib.Log;
import com.android.ddmlib.TimeoutRemainder;
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link AdbService} is the main entry point to initializing and obtaining the {@link AndroidDebugBridge}.
 *
 * <p>Actions that require a handle to the debug bridge should invoke {@link #getDebugBridge(File)} to obtain the debug bridge.
 * This bridge is only valid at the time it is obtained, and could go stale in the future.
 *
 * <p>Components that need to keep a handle to the bridge for longer durations (such as tool windows that monitor device state) should do so
 * by first invoking {@link #getDebugBridge(File)} to obtain the bridge, and implementing
 * {@link AndroidDebugBridge.IDebugBridgeChangeListener} to ensure that they get updates to the status of the bridge.
 */
public class AdbService implements Disposable, AdbOptionsService.AdbOptionsListener {
  private static final Logger LOG = Logger.getInstance(AdbService.class);
  /**
   * The default timeout used by many calls to ddmlib. This includes executing a command,
   * waiting for a response from a command, trying to send a command, etc.
   * The default value is very high because some operations using this timeout can
   * take a long time to complete (e.g. installing an application on a device). See
   * <a href="https://github.com/JetBrains/android/commit/c667dabb759df8bddc72120f51192ee4a5b4e308">IDEA-67042 increase timeout</a>
   * and <a href="https://github.com/JetBrains/android/commit/d17853af32a17788dbd8bd11c1ec5e720fb5bb6a">increase timeout</a>
   * for commits that resulted in the current value of 50 minutes.
   *
   * <p>The problem with such a worst-case timeout value is that many operations are
   * expected to take a very short amount of time, but, at the same time, ADB can
   * sometimes hang for unexpected reasons. This state of affairs makes it difficult
   * for callers to provide a user friendly experience, especially in cases where
   * ADB hangs unexpectedly.  Addressing this issue would require non trivial refactoring
   * of this code, and its callers, to either provide an explicit timeout for every invocation,
   * or maybe expose 2 timeouts: one for short lived operations, and one for operations that
   * can take a long time.
   */
  public static final int ADB_DEFAULT_TIMEOUT_MILLIS = (int)TimeUnit.MINUTES.toMillis(50);

  /**
   * Default timeout to use when calling {@link #terminateDdmlib()}. This ensures
   * that the call terminates even if ADB hangs unexpectedly, as terminating ADB
   * should never take more than a few seconds if ADB is responsive.
   */
  private static final long ADB_TERMINATE_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10);

  @GuardedBy("this")
  @Nullable private ListenableFuture<AndroidDebugBridge> myFuture;

  /**
   * The full path to the ADB command. The path is platform dependent (i.e. it ends with ".exe" on the Windows platform).
   */
  private final AtomicReference<File> myAdb = new AtomicReference<>();

  /**
   * adb initialization and termination could occur in separate threads (see {@link #terminateDdmlib()} and {@link CreateBridgeTask}.
   * This lock is used to synchronize between the two.
   * */
  private static final Object ADB_INIT_LOCK = new Object();

  public static AdbService getInstance() {
    return ApplicationManager.getApplication().getService(AdbService.class);
  }

  private AdbService() {
    // Synchronize ddmlib log level with the corresponding IDEA log level
    String defaultLogLevel = AdbLogOutput.SystemLogRedirecter.getLogger().isTraceEnabled()
                   ? Log.LogLevel.VERBOSE.getStringValue()
                   : AdbLogOutput.SystemLogRedirecter.getLogger().isDebugEnabled()
                     ? Log.LogLevel.DEBUG.getStringValue()
                     : Log.LogLevel.INFO.getStringValue();
    DdmPreferences.setLogLevel(defaultLogLevel);
    DdmPreferences.setTimeOut(ADB_DEFAULT_TIMEOUT_MILLIS);

    Log.addLogger(new AdbLogOutput.SystemLogRedirecter());

    // Ensure ADB is restarted when ADB options are changed
    AdbOptionsService.getInstance().addListener(this);

    // Ensure ADB is terminated when there are no more open projects.
    Application application = ApplicationManager.getApplication();
    application.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosed(@NotNull Project project) {
        // Ideally, android projects counts should be used here.
        // However, such logic would introduce circular dependency(relying AndroidFacet.ID in intellij.android.core).
        // So, we only check if all projects are closed. If yes, terminate adb.
        if (ProjectManager.getInstance().getOpenProjects().length == 0) {
          LOG.info("Ddmlib can be terminated as all projects have been closed");
          application.executeOnPooledThread(() -> {
            try {
              terminateDdmlib();
            }
            catch (TimeoutException e) {
              LOG.warn("Failed to terminate ADB", e);
            }
          });
        }
      }
    });
  }

  @Override
  public void dispose() {
    try {
      terminateDdmlib();
    }
    catch (TimeoutException e) {
      LOG.warn("Failed to terminate ADB within specified timeout", e);
    }
    AdbOptionsService.getInstance().removeListener(this);
  }

  /**
   * Given the path to the ADB command, asynchronously returns a connected {@link AndroidDebugBridge}
   * via a {@link ListenableFuture}.
   *
   * <p>If ADB has not been started yet, or has been in an error state, a new ADB server
   * is started and fully initialized (i.e. {@link AndroidDebugBridge#isConnected()} is {@code true})
   * before the future completes.
   *
   * <p>If ADB was previously started and is in good state, the future immediately succeeds, i.e only
   * the very first call is expensive and requires a round-trip to another thread.
   *
   * <p>The returned future always completes within the {@link AndroidDebugBridge#DEFAULT_START_ADB_TIMEOUT_MILLIS} timeout.
   * <p>The returned future will contain an exception if  ADB cannot be successfully be initialized within
   * the timeout.
   * <p>The returned future may be cancelled in case of concurrent ADB termination or object disposal.
   * @param adb The full path to the ADB command. See {@link #myAdb}
   */
  public synchronized ListenableFuture<AndroidDebugBridge> getDebugBridge(@NotNull File adb) {
    myAdb.set(adb);

    // Cancel previous requests if they were unsuccessful
    boolean terminateDdmlibFirst;
    if (myFuture != null && myFuture.isDone() && !wasSuccessful(myFuture)) {
      LOG.info("Cancelling current future since it finished with a failure", getFutureException(myFuture));
      cancelCurrentFuture();
      terminateDdmlibFirst = true;
    } else {
      terminateDdmlibFirst = false;
    }

    if (myFuture == null) {
      Future<BridgeConnectionResult> future = ApplicationManager.getApplication().executeOnPooledThread(new CreateBridgeTask(adb, () -> {
        if (terminateDdmlibFirst) {
          try {
            shutdownAndroidDebugBridge(ADB_TERMINATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
          }
          catch (TimeoutException e) {
            throw new RuntimeException(e);
          }
        }
      }, DEFAULT_START_ADB_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

      myFuture = makeListenableFuture(future);
    }

    return myFuture;
  }

  @Nullable
  private static <V> Throwable getFutureException(ListenableFuture<V> future) {
    if (!future.isDone()) {
      return null;
    }
    try {
      future.get();
      return null;
    } catch (Exception e) {
      return e;
    }
  }

  public synchronized void terminateDdmlib() throws TimeoutException {
    cancelCurrentFuture();
    shutdownAndroidDebugBridge(ADB_TERMINATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
  }

  private static void shutdownAndroidDebugBridge(long timeout, @NotNull TimeUnit unit) throws TimeoutException {
    LOG.info("Terminating ADB connection");

    synchronized (ADB_INIT_LOCK) {
      if (!AndroidDebugBridge.disconnectBridge(timeout, unit)) {
        LOG.warn("ADB connection did not terminate within specified timeout");
        throw new TimeoutException("ADB did not terminate within the specified timeout");
      }

      AndroidDebugBridge.terminate();
      LOG.info("ADB connection successfully terminated");
    }
  }

  @VisibleForTesting
  synchronized void cancelFutureForTesting() {
    assert myFuture != null;
    myFuture.cancel(true);
  }

  private synchronized void cancelCurrentFuture() {
    if (myFuture != null) {
      myFuture.cancel(true);
      myFuture = null;
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
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      File adb = myAdb.get();
      // we use the presence of myAdb as an indication that adb was started earlier
      if (adb != null) {
        try {
          LOG.info("Terminating adb server");
          terminateDdmlib();

          LOG.info("Restart adb server");
          getDebugBridge(adb).get();
        }
        catch (TimeoutException | InterruptedException | ExecutionException e) {
          LOG.warn("Error restarting ADB", e);
        }
      }
    });
  }

  private static class CreateBridgeTask implements Callable<BridgeConnectionResult> {
    private final File myAdb;
    private final Runnable myPreCreateAction;
    private final long myTimeout;
    private final TimeUnit myUnit;

    private CreateBridgeTask(@NotNull File adb, Runnable preCreateAction, long timeout, TimeUnit unit) {
      myAdb = adb;
      myPreCreateAction = preCreateAction;
      myTimeout = timeout;
      myUnit = unit;
    }

    @Override
    public BridgeConnectionResult call() {
      TimeoutRemainder rem = new TimeoutRemainder(myTimeout, myUnit);

      LOG.info("Initializing adb using: " + myAdb.getAbsolutePath());

      try {
        myPreCreateAction.run();
      } catch (Exception e) {
        return BridgeConnectionResult.make("Unable to prepare for adb server creation: " + e.getMessage());
      }

      AndroidDebugBridge bridge;
      AdbLogOutput.ToStringLogger toStringLogger = new AdbLogOutput.ToStringLogger();
      Log.addLogger(toStringLogger);
      try {
        AdbInitOptions.Builder options = AdbInitOptions.builder();
        options.setClientSupportEnabled(true); // IDE needs client monitoring support.
        options.useJdwpProxyService(StudioFlags.ENABLE_JDWP_PROXY_SERVICE.get());
        options.withEnv("ADB_LIBUSB", AdbOptionsService.getInstance().shouldUseLibusb() ? "1" : "0");

        // Enables Open Screen mDNS implementation in ADB host.
        // See https://android-review.googlesource.com/c/platform/packages/modules/adb/+/1549744
        options.withEnv("ADB_MDNS_OPENSCREEN", AdbOptionsService.getInstance().shouldUseMdnsOpenScreen() ? "1" : "0");

        if (ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode()) {
          // adb accesses $HOME/.android, which isn't allowed when running in the bazel sandbox
          options.withEnv("HOME", Files.createTempDir().getAbsolutePath());
        }
        if (AdbOptionsService.getInstance().shouldUseUserManagedAdb()) {
          options.enableUserManagedAdbMode(AdbOptionsService.getInstance().getUserManagedAdbPort());
        }
        synchronized (ADB_INIT_LOCK) {
          AndroidDebugBridge.init(options.build());
          bridge = AndroidDebugBridge.createBridge(myAdb.getPath(), false, rem.getRemainingUnits(), myUnit);
        }

        if (bridge == null) {
          return BridgeConnectionResult.make("Unable to start adb server: " + toStringLogger.getOutput());
        }

        while (!bridge.isConnected()) {
          if (rem.getRemainingUnits() <= 0) {
            return BridgeConnectionResult.make("Timed out attempting to connect to adb: " + toStringLogger.getOutput());
          }
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

    @NotNull
    public static BridgeConnectionResult make(@NotNull AndroidDebugBridge bridge) {
      return new BridgeConnectionResult(bridge, null);
    }

    @NotNull
    public static BridgeConnectionResult make(@NotNull String error) {
      return new BridgeConnectionResult(null, error);
    }
  }

  /**
   * Returns a {@link ListenableFuture}&lt;{@link AndroidDebugBridge}&gt; from a {@link Future}&lt;{@link BridgeConnectionResult}&gt;
   */
  @NotNull
  private static ListenableFuture<AndroidDebugBridge> makeListenableFuture(@NotNull final Future<BridgeConnectionResult> delegate) {
    final SettableFuture<AndroidDebugBridge> future = SettableFuture.create();

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        // No need for timeout as the underlying delegate already uses a timeout
        BridgeConnectionResult value = delegate.get();
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
    });

    return future;
  }

  @NotNull
  public static String getDebugBridgeDiagnosticErrorMessage(@NotNull Throwable t, @NotNull File adb) {
    // If we cannot connect to ADB in a reasonable amount of time (10 seconds timeout in AdbService), then something is seriously
    // wrong. The only identified reason so far is that some machines have incompatible versions of adb that were already running.
    // e.g. Genymotion, some HTC flashing software, Ubuntu's adb package may all conflict with the version of adb in the SDK.
    // A timeout can also happen if the user's hosts file points localhost to the wrong address.
    String msg;
    if (t.getMessage() != null) {
      msg = t.getMessage();
    }
    else {
      msg = String.format("Unable to establish a connection to adb.\n\n" +
                          "Check the Event Log for possible issues.\n" +
                          "This can happen if you have an incompatible version of adb running already,\n" +
                          "or if localhost is pointing to the wrong address.\n" +
                          "Try re-opening %1$s after killing any existing adb daemons and verifying that your\n" +
                          "localhost entry is pointing to 127.0.0.1 or ::1 for IPv4 or IPv6, respectively.\n\n" +
                          "If this happens repeatedly, please file a bug at http://b.android.com including the following:\n" +
                          "  1. Output of the command: '%2$s devices'\n" +
                          "  2. Your idea.log file (Help | Show Log in Explorer)\n",
                          ApplicationNamesInfo.getInstance().getProductName(), adb.getAbsolutePath());
    }
    return msg;
  }
}
