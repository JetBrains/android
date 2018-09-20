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
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.diagnostics.crash.StudioCrashReporter;
import com.android.tools.idea.diagnostics.crash.StudioHistogramReport;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.io.Files;
import com.google.wireless.android.sdk.stats.*;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind;
import com.google.wireless.android.sdk.stats.UIActionStats.InvocationKind;
import com.intellij.concurrency.JobScheduler;
import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.diagnostic.IdePerformanceListener;
import com.intellij.diagnostic.ThreadDump;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.AndroidStudioSystemHealthMonitorAdapter;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.ExceptionRegistry;
import com.intellij.ide.HistogramUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.StackTrace;
import com.intellij.ide.actions.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.analytics.StudioCrashDetails;
import com.intellij.internal.statistic.analytics.StudioCrashDetection;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.notification.*;
import com.intellij.notification.impl.NotificationFullContent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.*;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import org.HdrHistogram.Histogram;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import sun.tools.attach.HotSpotVirtualMachine;

/**
 * Extension to System Health Monitor that includes Android Studio-specific code.
 * <p>
 * Note: The component is initialized only in Android Studio. The code should be in ADT plugin, but it cannot be put there
 *   as there are platform calls into it, e.g. calls to recordEventTime(), reportException().
 */
public class AndroidStudioSystemHealthMonitor implements BaseComponent {
  private static final Logger LOG = Logger.getInstance(AndroidStudioSystemHealthMonitor.class);

  // The group should be registered by SystemHealthMonitor
  private final NotificationGroup myGroup = NotificationGroup.findRegisteredGroup("System Health");

  /** Count of action events fired. This is used as a proxy for user initiated activity in the IDE. */
  public static final AtomicLong ourStudioActionCount = new AtomicLong(0);
  private static final String STUDIO_ACTIVITY_COUNT = "studio.activity.count";

  /** Count of non fatal exceptions in the IDE. */
  private static final AtomicLong ourStudioExceptionCount = new AtomicLong(0);
  private static final AtomicLong ourInitialPersistedExceptionCount = new AtomicLong(0);
  private static final AtomicLong ourBundledPluginsExceptionCount = new AtomicLong(0);
  private static final AtomicLong ourNonBundledPluginsExceptionCount = new AtomicLong(0);

  private static final Object EXCEPTION_COUNT_LOCK = new Object();
  @NonNls private static final String STUDIO_EXCEPTION_COUNT_FILE = "studio.exc";
  @NonNls private static final String BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE = "studio.exb";
  @NonNls private static final String NON_BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE = "studio.exp";

  /**
   * Histogram of event timings, in milliseconds. Must be accessed from the EDT.
   */
  private static final Histogram myEventDurationsMs = new Histogram(1);
  /** Maximum freeze duration to record. Longer freeze durations are truncated to keep the size of the histogram bounded. */
  private static final long MAX_EVENT_DURATION_MS = 30 * 60 * 1000;
  /**
   * Histogram of write lock wait times, in milliseconds. Must be accessed from the EDT.
   */
  private static final Histogram myWriteLockWaitTimesMs = new Histogram(1);
  /** Maximum freeze duration to record. Longer freeze durations are truncated to keep the size of the histogram bounded. */
  private static final long MAX_WRITE_LOCK_WAIT_TIME_MS = 30 * 60 * 1000;

  private static final long TOO_MANY_EXCEPTIONS_THRESHOLD = 10000;

  private final StudioReportDatabase myReportsDatabase = new StudioReportDatabase(new File(PathManager.getTempPath(), "reports.dmp"));

  private static final Object ACTION_INVOCATIONS_LOCK = new Object();
  private static final Lock REPORT_EXCEPTIONS_LOCK = new ReentrantLock();
  // Updates to ourActionInvocations need to be done synchronized on ACTION_INVOCATIONS_LOCK to avoid updates during usage reporting.
  private static Map<String, Multiset<InvocationKind>> ourActionInvocations = new HashMap<>();

