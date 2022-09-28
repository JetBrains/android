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

import static com.android.tools.idea.diagnostics.heap.ComponentsSet.MEMORY_USAGE_REPORTING_SERVER_FLAG_NAME;

import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.HistogramUtil;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.diagnostics.crash.ExceptionDataCollection;
import com.android.tools.idea.diagnostics.crash.StudioCrashReporter;
import com.android.tools.idea.diagnostics.crash.UploadFields;
import com.android.tools.idea.diagnostics.heap.HeapSnapshotTraverseService;
import com.android.tools.idea.diagnostics.hprof.action.AnalysisRunnable;
import com.android.tools.idea.diagnostics.hprof.action.HeapDumpSnapshotRunnable;
import com.android.tools.idea.diagnostics.jfr.RecordingManager;
import com.android.tools.idea.diagnostics.kotlin.KotlinPerfCounters;
import com.android.tools.idea.diagnostics.report.DiagnosticReport;
import com.android.tools.idea.diagnostics.report.FreezeReport;
import com.android.tools.idea.diagnostics.report.HistogramReport;
import com.android.tools.idea.diagnostics.report.MemoryReportReason;
import com.android.tools.idea.diagnostics.report.PerformanceThreadDumpReport;
import com.android.tools.idea.diagnostics.report.UnanalyzedHeapReport;
import com.android.tools.idea.serverflags.ServerFlagService;
import com.android.tools.idea.serverflags.protos.MemoryUsageReportConfiguration;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.io.Files;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind;
import com.google.wireless.android.sdk.stats.GcPauseInfo;
import com.google.wireless.android.sdk.stats.StudioCrash;
import com.google.wireless.android.sdk.stats.StudioExceptionDetails;
import com.google.wireless.android.sdk.stats.StudioPerformanceStats;
import com.google.wireless.android.sdk.stats.UIActionStats;
import com.google.wireless.android.sdk.stats.UIActionStats.InvocationKind;
import com.intellij.concurrency.JobScheduler;
import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.diagnostic.IdePerformanceListener;
import com.intellij.diagnostic.LogMessage;
import com.intellij.diagnostic.MessagePool;
import com.intellij.diagnostic.ThreadDump;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.diagnostic.VMOptions;
import com.intellij.ide.AndroidStudioSystemHealthMonitorAdapter;
import com.intellij.ide.AppLifecycleListener;
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
import com.intellij.internal.statistic.analytics.StudioCrashDetails;
import com.intellij.internal.statistic.analytics.StudioCrashDetection;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.notification.BrowseNotificationAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.impl.NotificationFullContent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.HdrHistogram.SingleWriterRecorder;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
import sun.tools.attach.HotSpotVirtualMachine;

/**
 * Extension to System Health Monitor that includes Android Studio-specific code.
 */
@Service
public final class AndroidStudioSystemHealthMonitor {
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

  private static final int MAX_PERFORMANCE_REPORTS_COUNT =
    Integer.getInteger("studio.diagnostic.performanceThreadDump.maxReports", 0);
  private static final int MAX_HISTOGRAM_REPORTS_COUNT =
    Integer.getInteger("studio.diagnostic.histogram.maxReports", 10);
  private static final int MAX_FREEZE_REPORTS_COUNT =
    Integer.getInteger("studio.diagnostic.freeze.maxReports",
                       ApplicationManager.getApplication().isEAP() ? 20 : 1);

  private static final ConcurrentMap<GcPauseInfo.GcType, SingleWriterRecorder> myGcPauseInfo = new ConcurrentHashMap<>();
  /** Maximum GC pause duration to record. Longer pause durations are truncated to keep the size of the histogram bounded. */
  private static final long MAX_GC_PAUSE_TIME_MS = 30 * 60 * 1000;

  private static final long TOO_MANY_EXCEPTIONS_THRESHOLD = 10000;

  private final StudioReportDatabase myReportsDatabase;
  public static final HProfDatabase ourHProfDatabase = new HProfDatabase(Paths.get(PathManager.getTempPath()));

  private static final Object ACTION_INVOCATIONS_LOCK = new Object();
  private static final Lock REPORT_EXCEPTIONS_LOCK = new ReentrantLock();
  // Updates to ourActionInvocations need to be done synchronized on ACTION_INVOCATIONS_LOCK to avoid updates during usage reporting.
  private static Map<String, Multiset<InvocationKind>> ourActionInvocations = new HashMap<>();

