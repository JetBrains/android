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

import com.android.tools.idea.IdeInfo;
import com.intellij.internal.statistic.connect.StatisticsConnectionService;
import com.intellij.internal.statistic.connect.StatisticsResult;
import com.intellij.internal.statistic.connect.StatisticsService;

/**
 * Android Statistics Service.
 * Based on idea's RemotelyConfigurableStatisticsService.
 * Also sends a legacy ping using ADT's LegacySdkStatsService.
 */
@SuppressWarnings("MethodMayBeStatic")
public class AndroidStatisticsService implements StatisticsService {

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
