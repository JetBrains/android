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

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;
import static com.android.tools.idea.run.deployment.liveedit.ErrorReporterKt.errorMessage;
import static com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.createRecomposeErrorStatus;
import static com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.createRecomposeRetrievalErrorStatus;
import static com.android.tools.idea.run.deployment.liveedit.PrebuildChecksKt.prebuildChecks;
import static com.android.tools.idea.run.deployment.liveedit.PsiValidatorKt.getPsiValidationState;

import com.android.annotations.Nullable;
import com.android.annotations.Trace;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass;
import com.android.tools.idea.run.deployment.liveedit.desugaring.LiveEditDesugarResponse;
import com.android.tools.analytics.UsageTrackerUtils;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.AdbClient;
import com.android.tools.deployer.AdbInstaller;
import com.android.tools.deployer.Installer;
import com.android.tools.deployer.MetricsRecorder;
import com.android.tools.deployer.tasks.LiveUpdateDeployer;
import com.android.tools.idea.editors.liveedit.LiveEditService;
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
import com.intellij.psi.PsiManager;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import java.io.IOException;
import com.intellij.psi.PsiFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.KotlinFileType;

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
public class LiveEditProjectMonitor implements Disposable {

  // TODO: The logging is overly excessive for now given we have no UI to provide feedback to the user
  // when things go wrong. This will be changed in the final product.
  private static final LogWrapper LOGGER = new LogWrapper(Logger.getInstance(LiveEditProjectMonitor.class));

  private final @NotNull Project project;

  private @Nullable String applicationId;

  // TODO: This is the Thread where we process keystroke but also where we receive appDeploy.
  //       1. A better name would be "mainThreadExecutor".
  //       2. We should also run appRefresh notifications on this thread.
  //       3. We should also run ADB events on this thread.
  private final ScheduledExecutorService mainThreadExecutor = Executors.newSingleThreadScheduledExecutor();

  /**
   * Track the state of the project {@link #project} on devices it has been deployed on as {@link #applicationId}
   */
  private final LiveEditDevices liveEditDevices = new LiveEditDevices();

  private final DeviceEventWatcher deviceWatcher = new DeviceEventWatcher();

  // In manual mode, we buffer files until user triggers a LE push.
  private final ArrayList<PsiFile> bufferedFiles = new ArrayList<>();

  // For every files a user modify, we keep track of whether we were able to successfully compile it. As long as one file has an error,
  // LE status remains in Paused state.
  private final Set<String> filesWithCompilationErrors = new HashSet<>();

  private final AtomicReference<Boolean> intermediateSyncs = new AtomicReference<>(Boolean.FALSE);

  private final LiveEditCompiler compiler;

  // We want to log only a percentage of LE events, but we also always want to log the *first* event after a deployment.
  private final double LE_LOG_FRACTION = 0.1;

  // Random generator used in conjunction with LE_LOG_FRACTION
  private static final Random randomForLogging = new Random();

  private boolean hasLoggedSinceReset = false;

  // Bridge to ADB event (either ddmlib or adblib). We use it to receive device lifecycle events and app (a.k.a Client) lifecycle events.
  private final LiveEditAdbEventsListener adbEventsListener;

  // Care should be given when modifying this field to preserve atomicity.
  private final ConcurrentLinkedQueue<PsiFile> changedFileQueue = new ConcurrentLinkedQueue<>();

  private final ConcurrentHashMap<PsiFile, PsiState> psiSnapshots = new ConcurrentHashMap<>();

  private final MutableIrClassCache irClassCache = new MutableIrClassCache();

  private final AtomicInteger pendingRecompositionStatusPolls = new AtomicInteger(0);

  public static final int NUM_RECOMPOSITION_STATUS_POLLS_PER_EDIT = 5;

  public LiveEditProjectMonitor(@NotNull LiveEditService liveEditService, @NotNull Project project) {
    this(liveEditService, project, new DefaultApkClassProvider());
  }

