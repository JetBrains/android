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

import static com.android.tools.idea.editors.literals.LiveEditService.DISABLED_STATUS;
import static com.android.tools.idea.run.deployment.liveedit.ErrorReporterKt.errorMessage;
import static com.android.tools.idea.run.deployment.liveedit.PrebuildChecksKt.PrebuildChecks;
import static com.android.tools.idea.run.deployment.liveedit.PrebuildChecksKt.checkIwiAvailable;
import static com.android.tools.idea.run.deployment.liveedit.PrebuildChecksKt.checkJetpackCompose;
import static com.android.tools.idea.run.deployment.liveedit.PrebuildChecksKt.checkSupportedFiles;

import com.android.annotations.Nullable;
import com.android.annotations.Trace;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.AdbClient;
import com.android.tools.deployer.AdbInstaller;
import com.android.tools.deployer.Installer;
import com.android.tools.deployer.MetricsRecorder;
import com.android.tools.deployer.tasks.LiveUpdateDeployer;
import com.android.tools.idea.editors.literals.EditState;
import com.android.tools.idea.editors.literals.EditStatus;
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration;
import com.android.tools.idea.editors.literals.LiveEditService;
import com.android.tools.idea.editors.literals.LiveLiteralsMonitorHandler;
import com.android.tools.idea.editors.literals.LiveLiteralsService;
import com.android.tools.idea.editors.literals.EditEvent;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.run.AndroidRemoteDebugProcessHandler;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.run.deployment.AndroidExecutionTarget;
import com.android.tools.idea.util.StudioPathManager;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.LiveEditEvent;
import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension;

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
public class AndroidLiveEditDeployMonitor {
  // TODO: The logging is overly excessive for now given we have no UI to provide feedback to the user
  // when things go wrong. This will be changed in the final product.
  private static final LogWrapper LOGGER = new LogWrapper(Logger.getInstance(AndroidLiveEditDeployMonitor.class));

  private static final EditStatus LOADING = new EditStatus(EditState.LOADING, "Application being deployed.", null);

  private static final EditStatus UPDATE_IN_PROGRESS = new EditStatus(EditState.IN_PROGRESS, "Live edit update in progress.", null);

  private static final EditStatus DISCONNECTED = new EditStatus(EditState.PAUSED, "No apps are ready to receive live edits.", null);

  private static final EditStatus UP_TO_DATE = new EditStatus(EditState.UP_TO_DATE, "Up to date.", null);

  private static final EditStatus OUT_OF_DATE = new EditStatus(EditState.OUT_OF_DATE, "Refresh to view the latest Live Edit Changes. App state may be reset.", LiveEditService.getPIGGYBACK_ACTION_ID());

  private static final EditStatus RECOMPOSE_NEEDED = new EditStatus(EditState.RECOMPOSE_NEEDED, "Hard refresh must occur for all changes to be applied. App state will be reset.", "android.deploy.livedit.recompose");

  private static final EditStatus RECOMPOSE_ERROR = new EditStatus(EditState.RECOMPOSE_ERROR, "Error during recomposition.", null);

  private static final EditStatus DEBUGGER_ATTACHED = new EditStatus(EditState.RECOMPOSE_ERROR, "The app is currently running in debugging or profiling mode. These modes are not compatible with Live Edit.", ToolWindowId.RUN);

  private final @NotNull Project project;

  private final @NotNull LinkedHashMap<String, SourceInlineCandidate> sourceInlineCandidateCache;

  private @Nullable String applicationId;

  private final ScheduledExecutorService methodChangesExecutor = Executors.newSingleThreadScheduledExecutor();

  private final @NotNull Map<IDevice, EditStatus> editStatus = new ConcurrentHashMap<>();

  // In manual mode, we buffer events until user triggers a LE push.
  private final ArrayList<EditEvent> bufferedEvents = new ArrayList<>();

  public void clearBufferedEvents() {
    bufferedEvents.clear();
  }

