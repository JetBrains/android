/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.diagnostics;

import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.HistogramUtil;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.diagnostics.crash.ExceptionDataCollection;
import com.android.tools.idea.diagnostics.crash.ExceptionRateLimiter;
import com.android.tools.idea.diagnostics.crash.UploadFields;
import com.android.tools.idea.diagnostics.report.MemoryReportReason;
import com.android.tools.idea.diagnostics.report.UnanalyzedHeapReport;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.io.Files;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind;
import com.google.wireless.android.sdk.stats.GcPauseInfo;
import com.google.wireless.android.sdk.stats.HeapReportEvent;
import com.google.wireless.android.sdk.stats.StudioPerformanceStats;
import com.google.wireless.android.sdk.stats.UIActionStats;
import com.google.wireless.android.sdk.stats.UIActionStats.InvocationKind;
import com.intellij.diagnostic.LogMessage;
import com.intellij.diagnostic.MessagePool;
import com.intellij.diagnostic.VMOptions;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.CopyAction;
import com.intellij.ide.actions.CutAction;
import com.intellij.ide.actions.DeleteAction;
import com.intellij.ide.actions.NextOccurenceAction;
import com.intellij.ide.actions.PasteAction;
import com.intellij.ide.actions.PreviousOccurenceAction;
import com.intellij.ide.actions.SaveAllAction;
import com.intellij.ide.actions.UndoRedoAction;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.BrowseNotificationAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.impl.NotificationFullContent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.HdrHistogram.SingleWriterRecorder;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

/**
 * Extension to System Health Monitor that includes Android Studio-specific code.
 */
public final class AndroidStudioSystemHealthMonitor {
  private static final Logger LOG = Logger.getInstance(AndroidStudioSystemHealthMonitor.class);

  public static final String STUDIO_RUN_UNDER_INTEGRATION_TEST_KEY = "studio.run.under.integration.test";

  // The group should be registered by SystemHealthMonitor
  private final NotificationGroup myGroup = NotificationGroup.findRegisteredGroup("System Health");

  /**
   * Count of action events fired. This is used as a proxy for user initiated activity in the IDE.
   */
  public static final AtomicLong ourStudioActionCount = new AtomicLong(0);
  private static final String STUDIO_ACTIVITY_COUNT = "studio.activity.count";

  /**
   * Count of non fatal exceptions in the IDE.
   */
  private static final AtomicLong ourStudioExceptionCount = new AtomicLong(0);
  private static final AtomicLong ourInitialPersistedExceptionCount = new AtomicLong(0);
  private static final AtomicLong ourBundledPluginsExceptionCount = new AtomicLong(0);
  private static final AtomicLong ourNonBundledPluginsExceptionCount = new AtomicLong(0);

  private static final Object EXCEPTION_COUNT_LOCK = new Object();
  @NonNls private static final String STUDIO_EXCEPTION_COUNT_FILE = "studio.exc";
  @NonNls private static final String BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE = "studio.exb";
  @NonNls private static final String NON_BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE = "studio.exp";

  private static final int MAX_PERFORMANCE_REPORTS_COUNT =
    Integer.getInteger("studio.diagnostic.performanceThreadDump.maxReports", 0);
  private static final int MAX_HISTOGRAM_REPORTS_COUNT =
    Integer.getInteger("studio.diagnostic.histogram.maxReports", 10);
  private static final int MAX_FREEZE_REPORTS_COUNT =
    Integer.getInteger("studio.diagnostic.freeze.maxReports",
                       ApplicationManager.getApplication().isEAP() ? 20 : 1);
  private static final int MAX_JFR_REPORTS_COUNT =
    Integer.getInteger("studio.diagnostic.jfr.maxReports",
                       ApplicationManager.getApplication().isEAP() ? 20 : 1);

  private static final ConcurrentMap<GcPauseInfo.GcType, SingleWriterRecorder> myGcPauseInfo = new ConcurrentHashMap<>();
  /**
   * Maximum GC pause duration to record. Longer pause durations are truncated to keep the size of the histogram bounded.
   */
  private static final long MAX_GC_PAUSE_TIME_MS = 30 * 60 * 1000;

  private static final long TOO_MANY_EXCEPTIONS_THRESHOLD = 10000;

  private final StudioReportDatabase myReportsDatabase;
  public static final HProfDatabase ourHProfDatabase = new HProfDatabase(Paths.get(PathManager.getTempPath()));