  private final PropertiesComponent myProperties;
  private AndroidStudioSystemHealthMonitorAdapter.EventsListener myListener;
  private boolean myTooManyExceptionsPromptShown = false;
  private static long ourCurrentSessionStudioExceptionCount = 0;

  private static AndroidStudioSystemHealthMonitor ourInstance;

  public AndroidStudioSystemHealthMonitor(@NotNull PropertiesComponent properties) {
    myProperties = properties;
    if (ourInstance != null) {
      LOG.warn("Multiple instances of AndroidStudioSystemHealthMonitor!");
    } else {
      ourInstance = this;
    }
  }

  public static @Nullable AndroidStudioSystemHealthMonitor getInstance() {
    return ourInstance;
  }

  public void addHistogramToDatabase(@Nullable String description) {
    try {
      Path histogramDirPath = createHistogramPath();
      if (java.nio.file.Files.exists(histogramDirPath)) {
        LOG.info("Histogram path already exists: " + histogramDirPath.toString());
        return;
      }
      java.nio.file.Files.createDirectories(histogramDirPath);
      Path histogramFilePath = histogramDirPath.resolve("histogram.txt");
      java.nio.file.Files.write(histogramFilePath, getHistogram().getBytes(), StandardOpenOption.CREATE);

      Path threadDumpFilePath = histogramDirPath.resolve("threadDump.txt");
      FileUtil.writeToFile(threadDumpFilePath.toFile(), ThreadDumper.dumpThreadsToString());

      myReportsDatabase.appendHistogram(threadDumpFilePath, histogramFilePath, description);
    } catch (IOException e) {
      LOG.info("Exception while creating histogram", e);
    }
  }

  private static Path createHistogramPath() {
    String datePart = new SimpleDateFormat("yyyyMMdd-HHmmss").format(System.currentTimeMillis());
    String dirName = "threadDumps-histogram-" + datePart;
    return Paths.get(PathManager.getLogPath(), dirName);
  }

  private static long getMyPID() {
    String pidAndMachineName = ManagementFactory.getRuntimeMXBean().getName();
    String[] split = pidAndMachineName.split("@");
    long pid = -1;
    if (split.length == 2) {
      try {
        pid = Long.parseLong(split[0]);
      } catch (NumberFormatException ignore) {
      }
    }
    return pid;
  }

