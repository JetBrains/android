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
import com.intellij.internal.statistic.persistence.ApplicationStatisticsPersistenceComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.*;

/**
 * Android Statistics Service.
 * Based on idea's RemotelyConfigurableStatisticsService.
 * Also sends a legacy ping using ADT's LegacySdkStatsService.
 */
@SuppressWarnings("MethodMayBeStatic")
public class AndroidStatisticsService implements StatisticsService {

  private static final Logger LOG = Logger.getInstance("#" + AndroidStatisticsService.class.getName());

  private static final String CONTENT_TYPE = "Content-Type";
  private static final String HTTP_POST = "POST";
  private static final int HTTP_STATUS_OK = 200;
  private static final String PROTOBUF_CONTENT = "application/x-protobuf";

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

  @SuppressWarnings("ConstantConditions")
  @Override
  public StatisticsResult send() {
    synchronized (ApplicationStatisticsPersistenceComponent.class) {

      LegacySdkStatsService sdkstats = sendLegacyPing();

      StatisticsResult result = sendUsageStats(sdkstats);

      result = sendBuildStats(sdkstats);

      return result;
    }
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

  /**
   * Sends one LogRequests with multiple build records.
   * Does nothing if there are no records pending.
   */
  private StatisticsResult sendBuildStats(LegacySdkStatsService sdkstats) {
    StatisticsResult code = areStatisticsAuthorized();
    if (code.getCode() != StatisticsResult.ResultCode.SEND) {
      return code;
    }

    StudioBuildStatsPersistenceComponent records = StudioBuildStatsPersistenceComponent.getInstance();
    if (records == null || !records.hasRecords()) {
      return new StatisticsResult(StatisticsResult.ResultCode.NOTHING_TO_SEND, "NOTHING_TO_SEND");
    }

    StatsProto.LogRequest data = getRecordData(sdkstats, records);

    String error = null;
    try {
      error = sendData(data);
    } catch (Exception e) {
      error = e.getClass().getSimpleName() + " " + (e.getMessage() != null ? e.getMessage() : e.toString());
    }

    if (error != null) {
      LOG.debug("[SendStats/AS-2] Error " + (error == null ? "None" : error));
    }
    if (error == null) {
      return new StatisticsResult(StatisticsResult.ResultCode.SEND, "OK");
    } else {
      return new StatisticsResult(StatisticsResult.ResultCode.SENT_WITH_ERRORS, error);
    }
  }

  /**
   * Send IJ-style "usage" stats using our format. The idea is to deactivate this eventually.
   */
  @Deprecated
  private StatisticsResult sendUsageStats(LegacySdkStatsService sdkstats) {
    StatisticsResult code = areStatisticsAuthorized();
    if (code.getCode() != StatisticsResult.ResultCode.SEND) {
      return code;
    }

    StatisticsConnectionService service = new StatisticsConnectionService();
    StatsProto.LogRequest data = getUsageData(sdkstats, service.getDisabledGroups());

    String error = null;
    try {
      error = sendData(data);
    } catch (Exception e) {
      error = e.getClass().getSimpleName() + " " + (e.getMessage() != null ? e.getMessage() : e.toString());
    }

    if (error != null) {
      LOG.debug("[SendStats/AS-1] Error " + (error == null ? "None" : error));
    }
    if (error == null) {
      return new StatisticsResult(StatisticsResult.ResultCode.SEND, "OK");
    } else {
      return new StatisticsResult(StatisticsResult.ResultCode.SENT_WITH_ERRORS, error);
    }
  }

  private LegacySdkStatsService sendLegacyPing() {
    // Legacy ADT-compatible stats service.
    LegacySdkStatsService sdkstats = new LegacySdkStatsService();
    sdkstats.ping("studio", ApplicationInfo.getInstance().getFullVersion());
    return sdkstats;
  }

  /**
   * Transforms one or more BuildRecords into as many LogRequest.LogEvents as needed, each with their
   * own timestamp. The wrapper LogRequest has a "now" timestamp.
   */
  private StatsProto.LogRequest getRecordData(@NotNull LegacySdkStatsService sdkstats,
                                              @NotNull StudioBuildStatsPersistenceComponent records) {
    StatsProto.LogRequest.Builder request = StatsProto.LogRequest.newBuilder();

    request.setLogSource(StatsProto.LogRequest.LogSource.ANDROID_STUDIO);
    request.setRequestTimeMs(System.currentTimeMillis());

    String uuid = UpdateChecker.getInstallationUID(PropertiesComponent.getInstance());
    String appVersion = ApplicationInfo.getInstance().getFullVersion();
    request.setClientInfo(createClientInfo(sdkstats, uuid, appVersion));

    while (records.hasRecords()) {
      BuildRecord record = records.getFirstRecord();
      if (record == null) {
        break;
      }
      StatsProto.LogEvent.Builder evtBuilder = StatsProto.LogEvent.newBuilder();
      evtBuilder.setEventTimeMs(record.getUtcTimestampMs());
      evtBuilder.setTag("build");

      for (KeyString value : record.getData()) {
        StatsProto.LogEventKeyValues.Builder kvBuilder = StatsProto.LogEventKeyValues.newBuilder();
        kvBuilder.setKey(value.getKey());
        kvBuilder.setValue(value.getValue());
        evtBuilder.addValue(kvBuilder);
      }

      request.addLogEvent(evtBuilder.build());
    }

    return request.build();
  }

  private StatsProto.LogRequest getUsageData(@NotNull LegacySdkStatsService sdkstats,
                                             @NotNull Set<String> disabledGroups) {
    Map<String, KeyString[]> usages = new LinkedHashMap<String, KeyString[]>();
    final Map<String, KeyString[]> allUsages = getAllUsages(disabledGroups);
    usages.putAll(allUsages);

    String uuid = UpdateChecker.getInstallationUID(PropertiesComponent.getInstance());
    String appVersion = ApplicationInfo.getInstance().getFullVersion();
    return createRequest(sdkstats, uuid, appVersion, usages);
  }

  @NotNull
  public Map<String, KeyString[]> getAllUsages(@NotNull Set<String> disabledGroups) {
    Map<String, KeyString[]> allUsages = new LinkedHashMap<String, KeyString[]>();

    for (UsagesCollector usagesCollector : Extensions.getExtensions(UsagesCollector.EP_NAME)) {
      final GroupDescriptor groupDescriptor = usagesCollector.getGroupId();
      final String groupId = groupDescriptor.getId();

      if (!disabledGroups.contains(groupId)) {
        try {
          final Set<UsageDescriptor> usages = usagesCollector.getUsages();
          final Set<Counter> counters = new TreeSet<Counter>();
          for (UsageDescriptor usage : usages) {
            Counter counter = new Counter(usage.getKey(), usage.getValue());
            counters.add(counter);
          }
          allUsages.put(groupId, counters.toArray(new Counter[counters.size()]));

        } catch (CollectUsagesException e) {
          LOG.info(e);
        }
      }
    }

    return allUsages;
  }

  /**
   * Sends data. Returns an error if something occurred.
   *
   * TODO: the server send a reply that tells us how long to wait before sending the next one.
   * Capture that and report it to the caller.
   */
  @Nullable
  public String sendData(@NotNull StatsProto.LogRequest request) throws IOException {
    if (request == null) {
      return "[SendStats] Invalid arguments";
    }

    String url = "https://play.google.com/log";
    byte[] data = request.toByteArray();

    HttpURLConnection connection = HttpConfigurable.getInstance().openHttpConnection(url);
    connection.setConnectTimeout(2000);
    connection.setReadTimeout(2000);
    connection.setDoOutput(true);
    connection.setRequestMethod(HTTP_POST);
    connection.setRequestProperty(CONTENT_TYPE, PROTOBUF_CONTENT);

    OutputStream os = connection.getOutputStream();
    try {
      os.write(data);
    } finally {
      os.close();
    }

    int code = connection.getResponseCode();

    if (code == HTTP_STATUS_OK) {
      return null; // no error
    }

    return "[SendStats] Error " + code;
  }

  public StatsProto.LogRequest createRequest(@NotNull LegacySdkStatsService sdkstats,
                                             @NotNull String uuid,
                                             @NotNull String appVersion,
                                             @NotNull Map<String, KeyString[]> usages) {
    StatsProto.LogRequest.Builder request = StatsProto.LogRequest.newBuilder();

    request.setLogSource(StatsProto.LogRequest.LogSource.ANDROID_STUDIO);
    request.setRequestTimeMs(System.currentTimeMillis());
    request.setClientInfo(createClientInfo(sdkstats, uuid, appVersion));

    for (Map.Entry<String, KeyString[]> entry : usages.entrySet()) {
      request.addLogEvent(createEvent(entry.getKey(), entry.getValue()));
    }

    request.addLogEvent(createEvent("jvm", new KeyString[] {
      new KeyString("jvm-info", sdkstats.getJvmInfo()),
      new KeyString("jvm-vers", sdkstats.getJvmVersion()),
      new KeyString("jvm-arch", sdkstats.getJvmArch())
    } ));

    return request.build();
  }

  private StatsProto.LogEvent createEvent(@NotNull String groupId,
                                          @NotNull KeyString[] values) {
    StatsProto.LogEvent.Builder evtBuilder = StatsProto.LogEvent.newBuilder();
    evtBuilder.setEventTimeMs(System.currentTimeMillis());
    evtBuilder.setTag(groupId);

    for (KeyString value : values) {
      StatsProto.LogEventKeyValues.Builder kvBuilder = StatsProto.LogEventKeyValues.newBuilder();
      kvBuilder.setKey(value.getKey());
      kvBuilder.setValue(value.getValue());
      evtBuilder.addValue(kvBuilder);
    }

    return evtBuilder.build();
  }

  private  StatsProto.ClientInfo createClientInfo(@NotNull LegacySdkStatsService sdkstats,
                                                  @NotNull String uuid,
                                                  @NotNull String appVersion) {
    StatsProto.DesktopClientInfo.Builder desktop = StatsProto.DesktopClientInfo.newBuilder();

    desktop.setClientId(uuid);
    OsInfo info = sdkstats.getOsName();
    desktop.setOs(info.getOsName());
    String os_vers = info.getOsVersion();
    if (os_vers != null) {
      desktop.setOsMajorVersion(os_vers);
    }
    desktop.setOsFullVersion(info.getOsFull());
    desktop.setApplicationBuild(appVersion);

    StatsProto.ClientInfo.Builder cinfo = StatsProto.ClientInfo.newBuilder();
    cinfo.setClientType(StatsProto.ClientInfo.ClientType.DESKTOP);
    cinfo.setDesktopClientInfo(desktop);
    return cinfo.build();
  }
}