  private static final Object ACTION_INVOCATIONS_LOCK = new Object();
  private static final Lock REPORT_EXCEPTIONS_LOCK = new ReentrantLock();
  // Updates to ourActionInvocations need to be done synchronized on ACTION_INVOCATIONS_LOCK to avoid updates during usage reporting.
  private static Map<String, Multiset<InvocationKind>> ourActionInvocations = new HashMap<>();

  private final PropertiesComponent myProperties;
  private boolean myTooManyExceptionsPromptShown = false;
  private static long ourCurrentSessionStudioExceptionCount = 0;

  // If GC cleared more than MEMORY_FREED_THRESHOLD_FOR_HEAP_REPORT of unreachable/weak/soft references, then ignore the notification.
  public static final long MEMORY_FREED_THRESHOLD_FOR_HEAP_REPORT = 100_000_000;

  // If there is more free memory than FREE_MEMORY_THRESHOLD_FOR_HEAP_REPORT, ignore the notification.
  public static final long FREE_MEMORY_THRESHOLD_FOR_HEAP_REPORT = 300_000_000;

  private final ExceptionDataCollection myExceptionDataCollection = ExceptionDataCollection.getInstance();

  private final ExceptionRateLimiter exceptionRateLimiter = new ExceptionRateLimiter();

  @SuppressWarnings("unused")  // Called reflectively.
  public AndroidStudioSystemHealthMonitor() {
    this(new StudioReportDatabase(new File(PathManager.getTempPath(), "reports.dmp")));
  }

  public AndroidStudioSystemHealthMonitor(StudioReportDatabase studioReportDatabase) {
    myProperties = PropertiesComponent.getInstance();
    myReportsDatabase = studioReportDatabase;
  }

  public void addHeapReportToDatabase(@NotNull UnanalyzedHeapReport report) {
    // Removed from IntelliJ
  }

  public boolean hasPendingHeapReport() throws IOException {
    return false;
  }

  private final List<Runnable> myOomListeners = new LinkedList<>();
  private final Executor myOomListenersExecutor =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("OutOfMemory-notifier", 1);

  public void registerOutOfMemoryErrorListener(Runnable runnable, Disposable parentDisposable) {
    myOomListeners.add(runnable);
    Disposer.register(parentDisposable, () -> myOomListeners.remove(runnable));
  }

  public static @Nullable AndroidStudioSystemHealthMonitor getInstance() {
    return null;
  }

  public static Integer getMaxHistogramReportsCount() {
    return MAX_HISTOGRAM_REPORTS_COUNT;
  }

  private static long freeUpMemory() {
    long usedMemoryBefore = getUsedMemory();

    // Following code should trigger clearing of all weak/soft references. Quite often free memory + soft/weak referenced memory will be
    // less than Integer.MAX_VALUE, therefore the loop will not allocate anything and will fail quite fast.
    ArrayList<byte[]> list = new ArrayList<>();
    try {
      //noinspection InfiniteLoopStatement
      while (true) {
        // Maximum size of the array that can be allocated is Int.MAX_VALUE - 2.
        list.add(new byte[Integer.MAX_VALUE - 2]);
      }
    }
    catch (OutOfMemoryError ignored) {
      // ignore
    }
    finally {
      list.clear();
      // Help GC collect the list object earlier
      list = null;
      // Try to force reclaiming just allocated objects
      System.gc();
    }
    long usedMemoryAfter = getUsedMemory();

    long freedMemory = (usedMemoryAfter > usedMemoryBefore) ? 0 : usedMemoryBefore - usedMemoryAfter;
    UsageTracker.log(AndroidStudioEvent.newBuilder().setKind(EventKind.HEAP_REPORT_EVENT).setHeapReportEvent(
      HeapReportEvent.newBuilder().setStatus(HeapReportEvent.Status.FORCED_GC).setFreedMemory(freedMemory).build()));
    return freedMemory;
  }

  private static long getUsedMemory() {
    return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
  }

  public boolean lowMemoryDetected(MemoryReportReason reason) {
    return false;
  }

  private static long getMyPID() {
    String pidAndMachineName = ManagementFactory.getRuntimeMXBean().getName();
    String[] split = pidAndMachineName.split("@");
    long pid = -1;
    if (split.length == 2) {
      try {
        pid = Long.parseLong(split[0]);
      }
      catch (NumberFormatException ignore) {
      }
    }
    return pid;
  }

