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

package com.android.tools.idea.stats;

import com.android.annotations.NonNull;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.connect.StatisticsConnectionService;
import com.intellij.internal.statistic.connect.StatisticsResult;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.*;

/**
 * Android Statistics Service.
 *
 * Based on idea's RemotelyConfigurableStatisticsService.
 */
public class AndroidStatisticsService implements StatisticsService {

  private static final Logger LOG = Logger.getInstance("#" + AndroidStatisticsService.class.getName());


  @NonNull
  @Override
  public Notification createNotification(@NotNull final String groupDisplayId, @Nullable NotificationListener listener) {
    final String fullProductName = ApplicationNamesInfo.getInstance().getFullProductName();
    final String companyName = ApplicationInfo.getInstance().getCompanyName();

    String text =
      "<html>Please click <a href='allow'>I agree</a> if you want to help make " + fullProductName +
      " better or <a href='decline'>I don't agree</a> otherwise. <a href='settings'>more...</a></html>";

    String title = "Help improve " + fullProductName + " by sending usage statistics to " + companyName;

    return new Notification(groupDisplayId, title,
                            text,
                            NotificationType.INFORMATION,
                            listener);
  }

  @Nullable
  @Override
  public Map<String, String> getStatisticsConfigurationLabels() {
    Map<String, String> labels = new HashMap<String, String>();

    final String fullProductName = ApplicationNamesInfo.getInstance().getFullProductName();
    final String companyName = ApplicationInfo.getInstance().getCompanyName();

    labels.put("title",
               "Help improve " +  fullProductName + " by sending usage statistics to " + companyName);
    labels.put("allow-checkbox",
               "Send usage statistics to " + companyName);
    labels.put("details",
               "<html>This allows " + companyName + " to collect information about your plugins configuration (what is enabled and what is not)" +
               "<br/>and feature usage statistics (e.g. how frequently you're using code completion)." +
               "<br/>This data is collected in accordance with " + companyName + "'s privacy policy.</html>");

    return labels;
  }

  @Override
  public StatisticsResult send() {

    // Get the redirected URL
    final StatisticsConnectionService service = new StatisticsConnectionService();
    final String serviceUrl = service.getServiceUrl();

    // Check server provided an URL and enabled sending stats.
    if (serviceUrl == null) {
      return new StatisticsResult(StatisticsResult.ResultCode.ERROR_IN_CONFIG, "ERROR");
    }
    if (!service.isTransmissionPermitted()) {
      return new StatisticsResult(StatisticsResult.ResultCode.NOT_PERMITTED_SERVER, "NOT_PERMITTED");
    }

    AndroidStatHelper helper = new AndroidStatHelper();

    Gooplp.LogRequest data = getData(helper, service.getDisabledGroups());

    String error = null;
    try {
      IConnectionOpener opener = new IConnectionOpener() {
        @Override
        public HttpURLConnection openHttpConnection(String url) throws IOException {
          HttpURLConnection connection = HttpConfigurable.getInstance().openHttpConnection(url);
          connection.setConnectTimeout(2000);
          connection.setReadTimeout(2000);
          return connection;
        }
      };
      String error2 = helper.sendData(data, opener);
      if (error2 != null) {
        error = error2;
      }
    } catch (Exception e) {
      error = e.getClass().getSimpleName() + " " + (e.getMessage() != null ? e.getMessage() : e.toString());
    }

    LOG.debug("[SendStats/AS] Error " + (error == null ? "None" : error));
    if (error == null) {
      return new StatisticsResult(StatisticsResult.ResultCode.SEND, "OK");
    } else {
      return new StatisticsResult(StatisticsResult.ResultCode.SENT_WITH_ERRORS, error);
    }
  }

  private Gooplp.LogRequest getData(AndroidStatHelper helper, @NotNull Set<String> disabledGroups) {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();

    Map<String, KeyValue[]> usages = new LinkedHashMap<String, KeyValue[]>();
    for (Project project : openProjects) {
      final Map<String, KeyValue[]> allUsages = getAllUsages(project, disabledGroups);
      usages.putAll(usages);
    }

    String uuid = UpdateChecker.getInstallationUID(PropertiesComponent.getInstance());
    String appVersion = ApplicationInfo.getInstance().getFullVersion();
    return helper.createRequest(uuid, appVersion, usages);
  }

  @NotNull
  public Map<String, KeyValue[]> getAllUsages(@Nullable Project project, @NotNull Set<String> disabledGroups) {
    Map<String, KeyValue[]> allUsages = new LinkedHashMap<String, KeyValue[]>();

    for (UsagesCollector usagesCollector : Extensions.getExtensions(UsagesCollector.EP_NAME)) {
      final GroupDescriptor groupDescriptor = usagesCollector.getGroupId();
      final String groupId = groupDescriptor.getId();

      if (!disabledGroups.contains(groupId)) {
        try {
          final Set<UsageDescriptor> usages = usagesCollector.getUsages(project);
          final Set<Counter> counters = new TreeSet<Counter>();
          for (UsageDescriptor usage : usages) {
            Counter counter = new Counter(usage.getKey(), usage.getValue());
            counters.add(counter);
            LOG.info("[" + groupId + "] " + counter); // RM--DEBUG
          }
          allUsages.put(groupId, counters.toArray(new Counter[counters.size()]));

        } catch (CollectUsagesException e) {
          LOG.info(e);
        }
      }
    }

    return allUsages;
  }
}
