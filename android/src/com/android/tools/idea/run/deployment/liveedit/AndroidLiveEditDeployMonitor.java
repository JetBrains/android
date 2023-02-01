/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit;

import static com.android.tools.idea.run.deployment.liveedit.ErrorReporterKt.errorMessage;
import static com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.createRecomposeErrorStatus;
import static com.android.tools.idea.run.deployment.liveedit.PrebuildChecksKt.PrebuildChecks;

import com.android.annotations.Nullable;
import com.android.annotations.Trace;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.intellij.util.ThreeState;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.AdbClient;
import com.android.tools.deployer.AdbInstaller;
import com.android.tools.deployer.Installer;
import com.android.tools.deployer.MetricsRecorder;
import com.android.tools.deployer.tasks.LiveUpdateDeployer;
import com.android.tools.idea.editors.literals.LiveEditService;
import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration;
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.util.StudioPathManager;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.LiveEditEvent;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

/**
 * Helper to set up Live Literal deployment monitoring.
 *
 * Since the UI / UX of this is still not fully agreed upon. This class is design to have MVP like
 * functionality just enough for compose user group to dogfood for now.
 *
 * The LiveEdit change detection & handling flow is as follows:
 * There are three thread contexts:
 * - The UI thread, which reports PSI events
 * - The LiveEditService executor, which queues changes and schedules
 *   LiveEdit pushes. Single-threaded.
 * - The AndroidLiveEditDeployMonitor executor, which handles
 *   compile/push of LiveEdit changes. Single-threaded.
 *
 * ┌──────────┐         ┌───────────┐
 * │ UI Thread├─────────┤PSIListener├─handleChangeEvent()─────────────────────────►
 * └──────────┘         └──────────┬┘
 *                                 │
 * ┌───────────────────────┐   ┌───▼────────┐
 * │LiveEditServiceExecutor├───┤EditListener├─────────────────────────────────────►
 * └───────────────────────┘   └──────┬─────┘
 *                                    │
 *                                    │                       ┌─────┐
 *                                    ├──────────────────────►│QUEUE│
 *                                    │                       └──┬──┘
 *                                    │ schedule()               │
 * ┌──────────────────────────┐       │                          │
 * │AndroidEditServiceExecutor├───────▼──────────────────────────▼──processChanges()
 * └──────────────────────────┘
 *
 * It is important that both executors owned by LiveEdit are single-threaded,
 * in order to ensure that each processes events serially without any races.
 *
 * LiveEditService registers a single PSI listener with the PsiManager.
 * This listener receives callbacks on the UI thread when PSI
 * events are generated. There is one LiveEditService instance per Project.
 *
 * AndroidLiveEditDeployMonitor registers one LiveEditService.EditListener
 * per Project with the corresponding Project's LiveEditService. When the
 * LiveEditService receives PSI events, the listener receives a callback
 * on a single-threaded application thread pool owned by the
 * LiveEditService.
 *
 * The EditListener callback enqueues the event in a collection of
 * "unhandled" events, schedules a LiveEdit compile+push, and returns
 * quickly to allow the thread pool to continue enqueuing events.
 *
 * The scheduled LiveEdit compile+push is executed on a single-threaded
 * executor owned by the EditListener. It handles changes as follows:
 * 1. Lock the queue  of unhandled changes
 * 2. Make a copy of the queue, clear the queue, then unlock the queue
 * 3. If the copy is empty, return
 * 4. Attempt to compile and push the copied changes
 * 5. If the compilation is successful, return.
 * 6. If the compilation is cancelled, lock queue, read-add the removed
 * events, then schedule another compile+push
 *
 * Compilation may be cancelled by PSI write actions, such as the user
 * continuing to type after making a change. It may also be prevented by
 * an ongoing write action, or a PSI write action from another source,
 * which is why it is safer to schedule a retry rather than assuming
 * whatever PSI modification cancelled the change will cause a LiveEdit
 * push.
 *
 * Note that this retry logic does NOT apply if the compilation explicitly
 * fails; only if it is cancelled by PSI write actions.
 *
 * Compilation is responsible for handling duplicate changes
 * originating from the same file, and performs de-duplication logic to
 * ensure that the same file is not re-compiled multiple times.
 */
public class AndroidLiveEditDeployMonitor implements Disposable {

  // TODO: The logging is overly excessive for now given we have no UI to provide feedback to the user
  // when things go wrong. This will be changed in the final product.
  private static final LogWrapper LOGGER = new LogWrapper(Logger.getInstance(AndroidLiveEditDeployMonitor.class));

