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
import com.android.tools.idea.startup.AndroidStudioInitializer;
import com.intellij.internal.statistic.connect.StatisticsConnectionService;
import com.intellij.internal.statistic.connect.StatisticsResult;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Android Statistics Service.
 * Based on idea's RemotelyConfigurableStatisticsService.
 * Also sends a legacy ping using ADT's LegacySdkStatsService.
 */
@SuppressWarnings("MethodMayBeStatic")
public class AndroidStatisticsService implements StatisticsService {

  @NonNull
  @Override
  public Notification createNotification(@NotNull final String groupDisplayId,
                                         @Nullable NotificationListener listener) {
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

    labels.put(StatisticsService.TITLE,
               "Help improve " +  fullProductName + " by sending usage statistics to " + companyName);
    labels.put(StatisticsService.ALLOW_CHECKBOX,
               "Send usage statistics to " + companyName);
    labels.put(StatisticsService.DETAILS,
               "<html>This allows " + companyName + " to collect usage information, such as data about your feature usage," +
               "<br>resource usage and plugin configuration.</html>");

    // Note: we inline the constants corresponding to the following keys since the corresponding change in IJ
    // may not be in upstream as yet.
    labels.put("linkUrl", "http://www.google.com/policies/privacy/");
    labels.put("linkBeforeText", "This data is collected in accordance with " + companyName + "'s ");
    labels.put("linkText", "privacy policy");
    labels.put("linkAfterText", ".");

    return labels;
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public StatisticsResult send() {
    if (!AndroidStudioInitializer.isAndroidStudio()) {
      // If this is running as part of another product (not studio), then we return immediately
      // without sending anything via this service
      return new StatisticsResult(StatisticsResult.ResultCode.SEND, "OK");
    }

    StatisticsResult code = areStatisticsAuthorized();
    if (code.getCode() != StatisticsResult.ResultCode.SEND) {
      return code;
    }

    // Legacy ADT-compatible stats service.
    LegacySdkStatsService sdkstats = new LegacySdkStatsService();
    try {
      Method getStrictVersion = ApplicationInfo.class.getMethod("getStrictVersion");
      Object version = getStrictVersion.invoke(ApplicationInfo.getInstance());
      sdkstats.ping("studio", (String)version);
    }
    catch (Exception e) {
      // This code should only be run on AndroidStudio, if the method getStrictVersion
      // doesn't exist it means that we are incorrectly running this in Ij + android plugin.
      // Once the getStrictVersion function has been upstreamed, we can remove reflection here.
      throw new AssertionError(e);
    }

    return new StatisticsResult(StatisticsResult.ResultCode.SEND, "OK");
  }

  /**
   * Checks whether the statistics service has a service URL and is authorized
   * to send statistics.
   *
   * @return A {@link StatisticsResult} with a
   * {@link com.intellij.internal.statistic.connect.StatisticsResult.ResultCode#SEND} result code
   * on success, otherwise one of the error result codes.
   */
  static StatisticsResult areStatisticsAuthorized() {
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
    return new StatisticsResult(StatisticsResult.ResultCode.SEND, "OK");
  }
}