  public LiveEditProjectMonitor(@NotNull LiveEditService liveEditService,
                                @NotNull Project project,
                                @NotNull ApkClassProvider apkClassProvider) {
    this.project = project;
    this.compiler = new LiveEditCompiler(project, irClassCache, apkClassProvider);
    this.adbEventsListener = liveEditService.getAdbEventsListener();

    Disposer.register(liveEditService, this);
    project.getMessageBus().connect(this)
      .subscribe(PROJECT_SYSTEM_SYNC_TOPIC,
                 (ProjectSystemSyncManager.SyncResultListener)result -> intermediateSyncs.set(Boolean.TRUE));

    // TODO: This maze of listeners is complicated. LiveEditDevices should directly implement LiveEditAdbEventsListener.
    deviceWatcher.addListener(liveEditDevices::handleDeviceLifecycleEvents);
    adbEventsListener.addListener(deviceWatcher);

    liveEditDevices.addListener(this::handleDeviceStatusChange);
  }

  public void resetState() {
    bufferedFiles.clear();
    filesWithCompilationErrors.clear();
    compiler.resetState();
    hasLoggedSinceReset = false;
  }

  @VisibleForTesting
  int numFilesWithCompilationErrors() {
    return filesWithCompilationErrors.size();
  }

  @VisibleForTesting
  MutableIrClassCache getIrClassCache() {
    return irClassCache;
  }

  @NotNull
  public Set<IDevice> devices() {
    return liveEditDevices.devices();
  }

  @NotNull
  public LiveEditStatus status(@NotNull IDevice device) {
    LiveEditDeviceInfo info = liveEditDevices.getInfo(device);
    return info == null ? LiveEditStatus.Disabled.INSTANCE : info.getStatus();
  }

  private void processQueuedChanges() {
    if (changedFileQueue.isEmpty()) {
      return;
    }

    List<PsiFile> copy = new ArrayList<>();
    changedFileQueue.removeIf(e -> {
      copy.add(e);
      return true;
    });

    updateEditableStatus(LiveEditStatus.InProgress.INSTANCE);

    if (!handleChangedMethods(project, copy)) {
      changedFileQueue.addAll(copy);
      mainThreadExecutor.schedule(this::processQueuedChanges, LiveEditAdvancedConfiguration.getInstance().getRefreshRateMs(),
                                  TimeUnit.MILLISECONDS);
    }
  }

  @Override
  public void dispose() {
    // Don't leak deviceWatcher in our ADB bridge listeners.
    adbEventsListener.removeListener(deviceWatcher);
    changedFileQueue.clear();
    liveEditDevices.clear();
    deviceWatcher.clearListeners();
    mainThreadExecutor.shutdownNow();
  }

  public LiveEditCompiler getCompiler() {
    return compiler;
  }

  /**
   * Notifies the monitor that a {@link com.intellij.execution.configurations.RunConfiguration} has just started execution.
   *
   * @param devices The devices that the execution will deploy to.
   * @return true if multi-deploy is detected, false otherwise (this will be removed once multi-deploy is supported)
   */
  public boolean notifyExecution(@NotNull Collection<IDevice> devices) {
    Set<IDevice> newDevices = new HashSet<>(devices);
    newDevices.removeIf(d -> !supportLiveEdits(d));
    Ref<Boolean> multiDeploy = new Ref<>(false);
    liveEditDevices.update((oldDevice, status) -> {
      if (newDevices.contains(oldDevice)) {
        return (status == LiveEditStatus.NoMultiDeploy.INSTANCE) ? LiveEditStatus.Disabled.INSTANCE : status;
      }
      if (status == LiveEditStatus.Disabled.INSTANCE) {
        return status;
      }
      multiDeploy.set(true);
      return LiveEditStatus.NoMultiDeploy.INSTANCE;
    });
    return multiDeploy.get();
  }