  private final @NotNull Project project;

  private @Nullable String applicationId;

  private final ScheduledExecutorService methodChangesExecutor = Executors.newSingleThreadScheduledExecutor();

  private final DeviceStatusManager deviceStatusManager = new DeviceStatusManager();

  private final DeviceEventWatcher deviceWatcher = new DeviceEventWatcher();

  // In manual mode, we buffer events until user triggers a LE push.
  private final ArrayList<EditEvent> bufferedEvents = new ArrayList<>();

  // For every files a user modify, we keep track of whether we were able to successfully compile it. As long as one file has an error,
  // LE status remains in Paused state.
  private final Set<String> filesWithCompilationErrors = new HashSet<>();

  private AtomicReference<Long> gradleTimeSync = new AtomicReference<>(Integer.toUnsignedLong(0));

  private final LiveEditCompiler compiler;

  public void resetState() {
    bufferedEvents.clear();
    filesWithCompilationErrors.clear();
    compiler.resetState();
  }

  @NotNull
  public Set<IDevice> devices() {
    return deviceStatusManager.devices();
  }

  @NotNull
  public LiveEditStatus status(@NotNull IDevice device) {
    LiveEditStatus status = deviceStatusManager.get(device);
    return status == null ? LiveEditStatus.Disabled.INSTANCE : status;
  }

  // Care should be given when modifying this field to preserve atomicity.
  private final ConcurrentLinkedQueue<EditEvent> changedMethodQueue = new ConcurrentLinkedQueue<>();


  // This method is invoked on the listener executor thread in LiveEditService and does not block the UI thread.
  public void onPsiChanged(EditEvent event) {
    if (!LiveEditApplicationConfiguration.getInstance().isLiveEdit()) {
      return;
    }

    if (StringUtil.isEmpty(applicationId)) {
      return;
    }

    if (deviceStatusManager.isUnrecoverable() || deviceStatusManager.isDisabled()) {
      return;
    }

    if (GradleSyncState.getInstance(project).isSyncNeeded() != ThreeState.NO ||
        gradleTimeSync.get().compareTo(GradleSyncState.getInstance(project).getLastSyncFinishedTimeStamp()) != 0) {
      updateEditStatus(LiveEditStatus.SyncNeeded.INSTANCE);
      return;
    }

    changedMethodQueue.add(event);
    methodChangesExecutor.schedule(this::processQueuedChanges, LiveEditAdvancedConfiguration.getInstance().getRefreshRateMs(),
                                   TimeUnit.MILLISECONDS);
  }

  private void processQueuedChanges() {
    if (changedMethodQueue.isEmpty()) {
      return;
    }

    List<EditEvent> copy = new ArrayList<>();
    changedMethodQueue.removeIf(e -> {
      copy.add(e);
      return true;
    });

    deviceStatusManager.update(status -> status.unrecoverable() ? status : LiveEditStatus.InProgress.INSTANCE);

    if (!handleChangedMethods(project, copy)) {
      changedMethodQueue.addAll(copy);
      methodChangesExecutor.schedule(this::processQueuedChanges, LiveEditAdvancedConfiguration.getInstance().getRefreshRateMs(),
                                     TimeUnit.MILLISECONDS);
    }
  }

  public AndroidLiveEditDeployMonitor(@NotNull LiveEditService liveEditService, @NotNull Project project) {
    this.project = project;
    this.compiler = new LiveEditCompiler(project);
    gradleTimeSync.set(GradleSyncState.getInstance(project).getLastSyncFinishedTimeStamp());
    Disposer.register(liveEditService, this);

    deviceWatcher.addListener(this::handleAdbEvents);
    liveEditService.getDeviceConnection().addClientChangeListener(deviceWatcher);
    liveEditService.getDeviceConnection().addDeviceChangeListener(deviceWatcher);

    deviceStatusManager.addListener(this::handleDeviceStatusChange);
  }

  @Override
  public void dispose() {
    deviceStatusManager.clear();
    deviceWatcher.clearListeners();
    methodChangesExecutor.shutdownNow();
  }

  public LiveEditCompiler getCompiler() {
    return compiler;
  }