  private final PropertiesComponent myProperties;
  private AndroidStudioSystemHealthMonitorAdapter.EventsListener myListener;
  private boolean myTooManyExceptionsPromptShown = false;
  private static long ourCurrentSessionStudioExceptionCount = 0;

  // If GC cleared more than MEMORY_FREED_THRESHOLD_FOR_HEAP_REPORT of unreachable/weak/soft references, then ignore the notification.
  public static final long MEMORY_FREED_THRESHOLD_FOR_HEAP_REPORT = 100_000_000;

  // If there is more free memory than FREE_MEMORY_THRESHOLD_FOR_HEAP_REPORT, ignore the notification.
  public static final long FREE_MEMORY_THRESHOLD_FOR_HEAP_REPORT = 300_000_000;

  private final ExceptionDataCollection myExceptionDataCollection = ExceptionDataCollection.getInstance();

  @SuppressWarnings("unused")  // Called reflectively.
  public AndroidStudioSystemHealthMonitor() {
    this(new StudioReportDatabase(new File(PathManager.getTempPath(), "reports.dmp")));
  }

  public AndroidStudioSystemHealthMonitor(StudioReportDatabase studioReportDatabase) {
    myProperties = PropertiesComponent.getInstance();
    myReportsDatabase = studioReportDatabase;
  }

  public void addHeapReportToDatabase(@NotNull UnanalyzedHeapReport report) {
    try {
      myReportsDatabase.appendReport(report);
    } catch (IOException e) {
      LOG.warn("Exception when adding heap report to database", e);
    }
  }

  public boolean hasPendingHeapReport() throws IOException {
    return myReportsDatabase.getReports().stream().anyMatch(r -> r instanceof UnanalyzedHeapReport);
  }

  public static @NotNull AndroidStudioSystemHealthMonitor getInstance() {
    return ApplicationManager.getApplication().getService(AndroidStudioSystemHealthMonitor.class);
  }

  public static Integer getMaxHistogramReportsCount() {
    return MAX_HISTOGRAM_REPORTS_COUNT;
  }

  public static Integer getMaxPerformanceReportsCount() {
    return MAX_PERFORMANCE_REPORTS_COUNT;
  }

  public static Integer getMaxFreezeReportsCount() {
    return MAX_FREEZE_REPORTS_COUNT;
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
    } catch (OutOfMemoryError ignored) {
      // ignore
    } finally {
      list.clear();
      // Help GC collect the list object earlier
      list = null;
      // Try to force reclaiming just allocated objects
      System.gc();
    }
    long usedMemoryAfter = getUsedMemory();