  public static void recordGcPauseTime(String gcName, long durationMs) {
    GcPauseInfo.GcType gcType = getGcType(gcName);
    myGcPauseInfo.computeIfAbsent(gcType, (unused) -> new SingleWriterRecorder(1))
      .recordValue(Math.min(durationMs, MAX_GC_PAUSE_TIME_MS));
  }

  private static GcPauseInfo.GcType getGcType(String name) {
    switch (name) {
      case "Copy":
        return GcPauseInfo.GcType.SERIAL_YOUNG;
      case "MarkSweepCompact":
        return GcPauseInfo.GcType.SERIAL_OLD;
      case "PS Scavenge":
        return GcPauseInfo.GcType.PARALLEL_YOUNG;
      case "PS MarkSweep":
        return GcPauseInfo.GcType.PARALLEL_OLD;
      case "ParNew":
        return GcPauseInfo.GcType.CMS_YOUNG;
      case "ConcurrentMarkSweep":
        return GcPauseInfo.GcType.CMS_OLD;
      case "G1 Young Generation":
        return GcPauseInfo.GcType.G1_YOUNG;
      case "G1 Old Generation":
        return GcPauseInfo.GcType.G1_OLD;
      default:
        return GcPauseInfo.GcType.UNKNOWN;
    }
  }

  public void start() {
    // Removed from IntelliJ
  }

  public void startInternal() {

  }

  protected void registerPlatformEventsListener() {
    // Removed - no-op
  }

  private AtomicBoolean ourOomOccurred = new AtomicBoolean(false);

  private boolean handleExceptionEvent(IdeaLoggingEvent event, VMOptions.MemoryKind kind) {
    Throwable t = event.getThrowable();

    if (t instanceof OutOfMemoryError) {
      myOomListenersExecutor.execute(() -> myOomListeners.forEach(Runnable::run));
    }

    if (myExceptionDataCollection.requiresConfirmation(t)) {
      UploadFields fields = myExceptionDataCollection.getExceptionUploadFields(event.getThrowable(), false, true);
      List<Attachment> attachments = new ArrayList<>();
      fields.getLogs().forEach((name, log) -> {
        Attachment attachment = new Attachment("log_" + name + ".log", log);
        attachment.setIncluded(true);
        attachments.add(attachment);
      });
      MessagePool.getInstance().addIdeFatalMessage(
        LogMessage.createEvent(event.getThrowable(), event.getMessage(), attachments.toArray(new Attachment[0]))
      );
      return true;
    }

    // track exception count
    if (AnalyticsSettings.getOptedIn()) {
      if (t != null) {
        if (isReportableCrash(t)) {
          reportThrowableToCrash(t);
        }
      }
    }

    try {
      if (kind != null && !ourOomOccurred.getAndSet(true)) {
        // TODO: Report histogram and heap report on OOM
      }

      // if exception should not be shown in the errors UI then report it as handled.
      boolean showUI = isIdeErrorsDialogReportableCrash(t) || ApplicationManager.getApplication().isInternal();
      return !showUI;
    }
    catch (Throwable throwable) {
      LOG.warn("Exception while handling exception event", throwable);
      return false;
    }
  }

  private void reportThrowableToCrash(Throwable t) {
    incrementAndSaveExceptionCount(t);
    ErrorReportSubmitter reporter = null;
    if (reporter != null) {
      StackTrace stackTrace = ExceptionRegistry.INSTANCE.register(t);
      String signature = ExceptionDataCollection.Companion.calculateSignature(t);
      ExceptionRateLimiter.Permit permit = exceptionRateLimiter.tryAcquireForSignature(signature);
      if (permit.getPermissionType() == ExceptionRateLimiter.PermissionType.ALLOW) {
        IdeaLoggingEvent e = new AndroidStudioExceptionEvent(
          t.getMessage(), t, stackTrace,
          signature,
          permit.getGlobalExceptionCounter(),
          permit.getLocalExceptionCounter(),
          permit.getDeniedSinceLastAllow());
        reporter.submit(new IdeaLoggingEvent[]{e}, null, null, info -> {
        });
      }
    }
  }