  public Callable<?> getCallback(String applicationId, IDevice device) {
    if (!LiveEditApplicationConfiguration.getInstance().isLiveEdit()) {
      LOGGER.info("Live Edit on device disabled via settings.");
      return null;
    }

    if (!supportLiveEdits(device)) {
      LOGGER.info("Live edit not support for device %s targeting app %s", project.getName(), applicationId);
      return null;
    }

    LOGGER.info("Creating monitor for project %s targeting app %s", project.getName(), applicationId);

    // Initialize EditStatus for current device.
    deviceStatusManager.addDevice(device, LiveEditStatus.Loading.INSTANCE);

    return () -> methodChangesExecutor
      .schedule(
        () -> {
          this.applicationId = applicationId;
          this.gradleTimeSync.set(GradleSyncState.getInstance(project).getLastSyncFinishedTimeStamp());
          LiveEditService.getInstance(project).resetState();
          deviceWatcher.setApplicationId(applicationId);
        },
        0L,
        TimeUnit.NANOSECONDS)
      .get();
  }



  // Triggered from LiveEdit manual mode. Use buffered changes.
  @Trace
  public void onManualLETrigger(Project project) {
    methodChangesExecutor.schedule(this::doOnManualLETrigger, 0, TimeUnit.MILLISECONDS);
  }

  private void doOnManualLETrigger() {

    // If user to trigger a LE push twice in a row with compilation errors, the second trigger would set the state to "synced" even
    // though the compilation error prevented a push on the first trigger
    if (bufferedEvents.isEmpty()) {
      return;
    }

    updateEditStatus(LiveEditStatus.InProgress.INSTANCE);

    while(!processChanges(project, bufferedEvents, LiveEditEvent.Mode.MANUAL)) {
        LOGGER.info("ProcessChanges was interrupted");
    }
    bufferedEvents.clear();
  }



  @Trace
  boolean handleChangedMethods(Project project, List<EditEvent> changes) {
    LOGGER.info("Change detected for project %s targeting app %s", project.getName(), applicationId);

    // In manual mode, we store changes and update status but defer processing.
    if (LiveEditService.Companion.isLeTriggerManual()) {
      updateEditStatus(LiveEditStatus.OutOfDate.INSTANCE);

      if (bufferedEvents.size() < 2000) {
        bufferedEvents.addAll(changes);
      } else {
        // Something is wrong. Discard event otherwise we will run Out Of Memory
        updateEditStatus(LiveEditStatus.createErrorStatus("Too many buffered LE keystrokes. Redeploy app."));
      }

      return true;
    }

    return processChanges(project, changes, LiveEditEvent.Mode.AUTO);
  }

  @Trace
  /**
   * @return true is the changes were successfully processed (without being interrupted). Otherwise, false.
   */
  private boolean processChanges(Project project, List<EditEvent> changes, LiveEditEvent.Mode mode) {
    LiveEditEvent.Builder event = LiveEditEvent.newBuilder().setMode(mode);

    long start = System.nanoTime();
    long compileFinish, pushFinish;

    Optional<LiveEditCompilerOutput> compiled;

    try {
      PrebuildChecks(project, changes);
      List<LiveEditCompilerInput> inputs = changes.stream().map(
        change ->
          new LiveEditCompilerInput(change.getFile(), change.getOrigin(), change.getParentGroup()))
        .collect(Collectors.toList());
      compiled = compiler.compile(inputs, !LiveEditService.isLeTriggerManual());
      if (compiled.isEmpty()) {
        return false;
      }

      // Remove files successfully compiled from the error set.
      for (EditEvent change : changes) {
        filesWithCompilationErrors.remove(change.getFile().getName());
      }
    } catch (LiveEditUpdateException e) {
      boolean recoverable = e.getError().getRecoverable();
      if (recoverable) {
        filesWithCompilationErrors.add(e.getSource().getName());
      }
      updateEditStatus(recoverable ?
                       LiveEditStatus.createPausedStatus(errorMessage(e)) :
                       LiveEditStatus.createErrorStatus(errorMessage(e)));
      return true;
    }

    final LiveEditCompilerOutput finalCompiled = compiled.get();
    event.setHasNonCompose(finalCompiled.getResetState());

    compileFinish = System.nanoTime();
    event.setCompileDurationMs(TimeUnit.NANOSECONDS.toMillis(compileFinish - start));
    LOGGER.info("LiveEdit compile completed in %dms", event.getCompileDurationMs());

    Optional<LiveUpdateDeployer.UpdateLiveEditError> error = deviceIterator()
      .map(device -> pushUpdatesToDevice(applicationId, device, finalCompiled).errors)
      .flatMap(List::stream)
      .findFirst();

    if (error.isPresent()) {
      event.setStatus(errorToStatus(error.get()));
    } else {
      event.setStatus(LiveEditEvent.Status.SUCCESS);
    }

    pushFinish = System.nanoTime();
    event.setPushDurationMs(TimeUnit.NANOSECONDS.toMillis(pushFinish - compileFinish));
    LOGGER.info("LiveEdit push completed in %dms", event.getPushDurationMs());

    logLiveEditEvent(event);
    return true;
  }