    return (usedMemoryAfter > usedMemoryBefore) ? 0 : usedMemoryBefore - usedMemoryAfter;
  }

  private static long getUsedMemory() {
    return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
  }

  private static long getFreeMemory() {
    return Runtime.getRuntime().maxMemory() - getUsedMemory();
  }

  private final Lock lowMemoryDetectedLock = new ReentrantLock();
  private boolean memoryReportCreated = false;

  public boolean lowMemoryDetected(MemoryReportReason reason) {
    // Ignore concurrent calls to the method.
    if (!lowMemoryDetectedLock.tryLock()) {
      return false;
    }
    try {
      if (reason != MemoryReportReason.OutOfMemory) {
        // Don't clear weak/soft references if there is still plenty of free memory
        long freeMemory = getFreeMemory();
        if (freeMemory >= FREE_MEMORY_THRESHOLD_FOR_HEAP_REPORT) {
          return false;
        }

        // Free up some memory by clearing weak/soft references
        long memoryFreed = freeUpMemory();
        LOG.warn("Forced clear of soft/weak references. Reason: " + reason + ", freed memory: " + (memoryFreed / 1_000_000) + "MB");
        if (memoryFreed >= MEMORY_FREED_THRESHOLD_FOR_HEAP_REPORT) {
          // Enough memory was freed, so there is no reason to send the report.
          return false;
        }
      }

      // Create only one report per session
      if (!memoryReportCreated) {
        memoryReportCreated = true;
        addHistogramToDatabase(reason, "LowMemoryWatcher");
        ApplicationManager.getApplication()
          .invokeLater(new HeapDumpSnapshotRunnable(reason, HeapDumpSnapshotRunnable.AnalysisOption.SCHEDULE_ON_NEXT_START));
      }

      return true;
    } finally {
      lowMemoryDetectedLock.unlock();
    }
  }

  public void addHistogramToDatabase(MemoryReportReason reason, @Nullable String description) {
    try {
      Path histogramDirPath = createHistogramPath();
      if (java.nio.file.Files.exists(histogramDirPath)) {
        LOG.info("Histogram path already exists: " + histogramDirPath.toString());
        return;
      }
      java.nio.file.Files.createDirectories(histogramDirPath);
      Path histogramFilePath = histogramDirPath.resolve("histogram.txt");
      java.nio.file.Files.write(histogramFilePath, getHistogram().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);

      Path threadDumpFilePath = histogramDirPath.resolve("threadDump.txt");
      FileUtil.writeToFile(threadDumpFilePath.toFile(), ThreadDumper.dumpThreadsToString());

      myReportsDatabase.appendReport(new HistogramReport(threadDumpFilePath, histogramFilePath, reason, description));
    } catch (IOException e) {
      LOG.info("Exception while creating histogram", e);
    }
  }

  private static Path createHistogramPath() {
    String datePart = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(System.currentTimeMillis());
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
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(hotSpotVM.heapHisto("-live"), Charsets.UTF_8))) {
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

  public static void recordGcPauseTime(String gcName, long durationMs) {
    GcPauseInfo.GcType gcType = getGcType(gcName);
    myGcPauseInfo.computeIfAbsent(gcType, (unused) -> new SingleWriterRecorder(1))
      .recordValue(Math.min(durationMs, MAX_GC_PAUSE_TIME_MS));
  }

  private static GcPauseInfo.GcType getGcType (String name) {
    switch (name) {
      case "Copy": return GcPauseInfo.GcType.SERIAL_YOUNG;
      case "MarkSweepCompact": return GcPauseInfo.GcType.SERIAL_OLD;
      case "PS Scavenge": return GcPauseInfo.GcType.PARALLEL_YOUNG;
      case "PS MarkSweep": return GcPauseInfo.GcType.PARALLEL_OLD;
      case "ParNew": return GcPauseInfo.GcType.CMS_YOUNG;
      case "ConcurrentMarkSweep": return GcPauseInfo.GcType.CMS_OLD;
      case "G1 Young Generation": return GcPauseInfo.GcType.G1_YOUNG;
      case "G1 Old Generation": return GcPauseInfo.GcType.G1_OLD;
      default: return GcPauseInfo.GcType.UNKNOWN;
    }
  }

  public void start() {
    assert myGroup != null;
    Application application = ApplicationManager.getApplication();
    registerPlatformEventsListener();

    application.executeOnPooledThread(this::checkRuntime);

    if (ServerFlagService.Companion.getInstance()
          .getProtoOrNull(MEMORY_USAGE_REPORTING_SERVER_FLAG_NAME, MemoryUsageReportConfiguration.getDefaultInstance()) != null) {
      HeapSnapshotTraverseService.getInstance().addMemoryReportCollectionRequest();
    }

    List<DiagnosticReport> reports = myReportsDatabase.reapReports();
    processDiagnosticReports(reports);

    if (application.isInternal() || StatisticsUploadAssistant.isSendAllowed()) {
      initDataCollection();
    }

    if (application.isInternal()) {
      try {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        ObjectName beanName = new ObjectName("com.android.tools.idea.diagnostics.kotlin:type=KotlinPerfCounters");
        mBeanServer.registerMBean(new KotlinPerfCounters(), beanName);
      } catch (Exception ex) {
        LOG.debug(ex);
      }
    }
  }

  private void initDataCollection() {
    Application application = ApplicationManager.getApplication();

    ourStudioActionCount.set(myProperties.getLong(STUDIO_ACTIVITY_COUNT, 0L) + 1);
    ourStudioExceptionCount.set(getPersistedExceptionCount(STUDIO_EXCEPTION_COUNT_FILE));
    ourInitialPersistedExceptionCount.set(ourStudioExceptionCount.get());
    ourBundledPluginsExceptionCount.set(getPersistedExceptionCount(BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE));
    ourNonBundledPluginsExceptionCount.set(getPersistedExceptionCount(NON_BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE));

    StudioCrashDetection.updateRecordedVersionNumber(ApplicationInfo.getInstance().getStrictVersion());
    startActivityMonitoring();
    trackCrashes(StudioCrashDetection.reapCrashDescriptions());
    RecordingManager.init();

    application.getMessageBus().connect(application).subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appClosing() {
        myProperties.setValue(STUDIO_ACTIVITY_COUNT, Long.toString(ourStudioActionCount.get()));
        StudioCrashDetection.stop();
        reportExceptionsAndActionInvocations();
      }
    });

    application.getMessageBus().connect(application).subscribe(IdePerformanceListener.TOPIC, new IdePerformanceListener() {
      @Override
      public void uiFreezeFinished(long durationMs, @Nullable File reportDir) {
        // track how long the IDE was frozen
        UsageTracker.log(AndroidStudioEvent.newBuilder()
            .setKind(EventKind.STUDIO_PERFORMANCE_STATS)
            .setStudioPerformanceStats(StudioPerformanceStats.newBuilder()
                .setUiFreezeTimeMs((int)durationMs)));
      }

      @Override
      public void dumpedThreads(@NotNull File toFile, @NotNull ThreadDump dump) {
        // We don't want to add additional overhead when the IDE is already slow, so we just note down the file to which the threads
        // were dumped.
        try {
          myReportsDatabase.appendReport(new PerformanceThreadDumpReport(toFile.toPath(), "UIFreeze"));
        }
        catch (IOException ignored) { // don't worry about errors during analytics events
        }
      }
    });
    ThreadSamplingReport.startCollectingThreadSamplingReports(this::tryAppendReportToDatabase);
  }

  /**
   * @return List of paths to hprof files to be analyzed
   */
  private static List<Path> startHeapReportsAnalysis(List<UnanalyzedHeapReport> reports) {
    if (reports.isEmpty()) return Collections.emptyList();

    // Start only one analysis, even if there are more hprof files captured.
    final UnanalyzedHeapReport report = reports.get(0);
    final Path path = report.getHprofPath();

    ProjectManager projectManager = ProjectManager.getInstanceIfCreated();
    Project[] openedProjects = projectManager != null ? projectManager.getOpenProjects() : null;

    if (openedProjects != null && openedProjects.length > 0) {
      Project project = openedProjects[0];
      StartupManager.getInstance(project).runWhenProjectIsInitialized(
        () -> new AnalysisRunnable(report, true).run()
      );
    } else {
      MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
      AtomicBoolean eventHandled = new AtomicBoolean(false);

      connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
        @Override
        public void projectOpened(@NotNull Project project) {
          if (eventHandled.getAndSet(true)) {
            return;
          }
          connection.disconnect();
          StartupManager.getInstance(project).runWhenProjectIsInitialized(
            () -> new AnalysisRunnable(report, true).run());
        }
      });
      connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
        @Override
        public void welcomeScreenDisplayed() {
          if (eventHandled.getAndSet(true)) {
            return;
          }
          connection.disconnect();
          new AnalysisRunnable(report, true).run();
        }
      });
    }
    return Collections.singletonList(path);
  }

  private boolean tryAppendReportToDatabase(DiagnosticReport report) {
    try {
      myReportsDatabase.appendReport(report);
      return true;
    }
    catch (IOException ignored) {
      return false;
    }
  }

  protected void registerPlatformEventsListener() {
    myListener = new AndroidStudioSystemHealthMonitorAdapter.EventsListener() {

      @Override
      public void countActionInvocation(Class<? extends AnAction> aClass, Presentation presentation, AnActionEvent event) {
        AndroidStudioSystemHealthMonitor.countActionInvocation(aClass, presentation, event);
      }

      @Override
      public boolean handleExceptionEvent(IdeaLoggingEvent event, VMOptions.MemoryKind memoryKind) {
        return AndroidStudioSystemHealthMonitor.this.handleExceptionEvent(event, memoryKind);
      }
    };
    AndroidStudioSystemHealthMonitorAdapter.registerEventsListener(myListener);
  }

  private AtomicBoolean ourOomOccurred = new AtomicBoolean(false);

  private boolean handleExceptionEvent(IdeaLoggingEvent event, VMOptions.MemoryKind kind) {
    Throwable t = event.getThrowable();

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
          incrementAndSaveExceptionCount(t);
          ErrorReportSubmitter reporter = IdeErrorsDialog.getAndroidErrorReporter();
          if (reporter != null) {
            StackTrace stackTrace = ExceptionRegistry.INSTANCE.register(t);
            IdeaLoggingEvent e = new AndroidStudioExceptionEvent(t.getMessage(), t, stackTrace);
            reporter.submit(new IdeaLoggingEvent[]{e}, null, null, info -> {
            });
          }
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
    } catch (Throwable throwable) {
      LOG.warn("Exception while handling exception event", throwable);
      return false;
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
    if (firstFrame.equals("com.intellij.openapi.diagnostic.Logger#error") && Objects.equals(t.getClass(), Throwable.class))
      return false;

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

  /**
   * Android Studio-specific checks of Java runtime.
   */
  private void checkRuntime() {
    warnIfOpenJDK();
  }

  private void warnIfOpenJDK() {
    if (StringUtil.containsIgnoreCase(System.getProperty("java.vm.name", ""), "OpenJDK") &&
        !SystemInfo.isJetBrainsJvm && !SystemInfo.isStudioJvm) {
      showNotification("unsupported.jvm.openjdk.message", null);
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

  private static void processDiagnosticReports(@NotNull List<DiagnosticReport> reports) {
    if (AnalyticsSettings.getOptedIn()) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        sendDiagnosticReportsOfTypeWithLimit(PerformanceThreadDumpReport.REPORT_TYPE, reports, MAX_PERFORMANCE_REPORTS_COUNT);
        sendDiagnosticReportsOfTypeWithLimit(HistogramReport.REPORT_TYPE, reports, MAX_HISTOGRAM_REPORTS_COUNT);
        sendDiagnosticReportsOfTypeWithLimit(FreezeReport.REPORT_TYPE, reports, MAX_FREEZE_REPORTS_COUNT);
      });
    }

    processHeapReports(reports);
  }

  private static void processHeapReports(@NotNull List<DiagnosticReport> reports) {
    List<Path> hprofsToBeAnalyzed = startHeapReportsAnalysis(reports
                         .stream()
                         .filter(r -> r.getType().equals("UnanalyzedHeap"))
                         .filter(r -> r instanceof UnanalyzedHeapReport)
                         .map(r -> (UnanalyzedHeapReport) r)
                         .collect(Collectors.toList()));
    ourHProfDatabase.cleanupHProfFiles(hprofsToBeAnalyzed);
  }

  private static void sendDiagnosticReportsOfTypeWithLimit(String type,
                                                           @NotNull List<DiagnosticReport> reports,
                                                           int maxCount) {
    List<DiagnosticReport> reportsOfType = reports.stream().filter(r -> r.getType().equals(type)).collect(
      Collectors.toList());

    if (!type.equals(FreezeReport.REPORT_TYPE)) {
      Collections.shuffle(reportsOfType);
    }

    int numReportsToSkip = reportsOfType.size() > maxCount ? reportsOfType.size() - maxCount : 0;

    reportsOfType.stream().skip(numReportsToSkip).forEach(AndroidStudioSystemHealthMonitor::sendDiagnosticReport);
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
    public MyFullContentNotification (@NotNull String content) {
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

  private static final int INITIAL_DELAY_MINUTES = 1; // send out pending activity soon after startup
  private static final int INTERVAL_IN_MINUTES = 30;

  private void startActivityMonitoring() {
    JobScheduler.getScheduler().scheduleWithFixedDelay(this::reportExceptionsAndActionInvocations, INITIAL_DELAY_MINUTES, INTERVAL_IN_MINUTES, TimeUnit.MINUTES);
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
                .setDirect(true)
                .setUiPlace(event.getPlace())));
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
    if (actionClass.isAnonymousClass()) {
      Class enclosingClass = actionClass.getEnclosingClass();
      Class superClass = actionClass.getSuperclass();
      return String.format("%s@%s", metricsNameForClass(superClass), metricsNameForClass(enclosingClass));
    }

    String actionName = metricsNameForClass(actionClass);
    if (actionName.equals("ExecutorAction")) {
      actionName += "#" + templatePresentation.getText();
    }
    return actionName;
  }

  private static String metricsNameForClass(Class cls) {
    Package classPackage = cls.getPackage();
    String packageName = classPackage != null ? classPackage.getName() : "";
    if (packageName.startsWith("com.android.") || packageName.startsWith("com.intellij.") || packageName.startsWith("org.jetbrains.") ||
        packageName.startsWith("org.intellij.") || packageName.startsWith("com.jetbrains.") || packageName.startsWith("git4idea.")) {

      String actionName = cls.getSimpleName();
      Class parentClass = cls.getEnclosingClass();
      while (parentClass != null) {
        actionName = String.format("%s.%s", parentClass.getSimpleName(), actionName);
        parentClass = parentClass.getEnclosingClass();
      }
      return actionName;
    }
    return cls.getCanonicalName();
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

  private static void sendDiagnosticReport(@NotNull DiagnosticReport report) {
    if (!AnalyticsSettings.getOptedIn()) {
      return;
    }

    try {
      // Performance reports are not limited by a rate limiter.
      StudioCrashReporter.getInstance().submit(report.asCrashReport(), true);
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