  private static boolean isIdeErrorsDialogReportableCrash(Throwable t) {
    int maxCauseDepth = 100;
    while (maxCauseDepth > 0 && t.getCause() != null) {
      t = t.getCause();
      maxCauseDepth--;
    }
    // Report exceptions with too long cause chains.
    if (t.getCause() != null) return true;

    // Report all out of memory errors
    if (t instanceof OutOfMemoryError) return true;

    String className = t.getClass().getName();
    if (className != null) {
      if (className.equals("com.intellij.psi.PsiInvalidElementAccessException")) return false;
      if (className.equals("com.intellij.openapi.project.IndexNotReadyException")) return false;
      if (className.equals("com.intellij.openapi.util.TraceableDisposable.ObjectNotDisposedException")) return false;
      if (className.equals("com.intellij.openapi.util.TraceableDisposable$DisposalException")) return false;
      if (className.equals("com.intellij.openapi.wm.impl.FocusManagerImpl$1")) return false;
    }

    StackTraceElement[] stackTraceElements = t.getStackTrace();
    String firstFrame = "";
    String lastFrame = "";
    if (stackTraceElements != null && stackTraceElements.length >= 1) {
      firstFrame = stackTraceElements[0].getClassName() + "#" + stackTraceElements[0].getMethodName();
      int lastIndex = stackTraceElements.length - 1;
      lastFrame = stackTraceElements[lastIndex].getClassName() + "#" + stackTraceElements[lastIndex].getMethodName();
    }

    // Don't show Logger.error in errors dialog.
    if (firstFrame.equals("com.intellij.openapi.diagnostic.Logger#error") && Objects.equals(t.getClass(), Throwable.class)) {
      return false;
    }

    // Report only exceptions on EDT
    if (!lastFrame.equals("java.awt.EventDispatchThread#run")) {
      return false;
    }

    return true;
  }

  private static boolean isReportableCrash(@NotNull Throwable t) {
    if (t instanceof ClassNotFoundException) {
      String cls = t.getMessage();
      if (cls != null && cls.startsWith("com.sun.jdi.")) {
        // Running on a JRE. We're already warning about that in the System Health Monitor.
        // https://code.google.com/p/android/issues/detail?id=225130
        return false;
      }
    }
    return true;
  }

  private static void incrementAndSaveExceptionCount(@NotNull Throwable t) {
    incrementAndSaveExceptionCount();
    PluginId pluginId = PluginUtil.getInstance().findPluginId(t);
    if (pluginId != null) {
      IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(pluginId);
      if (plugin != null && plugin.isBundled()) {
        incrementAndSaveBundledPluginsExceptionCount();
      }
      else {
        incrementAndSaveNonBundledPluginsExceptionCount();
      }
    }
  }

  // Use this method to log crash events, so crashes on internal builds don't get logged.
  private static void logUsageOnlyIfNotInternalApplication(AndroidStudioEvent.Builder eventBuilder) {
    if (!ApplicationManager.getApplication().isInternal()) {
      UsageTracker.log(eventBuilder);
    }
    else {
      LOG.debug("SystemHealthMonitor would send following analytics event in the release build: " + eventBuilder.build());
    }
  }

  // Use this method to log crash events, so crashes on internal builds don't get logged.
  private static void logUsageOnlyIfNotInternalApplication(long eventTimeMs, AndroidStudioEvent.Builder eventBuilder) {
    if (!ApplicationManager.getApplication().isInternal()) {
      UsageTracker.log(eventTimeMs, eventBuilder);
    }
    else {
      logUsageOnlyIfNotInternalApplication(eventBuilder);
    }
  }

  void showNotification(@PropertyKey(resourceBundle = "messages.AndroidBundle") String key,
                        @Nullable NotificationAction action,
                        Object... params) {
    boolean ignored = myProperties.isValueSet("ignore." + key);
    LOG.info("issue detected: " + key + (ignored ? " (ignored)" : ""));
    if (ignored) return;

    Notification notification = new MyFullContentNotification(AndroidBundle.message(key, params));
    if (action != null) {
      notification.addAction(action);
    }
    notification.addAction(new NotificationAction(IdeBundle.message("sys.health.acknowledge.action")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        notification.expire();
        myProperties.setValue("ignore." + key, "true");
      }
    });
    notification.setImportant(true);