  private void scheduleErrorPolling(LiveUpdateDeployer deployer, Installer installer, AdbClient adb, String packageName) {
    ScheduledExecutorService scheduler = JobScheduler.getScheduler();
    ScheduledFuture<?> statusPolling = scheduler.scheduleWithFixedDelay(() -> {
      List<Deploy.ComposeException> errors = deployer.retrieveComposeStatus(installer, adb, packageName);
      if (!errors.isEmpty()) {
        updateEditStatus(createRecomposeErrorStatus(errors.get(0).getMessage()));
      }
    }, 2, 2, TimeUnit.SECONDS);
    // Schedule a cancel after 10 seconds.
    scheduler.schedule(() -> {statusPolling.cancel(true);}, 10, TimeUnit.SECONDS);
  }


  private static LiveEditEvent.Status errorToStatus(LiveUpdateDeployer.UpdateLiveEditError error) {
    switch(error.getType()) {
      case ADDED_METHOD:
        return LiveEditEvent.Status.UNSUPPORTED_ADDED_METHOD;
      case REMOVED_METHOD:
        return LiveEditEvent.Status.UNSUPPORTED_REMOVED_METHOD;
      case ADDED_CLASS:
        return LiveEditEvent.Status.UNSUPPORTED_ADDED_CLASS;
      case ADDED_FIELD:
      case MODIFIED_FIELD:
        return LiveEditEvent.Status.UNSUPPORTED_ADDED_FIELD;
      case REMOVED_FIELD:
        return LiveEditEvent.Status.UNSUPPORTED_REMOVED_FIELD;
      case MODIFIED_SUPER:
      case ADDED_INTERFACE:
      case REMOVED_INTERFACE:
        return LiveEditEvent.Status.UNSUPPORTED_MODIFY_INHERITANCE;
      case UNSUPPORTED_COMPOSE_VERSION:
        // TODO: Add new event.
        return LiveEditEvent.Status.UNKNOWN;
      default:
        return LiveEditEvent.Status.UNKNOWN;
    }
  }

  private static final Random random = new Random();
  private static void logLiveEditEvent(LiveEditEvent.Builder event) {
    // Because LiveEdit could conceivably run every time the user stops typing, we log only 10% of events.
    if (random.nextDouble() < 0.1) {
      UsageTracker.log(AndroidStudioEvent.newBuilder().setCategory(AndroidStudioEvent.EventCategory.DEPLOYMENT)
                         .setKind(AndroidStudioEvent.EventKind.LIVE_EDIT_EVENT).setLiveEditEvent(event));
    }
  }

  private void updateEditStatus(@NotNull IDevice device, @NotNull LiveEditStatus status) {
    deviceStatusManager.update(device, status);
  }

  private void updateEditStatus(@NotNull LiveEditStatus status) {
    deviceStatusManager.update(status);
  }

  private void handleDeviceStatusChange(Map<IDevice, LiveEditStatus> map) {
    // Force the UI to redraw with the new status. See com.intellij.openapi.actionSystem.AnAction#update().
    ActivityTracker.getInstance().inc();
  }

  private void handleAdbEvents(IDevice device, DeviceEvent event) {
    if (!deviceStatusManager.devices().contains(device)) {
      return;
    }

    switch (event) {
      case DEVICE_DISCONNECT ->
        deviceStatusManager.update(device, LiveEditStatus.Disabled.INSTANCE);
      case APPLICATION_CONNECT ->
        // If the device was previously in LOADING state, we are now ready to receive live edits.
        deviceStatusManager.update(device, status -> status == LiveEditStatus.Loading.INSTANCE ? LiveEditStatus.UpToDate.INSTANCE : status);
      case APPLICATION_DISCONNECT ->
        // If the application disconnects while in the Loading status (if it's the current session that disconnected while loading, we
        // would've gotten an APPLICATION_CONNECT event first before the Client disconnected), that means it is the disconnect from the
        // previous session that has finally arrived in Studio through ADB. Ignore the event in this case.
        deviceStatusManager.update(device, status -> status != LiveEditStatus.Loading.INSTANCE ? LiveEditStatus.Disabled.INSTANCE : status);
      case DEBUGGER_CONNECT ->
        deviceStatusManager.update(device, LiveEditStatus.DebuggerAttached.INSTANCE);
      case DEBUGGER_DISCONNECT ->
        // Don't return to up-to-date state if another state transition has taken place since.
        deviceStatusManager.update(device, status -> status == LiveEditStatus.DebuggerAttached.INSTANCE
                                                     ? LiveEditStatus.UpToDate.INSTANCE
                                                     : status);
    }
  }