  // Called from Android Studio when an app is "Refreshed" (namely Apply Changes or Apply Code Changes) to a device
  public boolean notifyAppRefresh(@NotNull IDevice device) {
    if (!LiveEditApplicationConfiguration.getInstance().isLiveEdit() || !supportLiveEdits(device)) {
      return false;
    }
    liveEditDevices.update(device, LiveEditStatus.UpToDate.INSTANCE);
    return true;
  }

  // Called from Android Studio when an app is deployed (a.k.a Installed / IWIed / Delta-installed) to a device
  public boolean notifyAppDeploy(String applicationId,
                                 IDevice device,
                                 @NotNull LiveEditApp app,
                                 List<VirtualFile> openFiles,
                                 @NotNull Supplier<Boolean> isLiveEditable) throws ExecutionException, InterruptedException {
    if (!isLiveEditable.get()) {
      LOGGER.info("Can not live edit the app due to either non-debuggability or does not use Compose");
      liveEditDevices.clear(device);
      return false;
    }

    if (!LiveEditApplicationConfiguration.getInstance().isLiveEdit()) {
      if (supportLiveEdits(device) && LiveEditService.usesCompose(project)) {
        LiveEditService.getInstance(project).notifyLiveEditAvailability(device);
      }

      LOGGER.info("Live Edit on device disabled via settings.");
      return false;
    }

    if (!supportLiveEdits(device)) {
      LOGGER.info("Live edit not support for device API %d targeting app %s", device.getVersion().getApiLevel(), applicationId);
      liveEditDevices.addDevice(device, LiveEditStatus.UnsupportedVersion.INSTANCE, app);
      return false;
    }

    LOGGER.info("Creating monitor for project %s targeting app %s", project.getName(), applicationId);

    // Initialize EditStatus for current device.
    liveEditDevices.addDevice(device, LiveEditStatus.Loading.INSTANCE, app);

    // This method (notifyAppDeploy) is called from Studio on a random Worker thread. We schedule the data update on the same Executor
    // we process our keystrokes {@link #methodChangesExecutor}
    mainThreadExecutor.submit(() -> {
      this.applicationId = applicationId;
      intermediateSyncs.set(Boolean.FALSE);
      resetState();

      // The app may have connected to ADB before we set up our ADB listeners.
      if (device.getClient(applicationId) != null) {
        updateEditStatus(device, LiveEditStatus.UpToDate.INSTANCE);
      }
      deviceWatcher.setApplicationId(applicationId);

      psiSnapshots.clear();
      updatePsiSnapshots(openFiles);
      irClassCache.clear();
    }).get();

    return true;
  }


  // Called when a new file is open in the editor. Only called on the class-differ code path.
  public void updatePsiSnapshot(VirtualFile file) {
    if (!shouldLiveEdit()) {
      return;
    }

    mainThreadExecutor.submit(() -> updatePsiSnapshots(List.of(file)));
  }

  private void updatePsiSnapshots(List<VirtualFile> files) {
    ReadAction.run(() -> {
      for (VirtualFile file : files) {
        PsiFile psiFile = getPsiInProject(file);

        // We don't care about PSI validation for non-Kotlin files. The errors displayed for editing
        // non-Kotlin files during a Live Edit session are thrown later in the pipeline.
        if (psiFile == null || psiFile.getFileType() != KotlinFileType.INSTANCE || psiSnapshots.containsKey(psiFile)) {
          continue;
        }

        psiSnapshots.put(psiFile, getPsiValidationState(psiFile));
      }
    });
  }

