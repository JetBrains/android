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

import com.android.adblib.AdbSession;
import com.android.adblib.CoroutineScopeCache;
import com.android.adblib.ddmlibcompatibility.debugging.AdbLibClientManagerFactory;
import com.android.annotations.concurrency.WorkerThread;
import com.android.ddmlib.*;
import com.android.ddmlib.clientmanager.ClientManager;
import com.android.tools.idea.adblib.AdbLibApplicationService;
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseListener;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.android.ddmlib.AndroidDebugBridge.DEFAULT_START_ADB_TIMEOUT_MILLIS;

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
@Service
public final class AdbService implements Disposable, AdbOptionsService.AdbOptionsListener, AndroidDebugBridge.IDebugBridgeChangeListener {
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

  /**
   * Main sequential executor to guarantee atomicity and ordering of critical operations in this class.
   * The main purpose is to avoid explicit locking and race conditions accessing {@link AndroidDebugBridge}.
   */
  private final @NotNull ListeningExecutorService mySequentialExecutor = MoreExecutors.listeningDecorator(
    SequentialTaskExecutor.createSequentialApplicationPoolExecutor("AdbService Executor"));

  /**
   * Underlying implementation of AdbService.
   */
  private final @NotNull AdbService.Implementation myImplementation = new Implementation();

  /**
   * Tracks whether ADB_MDNS_OPENSCREEN env. variable can be used when starting ADB.
   * <p>
   * This is required because some version of ADB crash at startup when ADB_MDNS_OPENSCREEN env variable is set.
   * We detect this pattern and prevent ADB_MDNS_OPENSCREEN to be set on the next ADB restart.
   */
  private boolean myAllowMdnsOpenscreen = true;

  /**
   * Tracks whether we have shown the notification popup about ADB crashing during initialization.
   * We use a static variable to ensure we show the notification only once per Android Studio session.
   */
  private boolean myInitializationErrorShown = false;

  /**
   * Anticipated adb version for ADB_MDNS_OPENSCREEN option fix.
   */
  private static final String MDNS_OPENSCREEN_FIX_ADB_VERSION = "1.0.42";

  @Override
  public void bridgeChanged(@Nullable AndroidDebugBridge bridge) {
  }

  @Override
  public void initializationError(@NotNull Exception exception) {
    // b/217251994 - ADB crashes when ADB_MDNS_OPENSCREEN is set on certain Windows configs.
    // Work around by disabling ADB_MDNS_OPENSCREEN and notifying the user that ADB WiFi is disabled.
    if (!SystemInfo.isWindows ||
        !AdbOptionsService.getInstance().shouldUseMdnsOpenScreen() ||
        !(exception instanceof IOException) ||
        !exception.getMessage().startsWith("An existing connection was forcibly closed by the remote host")) {
      return;
    }

    AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
    if (bridge == null) {
      return;
    }

    AdbVersion version = bridge.getCurrentAdbVersion();
    if (version == null || version.compareTo(AdbVersion.parseFrom(MDNS_OPENSCREEN_FIX_ADB_VERSION)) >= 0) {
      return;
    }

    Log.w("Remote shutdown of adb host was detected, attempting to restart server without MDNS Openscreen.", exception);
    String helpMessage = String.format(
      "Error initializing adb with MDNS Openscreen enabled.\n" +
      "Attempting restart adb with option disabled.\n" +
      "Try updating to a newer version of ADB (%s or later).",
      MDNS_OPENSCREEN_FIX_ADB_VERSION);
    Notification notification = NotificationGroup.balloonGroup("Adb Service")
      .createNotification(helpMessage, NotificationType.WARNING)
      .setImportant(true);
    Arrays.stream(ProjectManager.getInstance().getOpenProjects()).forEach(notification::notify);

    myAllowMdnsOpenscreen = false;

    if (!myInitializationErrorShown) {
      PopupUtil.showBalloonForActiveComponent(helpMessage, MessageType.WARNING);
      myInitializationErrorShown = true;
    }
    try {
      terminateDdmlib();
    }
    catch (TimeoutException ignored) {
    }
    // Leave until next getBridge caller to reinitialize.
  }