    ApplicationManager.getApplication().invokeLater(() -> Notifications.Bus.notify(notification));
  }

  private final class MyFullContentNotification extends MyNotification implements NotificationFullContent {
    public MyFullContentNotification(@NotNull String content) {
      super(content);
    }
  }

  class MyNotification extends Notification {
    public MyNotification(@NotNull String content) {
      super(myGroup.getDisplayId(), "", content, NotificationType.WARNING);
    }
  }

  static NotificationAction detailsAction(String url) {
    return new BrowseNotificationAction(IdeBundle.message("sys.health.details"), url);
  }

  private static void incrementAndSaveExceptionCount() {
    persistExceptionCount(ourStudioExceptionCount.incrementAndGet(), STUDIO_EXCEPTION_COUNT_FILE);
    if (ApplicationManager.getApplication().isInternal()) {
      // should be 0, but accounting for possible crashes in other threads..
      assert Math.abs(getPersistedExceptionCount(STUDIO_EXCEPTION_COUNT_FILE) - ourStudioExceptionCount.get()) < 5;
    }
  }

  private static void incrementAndSaveBundledPluginsExceptionCount() {
    persistExceptionCount(ourBundledPluginsExceptionCount.incrementAndGet(), BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE);
  }

  private static void incrementAndSaveNonBundledPluginsExceptionCount() {
    persistExceptionCount(ourNonBundledPluginsExceptionCount.incrementAndGet(), NON_BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE);
  }

  private static void persistExceptionCount(long count, @NotNull String countFileName) {
    synchronized (EXCEPTION_COUNT_LOCK) {
      try {
        File f = new File(PathManager.getTempPath(), countFileName);
        Files.write(Long.toString(count), f, StandardCharsets.UTF_8);
      }
      catch (Throwable ignored) {
      }
    }
  }

  private static long getPersistedExceptionCount(@NotNull String countFileName) {
    synchronized (EXCEPTION_COUNT_LOCK) {
      try {
        File f = new File(PathManager.getTempPath(), countFileName);
        String contents = Files.toString(f, StandardCharsets.UTF_8);
        return Long.parseLong(contents);
      }
      catch (Throwable t) {
        return 0;
      }
    }
  }

  /**
   * Collect usage stats for action invocations.
   */
  public static void countActionInvocation(@NotNull AnAction anAction,
                                           @NotNull Presentation templatePresentation,
                                           @NotNull AnActionEvent event) {
    ourStudioActionCount.incrementAndGet();
    Class actionClass = anAction.getClass();
    synchronized (ACTION_INVOCATIONS_LOCK) {
      String actionName = getActionName(actionClass, templatePresentation);
      InvocationKind invocationKind = getInvocationKindFromEvent(event);

      // We aggregate actions the user takes many times in the course of editing code (key events, copy/paste etc...)
      // other actions are logged directly (our logging mechanism batches the uploads, but timestamps will be accurate).
      if (shouldAggregate(actionClass)) {
        Multiset<InvocationKind> invocations = ourActionInvocations.get(actionName);
        if (invocations == null) {
          invocations = LinkedHashMultiset.create();
          ourActionInvocations.put(actionName, invocations);
        }
        invocations.add(invocationKind);
      }
      else {
        UIActionStats.Builder uiActionStatbuilder = UIActionStats.newBuilder()
          .setActionClassName(actionName)
          .setInvocationKind(invocationKind)
          .setInvocations(1)
          .setDirect(true)
          .setUiPlace(event.getPlace());
        if (anAction instanceof ToggleAction) {
          ToggleAction toggleAction = (ToggleAction)anAction;
          // events are tracked right before they occur, therefore take the negation of the current state
          uiActionStatbuilder.setTogglingOn(!toggleAction.isSelected(event));
        }
        AndroidStudioEvent.Builder builder = buildStudioUiEvent(uiActionStatbuilder);
        UsageTracker.log(builder);
      }
    }
  }

  @NotNull
  private static AndroidStudioEvent.Builder buildStudioUiEvent(UIActionStats.Builder uiActionStatbuilder) {
    return AndroidStudioEvent.newBuilder()
      .setCategory(EventCategory.STUDIO_UI)
      .setKind(EventKind.STUDIO_UI_ACTION_STATS)
      .setUiActionStats(uiActionStatbuilder);
  }

  /**
   * Checks if the action is one we need to aggregate.
   * We only aggregate actions the user takes many times in the course of editing code (key events, copy/paste etc...).
   */
  private static boolean shouldAggregate(Class actionClass) {
    return EditorAction.class.isAssignableFrom(actionClass)
           || UndoRedoAction.class.isAssignableFrom(actionClass)
           || PasteAction.class.isAssignableFrom(actionClass)
           || CopyAction.class.isAssignableFrom(actionClass)
           || CutAction.class.isAssignableFrom(actionClass)
           || SaveAllAction.class.isAssignableFrom(actionClass)
           || DeleteAction.class.isAssignableFrom(actionClass)
           || NextOccurenceAction.class.isAssignableFrom(actionClass)
           || PreviousOccurenceAction.class.isAssignableFrom(actionClass);
  }

  /**
   * Takes the current stats on action invocations and reports them through the {@link UsageTracker}.
   * Resets invocation counts by clearing the map.
   */
  private static void reportActionInvocations() {
    Map<String, Multiset<InvocationKind>> currentInvocations;
    synchronized (ACTION_INVOCATIONS_LOCK) {
      currentInvocations = ourActionInvocations;
      ourActionInvocations = new HashMap<>();
    }

    for (Map.Entry<String, Multiset<InvocationKind>> actionEntry : currentInvocations.entrySet()) {
      for (Multiset.Entry<InvocationKind> invocationEntry : actionEntry.getValue().entrySet()) {
        UsageTracker.log(AndroidStudioEvent.newBuilder()
                           .setCategory(EventCategory.STUDIO_UI)
                           .setKind(EventKind.STUDIO_UI_ACTION_STATS)
                           .setUiActionStats(UIActionStats.newBuilder()
                                               .setActionClassName(actionEntry.getKey())
                                               .setInvocationKind(invocationEntry.getElement())
                                               .setInvocations(invocationEntry.getCount())));
      }
    }

    StudioPerformanceStats.Builder statsProto = StudioPerformanceStats.newBuilder();
    for (Map.Entry<GcPauseInfo.GcType, SingleWriterRecorder> gcEntry : myGcPauseInfo.entrySet()) {
      statsProto.addGcPauseInfo(GcPauseInfo.newBuilder()
                                  .setCollectorType(gcEntry.getKey())
                                  .setPauseTimesMs(HistogramUtil.toProto(gcEntry.getValue().getIntervalHistogram())));
    }
    UsageTracker.log(AndroidStudioEvent.newBuilder()
                       .setCategory(EventCategory.STUDIO_UI)
                       .setKind(EventKind.STUDIO_PERFORMANCE_STATS)
                       .setStudioPerformanceStats(statsProto));
  }

  /**
   * Determines the way an event was invoked for usage tracking.
   */
  private static InvocationKind getInvocationKindFromEvent(AnActionEvent event) {
    if (event.getInputEvent() instanceof KeyEvent) {
      return InvocationKind.KEYBOARD_SHORTCUT;
    }
    String place = event.getPlace();
    if (place.contains("Menu")) {
      return InvocationKind.MENU;
    }
    if (place.contains("Toolbar")) {
      return InvocationKind.TOOLBAR;
    }
    if (event.getInputEvent() instanceof MouseEvent) {
      return InvocationKind.MOUSE;
    }
    return InvocationKind.UNKNOWN_INVOCATION_KIND;
  }

  /**
   * Gets an action name based on its class. For Android Studio code, we use simple names for plugins we use canonical names.
   */
  static String getActionName(@NotNull Class actionClass, @NotNull Presentation templatePresentation) {
    return null;
  }

  public static class AndroidStudioExceptionEvent extends IdeaLoggingEvent {
    private final StackTrace myStackTrace;

    private final String signature;
    private final int exceptionIndex;
    private final int signatureIndex;
    private final int deniedSinceLastAllow;

    public AndroidStudioExceptionEvent(String message, Throwable throwable,
                                       @NotNull StackTrace stackTrace,
                                       @NotNull String signature,
                                       int globalCount,
                                       int signatureIndex,
                                       int deniedSinceLastAllow) {
      super(message, throwable);
      myStackTrace = stackTrace;
      this.signature = signature;
      this.exceptionIndex = globalCount;
      this.signatureIndex = signatureIndex;
      this.deniedSinceLastAllow = deniedSinceLastAllow;
    }

    public String getSignature() {
      return signature;
    }

    public int getExceptionIndex() {
      return exceptionIndex;
    }

    public int getSignatureIndex() {
      return signatureIndex;
    }

    public int getDeniedSinceLastAllow() {
      return deniedSinceLastAllow;
    }

    @Nullable
    @Override
    public Object getData() {
      return ImmutableMap.of("Type", "Exception", // keep consistent with the error reporter in android plugin
                             "md5", myStackTrace.md5string(),
                             "summary", myStackTrace.summarize(50),
                             "exceptionIndex", exceptionIndex,
                             "signatureIndex", signatureIndex,
                             "deniedSinceLastAllow", deniedSinceLastAllow);
    }
  }
}
