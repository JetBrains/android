/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.diagnostics.error;

import com.android.annotations.Nullable;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.diagnostics.AndroidStudioSystemHealthMonitor;
import com.android.tools.idea.diagnostics.StudioCrashDetails;
import com.android.tools.idea.diagnostics.crash.StudioCrashReport;
import com.android.tools.idea.diagnostics.crash.StudioExceptionReport;
import com.android.tools.idea.diagnostics.crash.StudioCrashReporter;
import com.google.common.collect.ImmutableMap;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.SystemHealthEvent;
import com.intellij.diagnostic.AbstractMessage;
import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.diagnostic.ReportMessages;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.idea.IdeaLogger;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.diagnostics.error.ErrorBean;
import org.jetbrains.android.diagnostics.error.IdeaITNProxy;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class AndroidStudioErrorReportSubmitter extends ErrorReportSubmitter {
  private static final Logger LOG = Logger.getInstance(AndroidStudioErrorReportSubmitter.class);
  private static final String FEEDBACK_TASK_TITLE = "Submitting error report";
  private static final long REPORT_ID_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);

  @NotNull
  @Override
  public String getReportActionText() {
    return AndroidBundle.message("error.report.to.google.action");
  }

  @Override
  public boolean submit(@NotNull IdeaLoggingEvent[] events,
                        @Nullable String description,
                        @Nullable Component parentComponent,
                        @NotNull Consumer<? super SubmittedReportInfo> callback) {
    IdeaLoggingEvent event = events[0];
    ErrorBean bean = new ErrorBean(event.getThrowable(), IdeaLogger.ourLastActionId);

    bean.setDescription(description);
    bean.setMessage(event.getMessage());

    IdeaPluginDescriptor plugin = IdeErrorsDialog.getPlugin(event);
    if (plugin != null && (!plugin.isBundled() || plugin.allowBundledUpdate())) {
      bean.setPluginName(plugin.getName());
      bean.setPluginVersion(plugin.getVersion());
    }

    // Early escape (and no UI impact) if these are analytics events being pushed from the platform
    if (handleAnalyticsReports(event, bean)) {
      return true;
    }

    Object data = event.getData();
    if (data instanceof AbstractMessage) {
      bean.setAttachments(((AbstractMessage)data).getIncludedAttachments());
    }

    // Android Studio: SystemHealthMonitor is always calling submit with a null parentComponent. In order to determine the data context
    // associated with the currently-focused component, we run that query on the UI thread and delay the rest of the invocation below.
    java.util.function.Consumer<DataContext> submitter = dataContext -> {
    if (dataContext == null) {
      return;
    }

    final Project project = CommonDataKeys.PROJECT.getData(dataContext);

    Consumer<String> successCallback = token -> {
      final SubmittedReportInfo reportInfo = new SubmittedReportInfo(
        null, "Issue " + token, SubmittedReportInfo.SubmissionStatus.NEW_ISSUE);
      callback.consume(reportInfo);

      ReportMessages.GROUP
        .createNotification("Report Submitted", NotificationType.INFORMATION)
        .setImportant(false)
        .notify(project);
    };

    Consumer<Exception> errorCallback = e -> {
      String message = AndroidBundle.message("error.report.at.b.android", e.getMessage());

      ReportMessages.GROUP
        .createNotification(message, NotificationType.ERROR)
        .setListener(NotificationListener.URL_OPENING_LISTENER)
        .setImportant(false)
        .notify(project);
    };

    Task.Backgroundable feedbackTask;
    if (data instanceof ErrorReportCustomizer) {
      feedbackTask = ((ErrorReportCustomizer) data).makeReportingTask(project, FEEDBACK_TASK_TITLE, true, bean, successCallback, errorCallback);
    } else {
      Map<String, String> errorDataMap = getPlatformErrorData(bean);
      feedbackTask = new SubmitCrashReportTask(project, FEEDBACK_TASK_TITLE, true, event.getThrowable(), errorDataMap, successCallback, errorCallback);
    }

    if (project == null) {
      feedbackTask.run(new EmptyProgressIndicator());
    } else {
      ProgressManager.getInstance().run(feedbackTask);
    }
    };

    if (parentComponent != null) {
      submitter.accept(DataManager.getInstance().getDataContext(parentComponent));
    } else {
      DataManager.getInstance()
                 .getDataContextFromFocusAsync()
                 .onSuccess(submitter);
    }

    return true;
  }

  private static boolean handleAnalyticsReports(@NotNull IdeaLoggingEvent loggingEvent, ErrorBean bean) {
    Object data = loggingEvent.getData();

    if (!(data instanceof Map map)) {
      return false;
    }

    String type = (String)map.get("Type");
    switch (type) {
      case "Exception" -> {
        handleExceptionEvent(loggingEvent, map, bean);
        return true;
      }
      case "Crashes" -> {
        handleCrashesEvent(map);
        return true;
      }
    }
    return false;
  }

  private static void handleCrashesEvent(Map map) {
    //noinspection unchecked
    List<StudioCrashDetails> crashDetails = (List<StudioCrashDetails>)map.get("crashDetails");
    List<String> descriptions = ContainerUtil.map(crashDetails, StudioCrashDetails::getDescription);
    // If at least one report was JVM crash, submit the batch as a JVM crash
    boolean isJvmCrash = crashDetails.stream().anyMatch(StudioCrashDetails::isJvmCrash);
    // As there may be multiple crashes reported together, take the shortest uptime (most of the time there is only
    // a single crash anyway).
    long uptimeInMs = crashDetails.stream().mapToLong(StudioCrashDetails::getUptimeInMs).min().orElse(-1);

    StudioCrashReport.Builder reportBuilder =
      new StudioCrashReport.Builder().setDescriptions(descriptions).setIsJvmCrash(isJvmCrash).setUptimeInMs(uptimeInMs);

    if (isJvmCrash) {
      Optional<StudioCrashDetails> jvmCrashOptional = crashDetails.stream().filter(StudioCrashDetails::isJvmCrash).findAny();
      if (jvmCrashOptional.isPresent()) {
        StudioCrashDetails jvmCrash = jvmCrashOptional.get();
        reportBuilder.setErrorSignal(jvmCrash.getErrorSignal());
        reportBuilder.setErrorFrame(jvmCrash.getErrorFrame());
        reportBuilder.setErrorThread(jvmCrash.getErrorThread());
        reportBuilder.setNativeStack(jvmCrash.getNativeStack());
      }
    }

    StudioCrashReport report = reportBuilder.build();
    // Crash reports are not limited by a rate limiter.
    StudioCrashReporter.getInstance().submit(report, true);
  }

  private static void handleExceptionEvent(@NotNull IdeaLoggingEvent loggingEvent, Map<Object, Object> map, ErrorBean bean) {
    Throwable t = loggingEvent.getThrowable();

    if (t == null) {
      return;
    }

    AndroidStudioSystemHealthMonitor.AndroidStudioExceptionEvent exceptionEvent =
      loggingEvent instanceof AndroidStudioSystemHealthMonitor.AndroidStudioExceptionEvent
      ? (AndroidStudioSystemHealthMonitor.AndroidStudioExceptionEvent)loggingEvent
      : null;

    ImmutableMap.Builder<String, String> mapBuilder = ImmutableMap.builder();
    for (Entry<Object, Object> e : map.entrySet()) {
      mapBuilder.put(e.getKey().toString(), e.getValue().toString());
    }

    Map<String, String> platformMap = getPlatformErrorData(bean);
    for (Entry<String, String> e : platformMap.entrySet()) {
      mapBuilder.put(e.getKey(), e.getValue());
    }

    mapBuilder.put("sessionId", UsageTracker.getSessionId());

    ImmutableMap<String, String> productData = mapBuilder.buildKeepingLast();
    StudioExceptionReport exceptionReport =
      new StudioExceptionReport.Builder().setThrowable(t, false, true).addProductData(productData).build();

    // Submit exception through Crash reporter
    // Note: Exceptions use their own limiter, so it should skip StudioCrashReporter limiter
    CompletableFuture<String> reportIdFuture = StudioCrashReporter.getInstance().submit(exceptionReport, true);

    final long timeMs = AnalyticsSettings.getDateProvider().now().getTime();
    reportIdFuture
      .completeOnTimeout("[timeout:%dms]".formatted(REPORT_ID_TIMEOUT_MS), REPORT_ID_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      .whenComplete(
        (reportId, throwable) -> {
          if (throwable != null) {
            // Exception thrown while uploading report to crash
            reportId = "[exception:%s]".formatted(throwable.getClass().getName());
          }

          SystemHealthEvent.Exception.Builder exceptionBuilder =
            SystemHealthEvent.Exception.newBuilder()
              .setCrashReportId(reportId);

          String signature = "[missing signature]";
          if (exceptionEvent != null) {
            signature = exceptionEvent.getSignature();
            exceptionBuilder
              .setExceptionIndex(exceptionEvent.getExceptionIndex())
              .setSignatureIndex(exceptionEvent.getSignatureIndex())
              .setSignatureReportsSkipped(exceptionEvent.getDeniedSinceLastAllow())
              .setStableSignature(signature);
          }
          final AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder()
            .setKind(AndroidStudioEvent.EventKind.SYSTEM_HEALTH_EVENT)
              .setSystemHealthEvent(
                SystemHealthEvent.newBuilder()
                  .setEventType(SystemHealthEvent.SystemHealthEventType.EXCEPTION)
                  .setException(exceptionBuilder)
              );
          UsageTracker.log(timeMs, event);
          LOG.info("Exception signature: %s, report ID: %s".formatted(signature, reportId));
        });
  }

  @NotNull
  private static Map<String, String> getPlatformErrorData(ErrorBean bean) {
    List<Pair<String, String>> keyValuePairs = IdeaITNProxy
      .getKeyValuePairs(
        null,
        null,
        bean,
        ApplicationManager.getApplication(),
        (ApplicationInfoEx)ApplicationInfo.getInstance(),
        ApplicationNamesInfo.getInstance(),
        UpdateSettings.getInstance());

    ImmutableMap.Builder<String,String> builder = ImmutableMap.builder();
    for (Pair<String, String> p : keyValuePairs) {
      if (p.first != null && p.second != null && !ignoredErrorDataEntry(p.first)) {
        builder.put(p.first, p.second);
      }
    }
    return builder.build();
  }

  private static boolean ignoredErrorDataEntry(@NotNull String key) {
    for (String s : Arrays.asList("os.", "user.", "java.", "error.")) {
      if (key.startsWith(s))
        return true;
    }

    return false;
  }
}