  public static AdbService getInstance() {
    return ApplicationManager.getApplication().getService(AdbService.class);
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

  /**
   * Given the path to the ADB command, asynchronously returns a connected {@link AndroidDebugBridge}
   * via a {@link ListenableFuture}.
   *
   * <p>If ADB has not been started yet, or has been in an error state, a new ADB server
   * is started and fully initialized (i.e. {@link AndroidDebugBridge#isConnected()} is {@code true})
   * before the future completes.
   *
   * <p>If ADB was previously started and is in good state, the future returns the previous started adb,
   * i.e only the very first call is expensive and requires a round-trip to another thread.
   *
   * <p>The returned future always completes within the {@link AndroidDebugBridge#DEFAULT_START_ADB_TIMEOUT_MILLIS} timeout.
   * <p>The returned future will contain an exception if ADB cannot be successfully be initialized within the timeout.
   * <p>The returned future may be cancelled in case of concurrent ADB termination or object disposal.
   *
   * @param adb The full path to the ADB command.
   */
  public @NotNull ListenableFuture<AndroidDebugBridge> getDebugBridge(@NotNull File adb) {
    return mySequentialExecutor.submit(() -> myImplementation.getAndroidDebugBridge(adb));
  }

  /**
   * Asynchronously returns a connected {@link AndroidDebugBridge}
   * via a {@link ListenableFuture}.
   *
   * <p>If ADB has not been started yet, or has been in an error state, a new ADB server
   * is started and fully initialized (i.e. {@link AndroidDebugBridge#isConnected()} is {@code true})
   * before the future completes.
   *
   * <p>If ADB was previously started and is in good state, the future returns the previous started adb,
   * i.e only the very first call is expensive and requires a round-trip to another thread.
   *
   * <p>The returned future always completes within the {@link AndroidDebugBridge#DEFAULT_START_ADB_TIMEOUT_MILLIS} timeout.
   * <p>The returned future will contain an exception if ADB cannot be successfully be initialized within the timeout.
   * <p>The returned future may be cancelled in case of concurrent ADB termination or object disposal.
   *
   * @param project A {@link Project} that is used to find the ADB executable file.
   */
  public @NotNull ListenableFuture<AndroidDebugBridge> getDebugBridge(@NotNull Project project) {
    AdbFileProvider provider = AdbFileProvider.fromProject(project);
    if (provider == null) {
      LOG.warn("AdbFileProvider is not correctly set up (see AdbFileProviderInitializer)");
      return Futures.immediateFailedFuture(new IllegalStateException("AdbFileProvider is not correctly set up"));
    }
    File adbFile = provider.getAdbFile();
    if (adbFile == null) {
      LOG.warn("The path to the ADB command is not available");
      return Futures.immediateFailedFuture(new FileNotFoundException("The path to the ADB command is not available"));
    }
    return getDebugBridge(adbFile);
  }

  /**
   * Terminates ADB synchronously with a predetermined timeout.
   *
   * @throws TimeoutException when termination did not complete in {@link #ADB_TERMINATE_TIMEOUT_MILLIS} milliseconds
   */
  public void terminateDdmlib() throws TimeoutException {
    try {
      mySequentialExecutor.submit(myImplementation::terminate).get(ADB_TERMINATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException | ExecutionException e) {
      LOG.warn("Failed to terminate ADB", e);
    }
  }

  /**
   * Do not call this method directly. Should only be called by {@link com.intellij.openapi.components.ComponentManager}
   * when Android Studio shuts down.
   */
  @Override
  public void dispose() {
    LOG.info("Disposing AdbService");
    AndroidDebugBridge.removeDebugBridgeChangeListener(this);
    AdbOptionsService.getInstance().removeListener(this);
    try {
      mySequentialExecutor.submit(() -> {
        myImplementation.terminate();
        mySequentialExecutor.shutdownNow();
      }).get(ADB_TERMINATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }
    catch (TimeoutException e) {
      LOG.warn("Failed to dispose AdbService within specified timeout", e);
      return;
    }
    catch (InterruptedException | ExecutionException e) {
      LOG.warn("Error while disposing AdbService");
      return;
    }

    try {
      if (!mySequentialExecutor.awaitTermination(ADB_TERMINATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
        LOG.warn("Failed to shut down executor within specified timeout.");
      }
    }
    catch (InterruptedException e) {
      LOG.warn("Executor shutdown interrupted.", e);
    }
  }

  /**
   * Queues an options changed notification on EXECUTOR. Only called by {@link AdbOptionsService}.
   */
  @Override
  public void optionsChanged() {
    mySequentialExecutor.execute(myImplementation::optionsChanged);
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

    AdbOptionsService.getInstance().addListener(this);
    AndroidDebugBridge.addDebugBridgeChangeListener(this);

    // Ensure ADB is terminated when there are no more open projects.
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
      @Override
      public void projectClosed(@NotNull Project project) {
        // Ideally, android projects counts should be used here.
        // However, such logic would introduce circular dependency(relying AndroidFacet.ID in intellij.android.core).
        // So, we only check if all projects are closed. If yes, terminate adb.
        if (ProjectManager.getInstance().getOpenProjects().length == 0) {
          LOG.info("Ddmlib can be terminated as all projects have been closed");
          //noinspection unused
          Future<?> unused = mySequentialExecutor.submit(myImplementation::terminate);
        }
      }
    });
  }

  private static @NotNull AdbInitOptions getAdbInitOptions() {
    AdbInitOptions.Builder options = AdbInitOptions.builder();
    options.setClientSupportEnabled(true); // IDE needs client monitoring support.
    options.useJdwpProxyService(StudioFlags.ENABLE_JDWP_PROXY_SERVICE.get());
    options.useDdmlibCommandService(StudioFlags.ENABLE_DDMLIB_COMMAND_SERVICE.get());
    options.withEnv("ADB_LIBUSB", AdbOptionsService.getInstance().shouldUseLibusb() ? "1" : "0");
    if (StudioFlags.ADB_WIRELESS_PAIRING_ENABLED.get() && getInstance().myAllowMdnsOpenscreen) {
      // Enables Open Screen mDNS implementation in ADB host.
      // See https://android-review.googlesource.com/c/platform/packages/modules/adb/+/1549744
      options.withEnv("ADB_MDNS_OPENSCREEN", AdbOptionsService.getInstance().shouldUseMdnsOpenScreen() ? "1" : "0");
    }
    getInstance().myAllowMdnsOpenscreen = true;
    if (ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode()) {
      // adb accesses $HOME/.android, which isn't allowed when running in the bazel sandbox
      //noinspection UnstableApiUsage
      options.withEnv("HOME", Files.createTempDir().getAbsolutePath());
    }
    if (AdbOptionsService.getInstance().shouldUseUserManagedAdb()) {
      options.enableUserManagedAdbMode(AdbOptionsService.getInstance().getUserManagedAdbPort());
    }
    options.setClientManager(StudioFlags.ADBLIB_MIGRATION_DDMLIB_CLIENT_MANAGER.get() ?
                             getClientManager() :
                             null);
    return options.build();
  }

  @NotNull
  private static final CoroutineScopeCache.Key<ClientManager> CLIENT_MANAGER_KEY =
    new CoroutineScopeCache.Key<>("client manager for ddmlib compatibility");

  @NotNull
  private static ClientManager getClientManager() {
    AdbSession session = AdbLibApplicationService.getInstance().getSession();
    return session.getCache().getOrPut(CLIENT_MANAGER_KEY, () -> AdbLibClientManagerFactory.createClientManager(session));
  }

  /**
   * An inner class that deals with encapsulating ADB initialization data, as well as starting/stopping ADB.
   * This class deliberately does not synchronize as the parent class ensures all calls are serialized through a sequential executor.
   */
  private static class Implementation {
    /**
     * The full path to the ADB command. The path is platform dependent (i.e. it ends with ".exe" on the Windows platform).
     */
    private @Nullable File myAdbExecutableFile = null;

    /**
     * Creates an {@link Implementation} if there is no valid existing instance, or creates a new instance if existing instance uses
     * a different adb executable file.
     *
     * @param adb file location of ADB
     */
    @WorkerThread
    public @Nullable AndroidDebugBridge getAndroidDebugBridge(@NotNull File adb) {
      AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
      if (bridge == null || !bridge.isConnected()) {
        try {
          bridge = createBridge(adb);
        }
        catch (Exception e) {
          LOG.warn("Error creating adb", e);
        }
      }

      return bridge;
    }

    @WorkerThread
    public void terminate() {
      try {
        LOG.info("Terminating ADB connection");

        if (!AndroidDebugBridge.disconnectBridge(ADB_TERMINATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
          LOG.warn("ADB connection did not terminate within specified timeout");
          throw new TimeoutException("ADB did not terminate within the specified timeout");
        }

        AndroidDebugBridge.terminate();
        LOG.info("ADB connection successfully terminated");
      }
      catch (TimeoutException e) {
        LOG.warn("Timed out waiting for adb to terminate");
      }
    }

    @WorkerThread
    private @NotNull AndroidDebugBridge createBridge(@NotNull File adb) throws Exception {
      terminate();

      TimeoutRemainder rem = new TimeoutRemainder(DEFAULT_START_ADB_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
      LOG.info("Initializing adb using: " + adb.getAbsolutePath());

      AndroidDebugBridge bridge;
      AdbLogOutput.ToStringLogger toStringLogger = new AdbLogOutput.ToStringLogger();
      Log.addLogger(toStringLogger);
      try {
        AndroidDebugBridge.init(getAdbInitOptions());
        bridge = AndroidDebugBridge.createBridge(adb.getPath(), false, rem.getRemainingNanos(), TimeUnit.MILLISECONDS);

        if (bridge == null) {
          throw new Exception("Unable to start adb server: " + toStringLogger.getOutput());
        }

        while (!bridge.isConnected()) {
          if (rem.getRemainingNanos() <= 0) {
            throw new Exception("Timed out attempting to connect to adb: " + toStringLogger.getOutput());
          }
          try {
            TimeUnit.MILLISECONDS.sleep(200);
          }
          catch (InterruptedException e) {
            // if cancelled, don't wait for connection and return immediately
            throw new Exception("Timed out attempting to connect to adb: " + toStringLogger.getOutput());
          }
        }

        myAdbExecutableFile = adb;
        LOG.info("Successfully connected to adb");
        return bridge;
      }
      finally {
        Log.removeLogger(toStringLogger);
      }
    }

    @WorkerThread
    private void optionsChanged() {
      if (myAdbExecutableFile == null) {
        return;
      }

      LOG.info("Options changed. Re-initing/restarting adb server if needed.");
      AndroidDebugBridge.optionsChanged(
        getAdbInitOptions(),
        myAdbExecutableFile.getPath(),
        false,
        ADB_TERMINATE_TIMEOUT_MILLIS,
        DEFAULT_START_ADB_TIMEOUT_MILLIS,
        TimeUnit.MILLISECONDS);
    }
  }
}
