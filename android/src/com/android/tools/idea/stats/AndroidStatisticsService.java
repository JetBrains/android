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
import com.android.tools.idea.IdeInfo;
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

  @SuppressWarnings("ConstantConditions")
  @Override
  public StatisticsResult send() {
    if (!IdeInfo.getInstance().isAndroidStudio()) {
      // If this is running as part of another product (not studio), then we return immediately
      // without sending anything via this service
      return new StatisticsResult(StatisticsResult.ResultCode.SEND, "OK");
    }

    StatisticsResult code = areStatisticsAuthorized();
    if (code.getCode() != StatisticsResult.ResultCode.SEND) {
      return code;
    }


    return new StatisticsResult(StatisticsResult.ResultCode.SEND, "OK");
  }

  /**
   * Checks whether the statistics service has a service URL and is authorized to send statistics.
   *
   * @return A {@link StatisticsResult} with a {@link StatisticsResult.ResultCode#SEND} result code on success, otherwise one of the error
   * result codes.
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