  private Stream<IDevice> deviceIterator() {
    return deviceStatusManager.devices().stream().filter(IDevice::isOnline);
  }

  private static Installer newInstaller(IDevice device) {
    MetricsRecorder metrics = new MetricsRecorder();
    AdbClient adb = new AdbClient(device, LOGGER);
    return  new AdbInstaller(getLocalInstaller(), adb, metrics.getDeployMetrics(), LOGGER, AdbInstaller.Mode.DAEMON);
  }

  private LiveUpdateDeployer.UpdateLiveEditResult pushUpdatesToDevice(
      String applicationId, IDevice device, LiveEditCompilerOutput update) {
    LiveUpdateDeployer deployer = new LiveUpdateDeployer(LOGGER);
    Installer installer = newInstaller(device);
    AdbClient adb = new AdbClient(device, LOGGER);

    boolean useDebugMode = LiveEditAdvancedConfiguration.getInstance().getUseDebugMode();
    boolean usePartialRecompose = LiveEditAdvancedConfiguration.getInstance().getUsePartialRecompose() && !update.getResetState();

    LiveUpdateDeployer.UpdateLiveEditsParam param =
      new LiveUpdateDeployer.UpdateLiveEditsParam(
        update.getClasses(),
        update.getSupportClasses(),
        update.getGroupIds(),
        usePartialRecompose,
        useDebugMode);

    if (useDebugMode) {
      for (String className : update.getSupportClasses().keySet()) {
        byte[] bytecode = update.getSupportClasses().get(className);
        writeDebugToTmp(className.replaceAll("/", ".") + ".class", bytecode);
      }

      for (String supportClassName : update.getSupportClasses().keySet()) {
        byte[] bytecode = update.getSupportClasses().get(supportClassName);
        writeDebugToTmp(supportClassName.replaceAll("/", ".") + ".class", bytecode);
      }
    }

    LiveUpdateDeployer.UpdateLiveEditResult result = deployer.updateLiveEdit(installer, adb, applicationId, param);

    if (filesWithCompilationErrors.isEmpty()) {
      updateEditStatus(device, LiveEditStatus.UpToDate.INSTANCE);
    } else {
      Optional<String> errorFilename = filesWithCompilationErrors.stream().sequential().findFirst();
      String errorMsg = ErrorReporterKt.leErrorMessage(LiveEditUpdateException.Error.COMPILATION_ERROR, errorFilename.get());
      updateEditStatus(device, LiveEditStatus.createPausedStatus(errorMsg));
    }
    scheduleErrorPolling(deployer, installer, adb, applicationId);

    if (!result.errors.isEmpty()) {
      LiveUpdateDeployer.UpdateLiveEditError firstProblem = result.errors.get(0);
      if (firstProblem.getType() == Deploy.UnsupportedChange.Type.UNSUPPORTED_COMPOSE_VERSION) {
        updateEditStatus(device, LiveEditStatus.createComposeVersionError(firstProblem.getMessage()));
      } else {
        updateEditStatus(device, LiveEditStatus.createErrorStatus(firstProblem.getMessage()));
      }
    }
    return result;
  }

  private static void writeDebugToTmp(String name, byte[] data) {
    String tmpPath = System.getProperty("java.io.tmpdir");
    if (tmpPath == null) {
      return;
    }
    Path path = Paths.get(tmpPath, name);
    try {
      Files.write(path, data);
      LOGGER.info("Wrote debug file at '%s'", path.toAbsolutePath());
    }
    catch (IOException e) {
      LOGGER.info("Unable to write debug file '%s'", path.toAbsolutePath());
    }
  }

  private static boolean supportLiveEdits(IDevice device) {
    return device.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.R);
  }

  // TODO: Unify this part.
  private static String getLocalInstaller() {
    Path path;
    if (StudioPathManager.isRunningFromSources()) {
      // Development mode
      path = StudioPathManager.resolvePathFromSourcesRoot("bazel-bin/tools/base/deploy/installer/android-installer");
    } else {
      path = Paths.get(PathManager.getHomePath(), "plugins/android/resources/installer");
    }
    return path.toString();
  }
}