  private class EditStatusGetter implements LiveEditService.EditStatusProvider {
    @NotNull
    @Override
    public EditStatus status(@NotNull IDevice device) {
      if (StringUtil.isEmpty(applicationId)) {
        return LiveEditService.DISABLED_STATUS;
      }

      return editStatus.compute(device, (d, s) -> {
        EditStatus result;
        if (!device.isOnline()) {
          result = DISCONNECTED;
        }
        else {
          List<AndroidSessionInfo> info = AndroidSessionInfo.findActiveSession(project);
          if (info != null &&
              info.stream()
                .filter(i -> DefaultDebugExecutor.getDebugExecutorInstance().getId().equals(i.getExecutorId()))
                .map(AndroidSessionInfo::getProcessHandler)
                .filter(p -> p instanceof AndroidRemoteDebugProcessHandler)
                .map(p -> (AndroidRemoteDebugProcessHandler)p)
                .anyMatch(p -> !(p.isProcessTerminating() || p.isProcessTerminated()) && p.isPackageRunning(d, applicationId))) {
            result = DEBUGGER_ATTACHED;
          }
          else {
            boolean appAlive = Arrays.stream(device.getClients()).anyMatch(c -> applicationId.equals(c.getClientData().getPackageName()));
            if (s == null) {
              // Monitor for this device not initialized yet.
              result = DISABLED_STATUS;
            }
            else if (s == LOADING && appAlive) {
              // App has came online, so flip state to UP_TO_DATE.
              result = UP_TO_DATE;
            }
            else if (s != DISCONNECTED && s != LOADING && !appAlive) {
              // App was running and has been terminated (or this was in disabled state already - this saves extra check), hide the indicator.
              result = DISABLED_STATUS;
            }
            else {
              result = s;
            }
          }
        }
        return result;
      });
    }

    @NotNull
    @Override
    public Map<IDevice, EditStatus> status() {
      if (StringUtil.isEmpty(applicationId)) {
        return Collections.emptyMap();
      }
      // Get all devices that are running our app.
      Set<IDevice> devices = deviceIterator(project)
        .filter(
          d -> Arrays.stream(d.getClients()).anyMatch(c -> applicationId.equals(c.getClientData().getPackageName())))
        .collect(Collectors.toSet());

      // Find all devices that were deployed by us (and not user-started).
      return editStatus.keySet().stream().filter(devices::contains).collect(Collectors.toMap(d -> d, this::status));
    }

    @NotNull
    @Override
    public Set<IDevice> devices() {
      if (StringUtil.isEmpty(applicationId)) {
        return Collections.emptySet();
      }

      return deviceIterator(project)
        .filter(
          d -> Arrays.stream(d.getClients()).anyMatch(c -> applicationId.equals(c.getClientData().getPackageName())))
        .collect(Collectors.toSet());
    }
  }

  private class EditsListener implements Disposable {
    // Care should be given when modifying this field to preserve atomicity.
    private final ConcurrentLinkedQueue<EditEvent> changedMethodQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void dispose() {
      editStatus.clear();
      methodChangesExecutor.shutdownNow();
    }

    // This method is invoked on the listener executor thread in LiveEditService and does not block the UI thread.
    public void onLiteralsChanged(EditEvent event) {
      if (!LiveEditApplicationConfiguration.getInstance().isLiveEdit()) {
        return;
      }

      if (StringUtil.isEmpty(applicationId)) {
        return;
      }

      if (mergeStatuses(editStatus).getEditState() == EditState.ERROR) {
        return;
      }

      changedMethodQueue.add(event);
      methodChangesExecutor.schedule(this::processChanges, LiveEditAdvancedConfiguration.getInstance().getRefreshRateMs(), TimeUnit.MILLISECONDS);
    }

    private void processChanges() {
      if (changedMethodQueue.isEmpty()) {
        return;
      }

      List<EditEvent> copy = new ArrayList<>();
      changedMethodQueue.removeIf(e -> {
        copy.add(e);
        return true;
      });

      editStatus.replaceAll((key, status) -> {
        switch (status.getEditState()) {
          case PAUSED:
          case UP_TO_DATE:
          case LOADING:
          case IN_PROGRESS:
          case RECOMPOSE_ERROR:
            return UPDATE_IN_PROGRESS;
          default:
            return status;
        }
      });

      if (!handleChangedMethods(project, copy)) {
        changedMethodQueue.addAll(copy);
        methodChangesExecutor.schedule(this::processChanges, LiveEditAdvancedConfiguration.getInstance().getRefreshRateMs(), TimeUnit.MILLISECONDS);
      }
    }
  }

  public AndroidLiveEditDeployMonitor(@NotNull LiveEditService liveEditService, @NotNull Project project) {
    this.project = project;
    this.sourceInlineCandidateCache = liveEditService.getInlineCandidateCache();
    EditsListener editsListener = new EditsListener();
    liveEditService.addOnEditListener(editsListener::onLiteralsChanged);
    liveEditService.addEditStatusProvider(new EditStatusGetter());
    Disposer.register(liveEditService, editsListener);
  }

  public void notifyDebug(String applicationId, IDevice device) {
    updateEditStatus(device, DEBUGGER_ATTACHED);
  }