  private static String getHistogram() throws UnsupportedOperationException {
    StringBuilder sb = new StringBuilder();
    VirtualMachine vm;
    try {
      vm = VirtualMachine.attach(Long.toString(getMyPID()));
      if (!(vm instanceof HotSpotVirtualMachine)) {
        throw new UnsupportedOperationException();
      }
      HotSpotVirtualMachine hotSpotVM = (HotSpotVirtualMachine) vm;
      char[] chars = new char[1024];
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(hotSpotVM.heapHisto("-live")))) {
        int read;
        while ((read = reader.read(chars)) != -1) {
          sb.append(chars, 0, read);
        }
      }
    } catch (AttachNotSupportedException | IOException e) {
      throw new UnsupportedOperationException(e);
    }
    String fullHistogram = sb.toString();
    String[] lines = fullHistogram.split("\r?\n");
    final int TOP_LINES = 103;
    final int BOTTOM_LINES = 1;
    if (lines.length <= TOP_LINES + BOTTOM_LINES) {
      return sb.toString();
    }
    sb.setLength(0);
    for (int i = 0; i < TOP_LINES; i++) {
      sb.append(lines[i]).append("\n");
    }
    sb.append("[...]\n");
    for (int i = lines.length - BOTTOM_LINES; i < lines.length; i++) {
      sb.append(lines[i]).append("\n");
    }
    return sb.toString();
  }

  public static void recordEventTime(int interval, long durationMs) {
    myEventDurationsMs.recordValueWithCount(Math.min(durationMs, MAX_EVENT_DURATION_MS), interval);
  }

  public static void recordWriteLockWaitTime(long durationMs) {
    myWriteLockWaitTimesMs.recordValueWithCount(Math.min(durationMs, MAX_WRITE_LOCK_WAIT_TIME_MS), 1);
  }

  @Override
  public void initComponent() {
    assert myGroup != null;
    registerPlatformEventsListener();

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      checkRuntime();
    });

    if (!ApplicationManager.getApplication().isInternal() && !StatisticsUploadAssistant.isSendAllowed()) {
      return;
    }
    ourStudioActionCount.set(myProperties.getOrInitLong(STUDIO_ACTIVITY_COUNT, 0L) + 1);
    ourStudioExceptionCount.set(getPersistedExceptionCount(STUDIO_EXCEPTION_COUNT_FILE));
    ourInitialPersistedExceptionCount.set(ourStudioExceptionCount.get());
    ourBundledPluginsExceptionCount.set(getPersistedExceptionCount(BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE));
    ourNonBundledPluginsExceptionCount.set(getPersistedExceptionCount(NON_BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE));

    StudioCrashDetection.updateRecordedVersionNumber(ApplicationInfo.getInstance().getStrictVersion());
    startActivityMonitoring();
    trackCrashes(StudioCrashDetection.reapCrashDescriptions());
    trackStudioReports(myReportsDatabase.reapReportDetails());

    Application application = ApplicationManager.getApplication();
    application.getMessageBus().connect(application).subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
      @Override
      public void appClosing() {
        myProperties.setValue(STUDIO_ACTIVITY_COUNT, Long.toString(ourStudioActionCount.get()));
        StudioCrashDetection.stop();
        reportExceptionsAndActionInvocations();
      }
    });

    application.getMessageBus().connect(application).subscribe(IdePerformanceListener.TOPIC, new IdePerformanceListener.Adapter() {
      @Override
      public void uiFreezeFinished(int lengthInSeconds) {
        // track how long the IDE was frozen
        UsageTracker.log(AndroidStudioEvent.newBuilder()
            .setKind(EventKind.STUDIO_PERFORMANCE_STATS)
            .setStudioPerformanceStats(StudioPerformanceStats.newBuilder()
                .setUiFreezeTimeMs(lengthInSeconds * 1000)));
      }

      @Override
      public void dumpedThreads(@NotNull File toFile, @NotNull ThreadDump dump) {
        // We don't want to add additional overhead when the IDE is already slow, so we just note down the file to which the threads
        // were dumped.
        try {
          myReportsDatabase.appendPerformanceThreadDump(toFile.toPath(), "UIFreeze");
        }
        catch (IOException ignored) { // don't worry about errors during analytics events
        }
      }
    });
  }

  @Override
  public void disposeComponent() {
    if (myListener != null) {
      AndroidStudioSystemHealthMonitorAdapter.unregisterEventsListener(myListener);
    }
    ourInstance = null;
  }

  private void registerPlatformEventsListener() {
    myListener = new AndroidStudioSystemHealthMonitorAdapter.EventsListener() {

      @Override
      public void countActionInvocation(Class<? extends AnAction> aClass, Presentation presentation, AnActionEvent event) {
        AndroidStudioSystemHealthMonitor.countActionInvocation(aClass, presentation, event);
      }

      @Override
      public void incrementAndSaveBundledPluginsExceptionCount() {
        AndroidStudioSystemHealthMonitor.incrementAndSaveBundledPluginsExceptionCount();
      }

      @Override
      public void incrementAndSaveExceptionCount() {
        AndroidStudioSystemHealthMonitor.incrementAndSaveExceptionCount();

      }

      @Override
      public void incrementAndSaveNonBundledPluginsExceptionCount() {
        AndroidStudioSystemHealthMonitor.incrementAndSaveNonBundledPluginsExceptionCount();
      }

      @Override
      public void recordEventTime(int interval, long elapsed) {
        AndroidStudioSystemHealthMonitor.recordEventTime(interval, elapsed);
      }

      @Override
      public void reportException(Throwable t, StackTrace trace) {
        AndroidStudioSystemHealthMonitor.reportException(t, trace);
      }

      @Override
      public void recordWriteLockWaitTime(long elapsed) {
        AndroidStudioSystemHealthMonitor.recordWriteLockWaitTime(elapsed);
      }
    };
    AndroidStudioSystemHealthMonitorAdapter.registerEventsListener(myListener);
  }

  /**
   * Android Studio-specific checks of Java runtime.
   */
  private void checkRuntime() {
    warnIfOpenJDK();
    warnIfMacDragNDropJDKBug();
  }

  private void warnIfOpenJDK() {
    if (StringUtil.containsIgnoreCase(System.getProperty("java.vm.name", ""), "OpenJDK") &&
        !SystemInfo.isJetbrainsJvm && !SystemInfo.isStudioJvm) {
      showNotification("unsupported.jvm.openjdk.message", null);
    }
  }

  private void warnIfMacDragNDropJDKBug() {
    if (SystemInfoRt.isMac &&
        !SystemInfo.isJetbrainsJvm &&
        !SystemInfo.isStudioJvm &&
        SystemInfo.isJavaVersionAtLeast("1.8.0_60") &&
        !SystemInfo.isJavaVersionAtLeast("1.8.0_76")) {
      // Upstream JDK8 bug tracked by https://bugs.openjdk.java.net/browse/JDK-8134917, affecting 1.8.0_60 up to 1.8.0_76.
      // Fixed by Jetbrains in their 1.8.0_40-b108 JRE and tracked in https://youtrack.jetbrains.com/issue/IDEA-146691
      showNotification("unsupported.jvm.dragndrop.message", null);
    }
  }

  private void reportExceptionsAndActionInvocations() {
    if (!REPORT_EXCEPTIONS_LOCK.tryLock()) {
      return;
    }
    try {
      long activityCount = ourStudioActionCount.getAndSet(0);
      long exceptionCount = ourStudioExceptionCount.getAndSet(0);
      long bundledPluginExceptionCount = ourBundledPluginsExceptionCount.getAndSet(0);
      long nonBundledPluginExceptionCount = ourNonBundledPluginsExceptionCount.getAndSet(0);
      persistExceptionCount(0, STUDIO_EXCEPTION_COUNT_FILE);
      persistExceptionCount(0, BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE);
      persistExceptionCount(0, NON_BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE);
      ourCurrentSessionStudioExceptionCount += exceptionCount;

      if (ApplicationManager.getApplication().isInternal()) {
        // should be 0, but accounting for possible crashes in other threads..
        assert getPersistedExceptionCount(STUDIO_EXCEPTION_COUNT_FILE) < 5;
      }

      if (activityCount > 0 || exceptionCount > 0) {
        List<StackTrace> traces = ExceptionRegistry.INSTANCE.getStackTraces(0);
        ExceptionRegistry.INSTANCE.clear();
        trackExceptionsAndActivity(activityCount, exceptionCount, bundledPluginExceptionCount, nonBundledPluginExceptionCount, 0, traces);
      }
      if (!myTooManyExceptionsPromptShown &&
          ourCurrentSessionStudioExceptionCount >= TOO_MANY_EXCEPTIONS_THRESHOLD) {
        promptUnusuallyHighExceptionCount();
      }
      reportActionInvocations();
    } finally {
      REPORT_EXCEPTIONS_LOCK.unlock();
    }
  }

  private void promptUnusuallyHighExceptionCount() {
    // Show the prompt only once per session
    myTooManyExceptionsPromptShown = true;

    AnAction sendFeedback = ActionManager.getInstance().getAction("SendFeedback");
    NotificationAction notificationAction = NotificationAction.create(
      AndroidBundle.message("sys.health.send.feedback"),
      (event, notification) -> sendFeedback.actionPerformed(event)
    );
    showNotification("sys.health.too.many.exceptions", notificationAction);
  }

  private static void trackStudioReports(@NotNull List<StudioReportDetails> reports) {
    if (reports.isEmpty()) {
      return;
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      reports.stream().filter(r -> r.getType().equals("PerformanceThreadDump")).limit(10).forEach(r -> {
        if (r.getThreadDumpPath() == null) {
          return;
        }
        try {
          List<String> lines = java.nio.file.Files.readAllLines(r.getThreadDumpPath());
          reportAnr(r.getThreadDumpPath().getFileName().toString(), lines);
        }
        catch (IOException e) {
          // Ignore
        }
      });
      reports.stream().filter(r -> r.getType().equals("Histogram")).limit(10).forEach(r -> reportHistogram(r));
    });
  }

  public static void trackCrashes(@NotNull List<StudioCrashDetails> descriptions) {
    if (descriptions.isEmpty()) {
      return;
    }

    reportCrashes(descriptions);
    trackExceptionsAndActivity(0, 0, 0, 0, descriptions.size(), Collections.emptyList());
  }

  public static void trackExceptionsAndActivity(final long activityCount,
                                                final long exceptionCount,
                                                final long bundledPluginExceptionCount,
                                                final long nonBundledPluginExceptionCount,
                                                final long fatalExceptionCount,
                                                @NotNull List<StackTrace> stackTraces) {
    if (!StatisticsUploadAssistant.isSendAllowed()) {
      return;
    }

    // Log statistics (action/exception counts)
    final AndroidStudioEvent.Builder eventBuilder =
      AndroidStudioEvent.newBuilder()
        .setCategory(EventCategory.PING)
        .setKind(EventKind.STUDIO_CRASH)
        .setStudioCrash(StudioCrash.newBuilder()
          .setActions(activityCount)
          .setExceptions(exceptionCount)
          .setBundledPluginExceptions(bundledPluginExceptionCount)
          .setNonBundledPluginExceptions(nonBundledPluginExceptionCount)
          .setCrashes(fatalExceptionCount));
    logUsageOnlyIfNotInternalApplication(eventBuilder);

    // Log each stacktrace as a separate log event with the timestamp of when it was first hit
    for (StackTrace stackTrace : stackTraces) {
      final AndroidStudioEvent.Builder crashEventBuilder =
        AndroidStudioEvent.newBuilder()
          .setCategory(EventCategory.PING)
          .setKind(EventKind.STUDIO_CRASH)
          .setStudioCrash(StudioCrash.newBuilder()
            .addDetails(StudioExceptionDetails.newBuilder()
              .setHash(stackTrace.md5string())
              .setCount(stackTrace.getCount())
              .setSummary(stackTrace.summarize(20))
              .build()));
      logUsageOnlyIfNotInternalApplication(stackTrace.timeOfFirstHitMs(), crashEventBuilder);
    }
  }

  // Use this method to log crash events, so crashes on internal builds don't get logged.
  private static void logUsageOnlyIfNotInternalApplication(AndroidStudioEvent.Builder eventBuilder) {
    if (!ApplicationManager.getApplication().isInternal()) {
      UsageTracker.log(eventBuilder);
    } else {
      LOG.debug("SystemHealthMonitor would send following analytics event in the release build: " + eventBuilder.build());
    }
  }

  // Use this method to log crash events, so crashes on internal builds don't get logged.
  private static void logUsageOnlyIfNotInternalApplication(long eventTimeMs, AndroidStudioEvent.Builder eventBuilder) {
    if (!ApplicationManager.getApplication().isInternal()) {
      UsageTracker.log(eventTimeMs, eventBuilder);
    } else {
      logUsageOnlyIfNotInternalApplication(eventBuilder);
    }
  }

  private void showNotification(@PropertyKey(resourceBundle = "messages.AndroidBundle") String key,
                                @Nullable NotificationAction action,
                                Object... params) {
    boolean ignored = myProperties.isValueSet("ignore." + key);
    LOG.info("issue detected: " + key + (ignored ? " (ignored)" : ""));
    if (ignored) return;

    Notification notification = new MyNotification(AndroidBundle.message(key, params));
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

  private final class MyNotification extends Notification implements NotificationFullContent {
    public MyNotification(@NotNull String content) {
      super(myGroup.getDisplayId(), "", content, NotificationType.WARNING);
    }
  }

  private static NotificationAction detailsAction(String url) {
    return new BrowseNotificationAction(IdeBundle.message("sys.health.details"), url);
  }

  private static final int INITIAL_DELAY_MINUTES = 1; // send out pending activity soon after startup
  private static final int INTERVAL_IN_MINUTES = 30;

  private void startActivityMonitoring() {
    JobScheduler.getScheduler().scheduleWithFixedDelay(this::reportExceptionsAndActionInvocations, INITIAL_DELAY_MINUTES, INTERVAL_IN_MINUTES, TimeUnit.MINUTES);
  }

  public static void incrementAndSaveExceptionCount() {
    persistExceptionCount(ourStudioExceptionCount.incrementAndGet(), STUDIO_EXCEPTION_COUNT_FILE);
    if (ApplicationManager.getApplication().isInternal()) {
      // should be 0, but accounting for possible crashes in other threads..
      assert Math.abs(getPersistedExceptionCount(STUDIO_EXCEPTION_COUNT_FILE) - ourStudioExceptionCount.get()) < 5;
    }
  }

  public static void incrementAndSaveBundledPluginsExceptionCount() {
    persistExceptionCount(ourBundledPluginsExceptionCount.incrementAndGet(), BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE);
  }

  public static void incrementAndSaveNonBundledPluginsExceptionCount() {
    persistExceptionCount(ourNonBundledPluginsExceptionCount.incrementAndGet(), NON_BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE);
  }

  private static void persistExceptionCount(long count, @NotNull String countFileName) {
    synchronized (EXCEPTION_COUNT_LOCK) {
      try {
        File f = new File(PathManager.getTempPath(), countFileName);
        Files.write(Long.toString(count), f, Charsets.UTF_8);
      }
      catch (Throwable ignored) {
      }
    }
  }

  private static long getPersistedExceptionCount(@NotNull String countFileName) {
    synchronized (EXCEPTION_COUNT_LOCK) {
      try {
        File f = new File(PathManager.getTempPath(), countFileName);
        String contents = Files.toString(f, Charsets.UTF_8);
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
  public static void countActionInvocation(@NotNull Class actionClass, @NotNull Presentation templatePresentation, @NotNull AnActionEvent event) {
    ourStudioActionCount.incrementAndGet();
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
      } else {
        UsageTracker.log(AndroidStudioEvent.newBuilder()
            .setCategory(EventCategory.STUDIO_UI)
            .setKind(EventKind.STUDIO_UI_ACTION_STATS)
            .setUiActionStats(UIActionStats.newBuilder()
                .setActionClassName(actionName)
                .setInvocationKind(invocationKind)
                .setInvocations(1)
                .setDirect(true)));
      }
    }
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
    Map<String, Multiset<InvocationKind>> currentInvocations = null;
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

    // Move to the EDT since myEventDurationsMs structure can only be accessed from that thread.
    ApplicationManager.getApplication().invokeLater(() -> {
      StudioPerformanceStats.Builder statsProto =
          StudioPerformanceStats.newBuilder()
              .setEventServiceTimeSamplePeriod(IdeEventQueue.EVENT_TIMING_INTERVAL)
              .setEventServiceTimeMs(HistogramUtil.toProto(myEventDurationsMs))
              .setWriteLockWaitTimeMs(HistogramUtil.toProto(myWriteLockWaitTimesMs));
      UsageTracker.log(AndroidStudioEvent.newBuilder()
                           .setCategory(EventCategory.STUDIO_UI)
                           .setKind(EventKind.STUDIO_PERFORMANCE_STATS)
                           .setStudioPerformanceStats(statsProto));
      myEventDurationsMs.reset();
      myWriteLockWaitTimesMs.reset();
    });
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
    Class currentClass = actionClass;
    while (currentClass.isAnonymousClass()) {
      currentClass = currentClass.getEnclosingClass();
    }
    String packageName = currentClass.getPackage().getName();
    if (packageName.startsWith("com.android.") || packageName.startsWith("com.intellij.") || packageName.startsWith("org.jetbrains.") ||
        packageName.startsWith("or.intellij.") || packageName.startsWith("com.jetbrains.") || packageName.startsWith("git4idea.")) {

      String actionName = currentClass.getSimpleName();
      // ExecutorAction points to many different Run/Debug actions, we use the template text to distinguish.
      if (actionName.equals("ExecutorAction")) {
        actionName += "#" + templatePresentation.getText();
      }
      return actionName;
    }
    return currentClass.getCanonicalName();
  }

  public static void reportException(@NotNull Throwable t, @NotNull StackTrace stackTrace) {
    if (!AnalyticsSettings.getOptedIn()) {
      return;
    }

    ErrorReportSubmitter reporter = IdeErrorsDialog.getAndroidErrorReporter();
    if (reporter != null) {
      IdeaLoggingEvent e = new AndroidStudioExceptionEvent(t.getMessage(), t, stackTrace);
      reporter.submit(new IdeaLoggingEvent[]{e}, null, null, info -> {
      });
    }
  }

  private static void reportAnr(@NotNull String fileName, @NotNull List<String> threadDump) {
    if (!AnalyticsSettings.getOptedIn()) {
      return;
    }

    ErrorReportSubmitter reporter = IdeErrorsDialog.getAndroidErrorReporter();
    if (reporter != null) {
      IdeaLoggingEvent e = new AndroidStudioAnrEvent(fileName, Joiner.on('\n').join(threadDump));
      reporter.submit(new IdeaLoggingEvent[]{e}, null, null, info -> {
      });
    }
  }

  private static void reportCrashes(@NotNull List<StudioCrashDetails> descriptions) {
    if (!AnalyticsSettings.getOptedIn()) {
      return;
    }

    ErrorReportSubmitter reporter = IdeErrorsDialog.getAndroidErrorReporter();
    if (reporter != null) {
      IdeaLoggingEvent e = new AndroidStudioCrashEvents(descriptions);
      reporter.submit(new IdeaLoggingEvent[]{e}, null, null, info -> {
      });
    }
  }

  private static void reportHistogram(StudioReportDetails details) {
    if (!AnalyticsSettings.getOptedIn()) {
      return;
    }

    try {
      if (details.getHistogramPath() == null || details.getThreadDumpPath() == null) {
        return;
      }
      StudioHistogramReport histogramReport = new StudioHistogramReport.Builder()
        .setThreadDump(new String(Files.asCharSource(details.getThreadDumpPath().toFile(), Charsets.UTF_8).read()))
        .setHistogram(new String(Files.asCharSource(details.getHistogramPath().toFile(), Charsets.UTF_8).read()))
        .build();
      // Performance reports are not limited by a rate limiter.
      StudioCrashReporter.getInstance().submit(histogramReport, true);
    }
    catch (IOException e) {
      // Ignore
    }
  }

  private static class AndroidStudioExceptionEvent extends IdeaLoggingEvent {
    private final StackTrace myStackTrace;

    public AndroidStudioExceptionEvent(String message, Throwable throwable, @NotNull StackTrace stackTrace) {
      super(message, throwable);
      myStackTrace = stackTrace;
    }

    @Nullable
    @Override
    public Object getData() {
      return ImmutableMap.of("Type", "Exception", // keep consistent with the error reporter in android plugin
                             "md5", myStackTrace.md5string(),
                             "summary", myStackTrace.summarize(50));
    }
  }

  private static class AndroidStudioAnrEvent extends IdeaLoggingEvent {
    private final String myFileName;
    private final String myThreadDump;

    public AndroidStudioAnrEvent(@NotNull String fileName, @NotNull String threadDump) {
      super("", null);
      myFileName = fileName;
      myThreadDump = threadDump;
    }

    @Nullable
    @Override
    public Object getData() {
      return ImmutableMap.of("Type", "ANR", // keep consistent with the error reporter in android plugin
                             "file", myFileName,
                             "threadDump", myThreadDump);
    }
  }

  private static class AndroidStudioCrashEvents extends IdeaLoggingEvent {
    private List<StudioCrashDetails> myCrashDetails;

    public AndroidStudioCrashEvents(@NotNull List<StudioCrashDetails> crashDetails) {
      super("", null);
      myCrashDetails = crashDetails;
    }

    @Nullable
    @Override
    public Object getData() {
      return ImmutableMap.of("Type", "Crashes", // keep consistent with the error reporter in android plugin
                             "crashDetails", myCrashDetails);
    }
  }

}