  // Called when a file is modified. Only called on the class-differ code path.
  public void fileChanged(VirtualFile file) {
    if (liveEditDevices.hasUnsupportedApi()) {
      liveEditDevices.update(LiveEditStatus.UnsupportedVersionOtherDevice.INSTANCE);
      return;
    }
    
    if (!shouldLiveEdit()) {
      return;
    }

    mainThreadExecutor.submit(() -> {
      PsiFile psiFile = ReadAction.compute(() -> getPsiInProject(file));
      if (psiFile == null) {
        return;
      }

      changedFileQueue.add(psiFile);

      if (ProjectSystemUtil.getProjectSystem(project).getSyncManager().isSyncNeeded() || intermediateSyncs.get()) {
        updateEditStatus(LiveEditStatus.SyncNeeded.INSTANCE);
        return;
      }
      processQueuedChanges();
    });
  }

  @RequiresReadLock
  private @Nullable PsiFile getPsiInProject(VirtualFile file) {
    // Ignore files in closed projects, deleted files, or read-only files.
    if (project.isDisposed() || !file.isValid() || !file.isWritable()) {
      return null;
    }

    if (!ProjectFileIndex.getInstance(project).isInProject(file)) {
      return null;
    }

    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile != null) {
      // Ensure that we have the original, VirtualFile-backed version of the file, since sometimes
      // an event is generated with a non-physical version of a given file, which will cause some
      // Live Edit checks that assume a non-null VirtualFile to fail.
      return psiFile.getOriginalFile();
    }
    return null;
  }

  private boolean shouldLiveEdit() {
    return LiveEditApplicationConfiguration.getInstance().isLiveEdit() &&
           StringUtil.isNotEmpty(applicationId) &&
           !liveEditDevices.isUnrecoverable() &&
           !liveEditDevices.isDisabled();
  }

  @VisibleForTesting
  @NotNull
  public LiveEditDevices getLiveEditDevices() {
    return liveEditDevices;
  }

  // Triggered from LiveEdit manual mode. Use buffered changes.
  @Trace
  public void onManualLETrigger() {
    mainThreadExecutor.schedule(this::doOnManualLETrigger, 0, TimeUnit.MILLISECONDS);
  }

  @VisibleForTesting
  void doOnManualLETrigger() {

    // If user to trigger a LE push twice in a row with compilation errors, the second trigger would set the state to "synced" even
    // though the compilation error prevented a push on the first trigger
    if (bufferedFiles.isEmpty()) {
      return;
    }

    updateEditableStatus(LiveEditStatus.InProgress.INSTANCE);

    while(!processChanges(project, bufferedFiles,
                          LiveEditService.isLeTriggerOnSave() ? LiveEditEvent.Mode.ON_SAVE : LiveEditEvent.Mode.MANUAL)) {
        LOGGER.info("ProcessChanges was interrupted");
    }
    bufferedFiles.clear();
  }

  @Trace
  boolean handleChangedMethods(Project project, List<PsiFile> changedFiles) {
    LOGGER.info("Change detected for project %s targeting app %s", project.getName(), applicationId);

    // In manual mode, we store changes and update status but defer processing.
    if (LiveEditService.Companion.isLeTriggerManual()) {
      if (bufferedFiles.size() < 2000) {
        bufferedFiles.addAll(changedFiles);
        updateEditableStatus(LiveEditStatus.OutOfDate.INSTANCE);
      } else {
        // Something is wrong. Discard event otherwise we will run Out Of Memory
        updateEditableStatus(LiveEditStatus.createErrorStatus("Too many buffered LE keystrokes. Redeploy app."));
      }

      return true;
    }

    return processChanges(project, changedFiles, LiveEditEvent.Mode.AUTO);
  }

  // Allows calling processChanges correctly on the main thread executor from a test context, to prevent hacks/concurrency bugs
  // that only appear in tests due to incorrectly calling processChanges on a thread other than the executor.
  @VisibleForTesting
  boolean processChangesForTest(Project project, List<PsiFile> changedFiles, LiveEditEvent.Mode mode) throws Exception {
    return mainThreadExecutor.submit(() -> processChanges(project, changedFiles, mode)).get();
  }

  // Waits for the LE main thread to complete all previously scheduled work. Not perfectly reliable due to retry logic, and the
  // existence of both this and processChangesForTest strongly imply a need to refactor our threading to make it testable, but that's not
  // a high priority right now.
  @VisibleForTesting
  void waitForThreadInTest(long timeoutMillis) throws Exception {
    mainThreadExecutor.submit(() -> {}).get(timeoutMillis, TimeUnit.MILLISECONDS);
  }

  @Trace
  /**
   * @return true is the changes were successfully processed (without being interrupted). Otherwise, false.
   */
  private boolean processChanges(Project project, List<PsiFile> changedFiles, LiveEditEvent.Mode mode) {
    LiveEditEvent.Builder event = LiveEditEvent.newBuilder().setMode(mode);

    long start = System.nanoTime();
    long compileFinish, pushFinish;

    Optional<LiveEditDesugarResponse> compiled;

    PsiDocumentManager psiManager = PsiDocumentManager.getInstance(project);
    try {
      prebuildChecks(project, changedFiles);

      List<LiveEditCompilerInput> inputs = new ArrayList<>();
      for (PsiFile file : changedFiles) {
        // The PSI might not update immediately after a file is edited. Interrupt until all changes are committed to the PSI.
        Document doc = psiManager.getDocument(file);
        if (doc != null && psiManager.isUncommited(doc)) {
          return false;
        }

        PsiState state = psiSnapshots.get(file);
        inputs.add(new LiveEditCompilerInput(file, state));
      }

      compiled = compiler.compile(inputs, !LiveEditService.isLeTriggerManual(), getDevicesApiLevels());
      if (compiled.isEmpty()) {
        return false;
      }

      // Remove files successfully compiled from the error set.
      for (PsiFile file : changedFiles) {
        filesWithCompilationErrors.remove(file.getName());
      }
    } catch (LiveEditUpdateException e) {
      boolean recoverable = e.getError().getRecoverable();

      // The FIRST thing we should do is update the status and do the bookkeeping task after.
      // Otherwise, if any of bookkeeping step causes a crash, status is not updated and we get an infinite spinner.
      updateEditableStatus(recoverable ?
                           LiveEditStatus.createPausedStatus(errorMessage(e)) :
                           LiveEditStatus.createRerunnableErrorStatus(errorMessage(e)));

      if (recoverable) {
        for (PsiFile file : changedFiles) {
          filesWithCompilationErrors.add(file.getName());
        }
      }

      // We log all unrecoverable events, ignoring easily recoverable syntax / type errors that happens way too common during editing.
      // Both inlining restriction should are also logged despite being recoverable as well.
      if (e.getError() == LiveEditUpdateException.Error.UNABLE_TO_INLINE ||
          e.getError() == LiveEditUpdateException.Error.NON_PRIVATE_INLINE_FUNCTION ||
          !recoverable) {
        event.setStatus(e.getError().getMetric());
        logLiveEditEvent(event);
      }

      return true;
    }

    if (mode == LiveEditEvent.Mode.AUTO && !filesWithCompilationErrors.isEmpty()) {

      // When we are only confined to the current file, we are not going to check of there
      // are errors in other files.
      if (!StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_CONFINED_ANALYSIS.get()) {
        Optional<String> errorFilename = filesWithCompilationErrors.stream().findFirst();
        String errorMsg = ErrorReporterKt.leErrorMessage(LiveEditUpdateException.Error.COMPILATION_ERROR, errorFilename.get());
        updateEditStatus(LiveEditStatus.createPausedStatus(errorMsg));
        return true;
      }
    }

    final LiveEditDesugarResponse desugaredResponse = compiled.get();
    event.setHasNonCompose(desugaredResponse.getHasNonComposeChanges());

    compileFinish = System.nanoTime();
    event.setCompileDurationMs(TimeUnit.NANOSECONDS.toMillis(compileFinish - start));
    LOGGER.info("LiveEdit compile completed in %dms", event.getCompileDurationMs());

    List<LiveUpdateDeployer.UpdateLiveEditError> errors = editableDeviceIterator()
      .map(device -> pushUpdatesToDevice(applicationId, device, desugaredResponse).errors)
      .flatMap(List::stream)
      .toList();

    LiveEditEvent.Device type = switch (devices().size()) {
      case 0 -> LiveEditEvent.Device.NONE;
      case 1 -> devices().iterator().next().isEmulator() ? LiveEditEvent.Device.EMULATOR : LiveEditEvent.Device.PHYSICAL;
      default -> LiveEditEvent.Device.MULTI;
    };
    event.setTargetDevice(type);

    if (!errors.isEmpty()) {
      event.setStatus(errorToStatus(errors.get(0)));
    } else {
      event.setStatus(LiveEditEvent.Status.SUCCESS);
      for (IrClass irClass : desugaredResponse.getCompilerOutput().getIrClasses()) {
        irClassCache.update(irClass);
      }
    }

    pushFinish = System.nanoTime();
    event.setPushDurationMs(TimeUnit.NANOSECONDS.toMillis(pushFinish - compileFinish));
    LOGGER.info("LiveEdit push completed in %dms", event.getPushDurationMs());

    logLiveEditEvent(event);
    return true;
  }

  @NotNull
  private Set<Integer> getDevicesApiLevels() {
    return editableDeviceIterator()
      .map(device -> liveEditDevices.getInfo(device).getApp().getMinAPI())
      .collect(Collectors.toSet());
  }

  public void requestRerun() {
    // This is triggered when Live Edit is just toggled on. Since the last deployment didn't start the Live Edit service,
    // we will fetch all the running devices and change every one of them to be outdated.
    for (IDevice device : AndroidDebugBridge.getBridge().getDevices()) {
      liveEditDevices.addDevice(device, LiveEditStatus.createRerunnableErrorStatus("Re-run application to start Live Edit updates."));
    }
  }

  @VisibleForTesting
  void scheduleErrorPolling(LiveUpdateDeployer deployer, Installer installer, AdbClient adb, String packageName) {
    if (pendingRecompositionStatusPolls.getAndSet(NUM_RECOMPOSITION_STATUS_POLLS_PER_EDIT) < 1) {
      scheduleNextErrorPolling(deployer, installer, adb, packageName);
    }
  }

  private void scheduleNextErrorPolling(LiveUpdateDeployer deployer, Installer installer, AdbClient adb, String packageName) {
    ScheduledExecutorService scheduler = JobScheduler.getScheduler();

    scheduler.schedule(() -> {
      int pollsLeft = pendingRecompositionStatusPolls.decrementAndGet();
      try {
        List<Deploy.ComposeException> errors = deployer.retrieveComposeStatus(installer, adb, packageName);
        if (!errors.isEmpty()) {
          Deploy.ComposeException error = errors.get(0);
          updateEditableStatus(createRecomposeErrorStatus(error.getExceptionClassName(), error.getMessage(), error.getRecoverable()));
        }
        if (pollsLeft > 0) {
          scheduleNextErrorPolling(deployer, installer, adb, packageName);
        }
      } catch (IOException e) {
        updateEditableStatus(createRecomposeRetrievalErrorStatus(e));
        LOGGER.warning(e.toString());
      }
    }, 2, TimeUnit.SECONDS);
  }

  public void clearDevices() {
    liveEditDevices.clear();
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
        return LiveEditEvent.Status.UNSUPPORTED_COMPOSE_RUNTIME_VERSION;
      default:
        return LiveEditEvent.Status.UNKNOWN_LIVE_UPDATE_DEPLOYER_ERROR;
    }
  }

  private void logLiveEditEvent(LiveEditEvent.Builder event) {
    if (!hasLoggedSinceReset || randomForLogging.nextDouble() < LE_LOG_FRACTION) {
      UsageTracker.log(
        UsageTrackerUtils.withProjectId(AndroidStudioEvent.newBuilder()
                                          .setCategory(AndroidStudioEvent.EventCategory.DEPLOYMENT)
                                          .setKind(AndroidStudioEvent.EventKind.LIVE_EDIT_EVENT)
                                          .setLiveEditEvent(event),
                                        project));
      hasLoggedSinceReset = true;
    }
  }

  private void updateEditStatus(@NotNull IDevice device, @NotNull LiveEditStatus status) {
    liveEditDevices.update(device, status);
  }

  private void updateEditStatus(@NotNull LiveEditStatus status) {
    liveEditDevices.update(status);
  }

  @VisibleForTesting
  void updateEditableStatus(@NotNull LiveEditStatus newStatus) {
    liveEditDevices.update((device, prevStatus) -> (
      prevStatus.unrecoverable() ||
      prevStatus == LiveEditStatus.Disabled.INSTANCE ||
      prevStatus == LiveEditStatus.NoMultiDeploy.INSTANCE) ? prevStatus : newStatus);
  }

  private void handleDeviceStatusChange(Map<IDevice, LiveEditStatus> map) {
    // Force the UI to redraw with the new status. See com.intellij.openapi.actionSystem.AnAction#update().
    ActivityTracker.getInstance().inc();
  }

  private Stream<IDevice> editableDeviceIterator() {
    return liveEditDevices.devices().stream().filter(IDevice::isOnline).filter(
      device ->
        liveEditDevices.getInfo(device).getStatus() != LiveEditStatus.Disabled.INSTANCE &&
        liveEditDevices.getInfo(device).getStatus() != LiveEditStatus.NoMultiDeploy.INSTANCE);
  }

  @VisibleForTesting
  static Installer newInstaller(IDevice device) {
    MetricsRecorder metrics = new MetricsRecorder();
    AdbClient adb = new AdbClient(device, LOGGER);
    return new AdbInstaller(getLocalInstaller(), adb, metrics.getDeployMetrics(), LOGGER, AdbInstaller.Mode.DAEMON);
  }

  private LiveUpdateDeployer.UpdateLiveEditResult pushUpdatesToDevice(
      String applicationId, IDevice device, LiveEditDesugarResponse update) {
    LiveUpdateDeployer deployer = new LiveUpdateDeployer(LOGGER);
    Installer installer = newInstaller(device);
    AdbClient adb = new AdbClient(device, LOGGER);

    boolean useDebugMode = LiveEditAdvancedConfiguration.getInstance().getUseDebugMode();

    int apiLevel = liveEditDevices.getInfo(device).getApp().getMinAPI();
    LiveUpdateDeployer.UpdateLiveEditsParam param =
      new LiveUpdateDeployer.UpdateLiveEditsParam(
        update.classes(apiLevel),
        update.supportClasses(apiLevel),
        update.getGroupIds(),
        update.getInvalidateMode(),
        useDebugMode);

    LiveUpdateDeployer.UpdateLiveEditResult result = null;

    // Sometimes we get a PSI event for a top-level file when no top-level class exists. In this
    // case, just treat it as a no-op success. This isn't an issue with the class differ
    // as we would no longer get spurious PSI update events anymore.
    if (!StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_CLASS_DIFFER.get() && param.classes.isEmpty()) {
      result = new LiveUpdateDeployer.UpdateLiveEditResult();
    } else {
      result = deployer.updateLiveEdit(installer, adb, applicationId, param);
    }

    if (filesWithCompilationErrors.isEmpty() || StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_CONFINED_ANALYSIS.get()) {
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
        updateEditStatus(device, LiveEditStatus.createRerunnableErrorStatus(firstProblem.getMessage()));
      }
    }
    return result;
  }

  @VisibleForTesting
  boolean isGradleSyncNeeded(){
    return ProjectSystemUtil.getProjectSystem(project).getSyncManager().isSyncNeeded() || intermediateSyncs.get();
  }



  public static boolean supportLiveEdits(IDevice device) {
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