  public Callable<?> getCallback(String applicationId, IDevice device) {
    String deviceId = device.getSerialNumber();

    // TODO: Don't use Live Literal's reporting
    LiveLiteralsService.getInstance(project).liveLiteralsMonitorStopped(deviceId + "#" + applicationId);

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
    updateEditStatus(device, LOADING);

    return () -> methodChangesExecutor
      .schedule(
        () -> {
          this.applicationId = applicationId;
          LiveEditService.getInstance(project).resetState();

          LiveLiteralsMonitorHandler.DeviceType deviceType;
          if (device.isEmulator()) {
            deviceType = LiveLiteralsMonitorHandler.DeviceType.EMULATOR;
          }
          else {
            deviceType = LiveLiteralsMonitorHandler.DeviceType.PHYSICAL;
          }

          LiveLiteralsService.getInstance(project).liveLiteralsMonitorStarted(deviceId + "#" + applicationId, deviceType);
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

    updateEditStatus(UPDATE_IN_PROGRESS);

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
      updateEditStatus(OUT_OF_DATE);

      if (bufferedEvents.size() < 2000) {
        bufferedEvents.addAll(changes);
      } else {
        // Something is wrong. Discard event otherwise we will run Out Of Memory
        updateEditStatus(new EditStatus(EditState.ERROR, "Too many buffered LE keystrokes. Redeploy app.", null));
      }

      return true;
    }

    return processChanges(project, changes, LiveEditEvent.Mode.AUTO);
  }

  @Trace
  private boolean processChanges(Project project, List<EditEvent> changes, LiveEditEvent.Mode mode) {
    LiveEditEvent.Builder event = LiveEditEvent.newBuilder().setMode(mode);

    long start = System.nanoTime();
    long compileFinish, pushFinish;

    ArrayList<AndroidLiveEditCodeGenerator.CodeGeneratorOutput> compiled = new ArrayList<>();

    try {
      PrebuildChecks(project, changes);
      List<AndroidLiveEditCodeGenerator.CodeGeneratorInput> inputs = changes.stream().map(
        change ->
          new AndroidLiveEditCodeGenerator.CodeGeneratorInput(change.getFile(), change.getOrigin(), change.getParentGroup()))
        .collect(Collectors.toList());
      if (!new AndroidLiveEditCodeGenerator(project, sourceInlineCandidateCache).compile(inputs, compiled, !LiveEditService.isLeTriggerManual())) {
        return false;
      }
    } catch (LiveEditUpdateException e) {
      updateEditStatus(new EditStatus(
        e.getError().getRecoverable() ? EditState.PAUSED : EditState.ERROR,
        errorMessage(e), null));
      return true;
    }

    // Ignore FunctionType.NONE, since those are changes to non-function elements. Counting any change to a non-function as a non-compose
    // change might make the data useless, as a lot of "noisy" class-level/file-level PSI events are generated along with function edits.
    event.setHasNonCompose(compiled.stream().anyMatch(c -> c.getFunctionType() == AndroidLiveEditCodeGenerator.FunctionType.KOTLIN));

    compileFinish = System.nanoTime();
    event.setCompileDurationMs(TimeUnit.NANOSECONDS.toMillis(compileFinish - start));
    LOGGER.info("LiveEdit compile completed in %dms", event.getCompileDurationMs());

    Optional<LiveUpdateDeployer.UpdateLiveEditError> error = deviceIterator(project)
      .map(device -> pushUpdatesToDevice(applicationId, device, compiled))
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
      boolean hasError = !deployer.retrieveComposeStatus(installer, adb, packageName);
      if (hasError) {
        updateEditStatus(RECOMPOSE_ERROR);
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

  private void updateEditStatus(@NotNull IDevice device, @NotNull EditStatus status) {
    editStatus.put(device, status);
  }

  private void updateEditStatus(@NotNull EditStatus status) {
    editStatus.replaceAll((device, oldStatus) -> status);
  }

  @NotNull
  public EditStatus mergeStatuses(@NotNull Map<IDevice, EditStatus> editStatus) {
    if (StringUtil.isEmpty(applicationId)) {
      return DISABLED_STATUS;
    }

    Set<IDevice> devices = deviceIterator(project)
      .filter(
        d -> Arrays.stream(d.getClients()).anyMatch(c -> applicationId.equals(c.getClientData().getPackageName())))
      .collect(Collectors.toSet());
    Set<IDevice> keys = new HashSet<>(editStatus.keySet());
    keys.retainAll(devices);
    List<EditStatus> statuses = editStatus
      .entrySet()
      .stream()
      .filter(e -> keys.contains(e.getKey())).map(Map.Entry::getValue)
      .collect(Collectors.toList());

    if (statuses.isEmpty()) {
      return DISCONNECTED;
    }
    EditStatus mergedStatus = DISABLED_STATUS;
    for (EditStatus status : statuses) {
      if (status.getEditState().ordinal() < mergedStatus.getEditState().ordinal()) {
        mergedStatus = status;
      }
    }
    return mergedStatus;
  }

  private static Stream<IDevice> deviceIterator(Project project) {
    List<AndroidSessionInfo> sessions = AndroidSessionInfo.findActiveSession(project);
    if (sessions == null) {
      LOGGER.info("No running session found for %s", project.getName());
      return Stream.empty();
    }

    return sessions
      .stream()
      .map(AndroidSessionInfo::getExecutionTarget)
      .filter(t -> t instanceof AndroidExecutionTarget)
      .flatMap(t -> ((AndroidExecutionTarget)t).getRunningDevices().stream())
      .filter(AndroidLiveEditDeployMonitor::supportLiveEdits);
  }

  private static Installer newInstaller(IDevice device) {
    MetricsRecorder metrics = new MetricsRecorder();
    AdbClient adb = new AdbClient(device, LOGGER);
    return  new AdbInstaller(getLocalInstaller(), adb, metrics.getDeployMetrics(), LOGGER, AdbInstaller.Mode.DAEMON);
  }

  public void sendRecomposeRequest() {
    updateEditStatus(UPDATE_IN_PROGRESS);
    methodChangesExecutor.schedule(this::doSendRecomposeRequest, 0 , TimeUnit.MILLISECONDS);
  }

  private void doSendRecomposeRequest() {
    try {
      deviceIterator(project).forEach(device -> sendRecomposeRequests(device));
    } finally {
      updateEditStatus(UP_TO_DATE);
    }
  }

  private void sendRecomposeRequests(IDevice device) {
    LiveUpdateDeployer deployer = new LiveUpdateDeployer(LOGGER);
    Installer installer = newInstaller(device);
    AdbClient adb = new AdbClient(device, LOGGER);
    deployer.recompose(installer, adb, applicationId);
    scheduleErrorPolling(deployer, installer, adb, applicationId);
  }

  private List<LiveUpdateDeployer.UpdateLiveEditError> pushUpdatesToDevice(
      String applicationId, IDevice device, List<AndroidLiveEditCodeGenerator.CodeGeneratorOutput> updates) {
    LiveUpdateDeployer deployer = new LiveUpdateDeployer(LOGGER);
    Installer installer = newInstaller(device);
    AdbClient adb = new AdbClient(device, LOGGER);

    // TODO: Batch multiple updates in one LiveEdit operation; listening to all PSI events means multiple class events can be
    //  generated from a single keystroke, leading to multiple LEs and multiple recomposes.
    List<LiveUpdateDeployer.UpdateLiveEditError> results = new ArrayList<>();
    boolean recomposeNeeded = false;
    for (AndroidLiveEditCodeGenerator.CodeGeneratorOutput update : updates) {
      boolean useDebugMode = LiveEditAdvancedConfiguration.getInstance().getUseDebugMode();
      boolean usePartialRecompose = LiveEditAdvancedConfiguration.getInstance().getUsePartialRecompose() &&
                                    (update.getFunctionType() == AndroidLiveEditCodeGenerator.FunctionType.COMPOSABLE ||
                                     update.getHasGroupId());

      // In manual mode we don't recompose automatically if priming happened.
      // Last minute change, we don't want user to have to perform "hard-refresh" is a class was primed.
      // TODO: Delete if it turns our we don't need Hard-refresh trigger.
      //boolean recomposeAfterPriming = !LiveEditService.Companion.isLeTriggerManual();
      boolean recomposeAfterPriming = true;

      LiveUpdateDeployer.UpdateLiveEditsParam param =
        new LiveUpdateDeployer.UpdateLiveEditsParam(
          update.getClassName(), update.getMethodName(), update.getMethodDesc(),
          usePartialRecompose,
          update.getGroupId(),
          update.getClassData(),
          update.getSupportClasses(), useDebugMode,
          recomposeAfterPriming);


      if (useDebugMode) {
        writeDebugToTmp(update.getClassName().replaceAll("/", ".") + ".class", update.getClassData());
        for (String supportClassName : update.getSupportClasses().keySet()) {
          byte[] bytecode = update.getSupportClasses().get(supportClassName);
          writeDebugToTmp(supportClassName.replaceAll("/", ".") + ".class", bytecode);
        }
      }

      LiveUpdateDeployer.UpdateLiveEditResult result = deployer.updateLiveEdit(installer, adb, applicationId, param);
      if (LiveEditService.Companion.isLeTriggerManual()) {
        // In manual mode, we need to let the user know that recompose was not called if classes were Primed.
        if (result.recomposeType == Deploy.AgentLiveEditResponse.RecomposeType.RESET_SKIPPED) {
          recomposeNeeded = true;
        }
      }
      results.addAll(result.errors);
    }

    if (recomposeNeeded) {
      updateEditStatus(device, RECOMPOSE_NEEDED);
    } else {
      updateEditStatus(device, UP_TO_DATE);
      scheduleErrorPolling(deployer, installer, adb, applicationId);
    }

    if (!results.isEmpty()) {
      updateEditStatus(device, new EditStatus(EditState.ERROR, results.get(0).getMessage(), null));
    }
    return results;
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
