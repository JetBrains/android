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

import com.google.common.collect.Maps;
import com.intellij.diagnostic.AbstractMessage;
import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.diagnostic.ReportMessages;
import com.intellij.errorreport.bean.ErrorBean;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
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
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import org.jetbrains.android.diagnostics.error.IdeaITNProxy;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.Map;

/** Sends crash reports to Google. Patterned after {@link com.intellij.diagnostic.ITNReporter} */
public class ErrorReporter extends ErrorReportSubmitter {

  private static final String FEEDBACK_TASK_TITLE = "Submitting error report";

  @Override
  public String getReportActionText() {
    return AndroidBundle.message("error.report.to.google.action");
  }

  @Override
  public boolean submit(@NotNull IdeaLoggingEvent[] events, String additionalInfo, @NotNull Component parentComponent, @NotNull Consumer<SubmittedReportInfo> consumer) {
    ErrorBean errorBean = new ErrorBean(events[0].getThrowable(), IdeaLogger.ourLastActionId);
    return doSubmit(events[0], parentComponent, consumer, errorBean, additionalInfo);
  }

  private static boolean doSubmit(final IdeaLoggingEvent event,
                                  final Component parentComponent,
                                  final Consumer<SubmittedReportInfo> callback,
                                  final ErrorBean bean,
                                  final String description) {
    final DataContext dataContext = DataManager.getInstance().getDataContext(parentComponent);

    bean.setDescription(description);
    bean.setMessage(event.getMessage());

    Throwable t = event.getThrowable();
    if (t != null) {
      final PluginId pluginId = IdeErrorsDialog.findPluginId(t);
      if (pluginId != null) {
        final IdeaPluginDescriptor ideaPluginDescriptor = PluginManager.getPlugin(pluginId);
        if (ideaPluginDescriptor != null && !ideaPluginDescriptor.isBundled()) {
          bean.setPluginName(ideaPluginDescriptor.getName());
          bean.setPluginVersion(ideaPluginDescriptor.getVersion());
        }
      }
    }

    Object data = event.getData();

    if (data instanceof AbstractMessage) {
      bean.setAttachments(((AbstractMessage)data).getIncludedAttachments());
    }

    final Project project = CommonDataKeys.PROJECT.getData(dataContext);

    Consumer<String> successCallback = new Consumer<String>() {
      @Override
      public void consume(String token) {
        final SubmittedReportInfo reportInfo = new SubmittedReportInfo(
          null, "Issue " + token, SubmittedReportInfo.SubmissionStatus.NEW_ISSUE);
        callback.consume(reportInfo);

        ReportMessages.GROUP.createNotification(ReportMessages.ERROR_REPORT,
                                                "Submitted",
                                                NotificationType.INFORMATION,
                                                null).setImportant(false).notify(project);
      }
    };

    Consumer<Exception> errorCallback = new Consumer<Exception>() {
      @Override
      public void consume(Exception e) {
        // TODO: check for updates
        String message = AndroidBundle.message("error.report.at.b.android", e.getMessage());
        ReportMessages.GROUP.createNotification(ReportMessages.ERROR_REPORT, message, NotificationType.ERROR,
                                                NotificationListener.URL_OPENING_LISTENER).setImportant(false).notify(project);
      }
    };

    Task.Backgroundable feedbackTask;
    if (data instanceof ErrorReportCustomizer) {
      feedbackTask = ((ErrorReportCustomizer) data).makeReportingTask(project, FEEDBACK_TASK_TITLE, true, bean, successCallback, errorCallback);
    } else {
      List<Pair<String, String>> kv = IdeaITNProxy
        .getKeyValuePairs(null, null, bean, IdeaLogger.getOurCompilationTimestamp(), ApplicationManager.getApplication(),
                          (ApplicationInfoEx)ApplicationInfo.getInstance(), ApplicationNamesInfo.getInstance(),
                          UpdateSettings.getInstance());

      feedbackTask =
        new AnonymousFeedbackTask(project, FEEDBACK_TASK_TITLE, true, t, pair2map(kv), bean.getMessage(), bean.getDescription(),
                                  ApplicationInfo.getInstance().getFullVersion(), successCallback, errorCallback);
    }
    if (project == null) {
      feedbackTask.run(new EmptyProgressIndicator());
    } else {
      ProgressManager.getInstance().run(feedbackTask);
    }
    return true;
  }

  private static Map<String, String> pair2map(List<Pair<String, String>> kv) {
    Map<String, String> m = Maps.newHashMapWithExpectedSize(kv.size());

    for (Pair<String, String> i : kv) {
      m.put(i.getFirst(), i.getSecond());
    }

    return m;
  }
}
