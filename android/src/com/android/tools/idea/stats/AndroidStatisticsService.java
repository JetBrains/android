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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Android Statistics Service.
 *
 * Based on idea's RemotelyConfigurableStatisticsService.
 */
@SuppressWarnings("MethodMayBeStatic")
public class AndroidStatisticsService implements StatisticsService {

  private static final Logger LOG = Logger.getInstance("#" + AndroidStatisticsService.class.getName());

  private static final String SYS_PROP_OS_ARCH      = "os.arch";
  private static final String SYS_PROP_JAVA_VERSION = "java.version";
  private static final String SYS_PROP_OS_VERSION   = "os.version";
  private static final String SYS_PROP_OS_NAME      = "os.name";

  private static final String CONTENT_TYPE = "Content-Type";
  private static final String HTTP_POST = "POST";
  private static final int HTTP_STATUS_OK = 200;
  private static final String PROTOBUF_CONTENT = "application/x-protobuf";

  private final long myNow = System.currentTimeMillis();


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

    StatsProto.LogRequest data = getData(service.getDisabledGroups());

    String error = null;
    try {
      String error2 = sendData(data);
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

  private StatsProto.LogRequest getData(@NotNull Set<String> disabledGroups) {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();

    Map<String, KeyString[]> usages = new LinkedHashMap<String, KeyString[]>();
    for (Project project : openProjects) {
      final Map<String, KeyString[]> allUsages = getAllUsages(project, disabledGroups);
      usages.putAll(allUsages);
    }

    String uuid = UpdateChecker.getInstallationUID(PropertiesComponent.getInstance());
    String appVersion = ApplicationInfo.getInstance().getFullVersion();
    return createRequest(uuid, appVersion, usages);
  }

  @NotNull
  public Map<String, KeyString[]> getAllUsages(@Nullable Project project, @NotNull Set<String> disabledGroups) {
    Map<String, KeyString[]> allUsages = new LinkedHashMap<String, KeyString[]>();

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

  /** Sends data. Returns an error if something occurred. */
  public String sendData(StatsProto.LogRequest request) throws IOException {

    if (request == null) {
      return "[SendStats] Invalid arguments";
    }

    String url = "http://play.googleapis.com/log";
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

  public StatsProto.LogRequest createRequest(String uuid, String appVersion, Map<String, KeyString[]> usages) {
    StatsProto.LogRequest.Builder request = StatsProto.LogRequest.newBuilder();

    request.setLogSource(StatsProto.LogRequest.LogSource.ANDROID_STUDIO);
    request.setRequestTimeMs(myNow);
    request.setClientInfo(createClientInfo(uuid, appVersion));

    for (Map.Entry<String, KeyString[]> entry : usages.entrySet()) {
      request.addLogEvent(createEvent(entry.getKey(), entry.getValue()));
    }

    request.addLogEvent(createEvent("jvm", new KeyString[] {
      new KeyString("jvm-info", getJvmInfo()),
      new KeyString("jvm-vers", getJvmVersion()),
      new KeyString("jvm-arch", getJvmArch())
    } ));

    return request.build();
  }

  private StatsProto.LogEvent createEvent(String groupId, KeyString[] values) {
    StatsProto.LogEvent.Builder evtBuilder = StatsProto.LogEvent.newBuilder();
    evtBuilder.setEventTimeMs(myNow);
    evtBuilder.setTag(groupId);

    for (KeyString value : values) {
      StatsProto.LogEventKeyValues.Builder kvBuilder = StatsProto.LogEventKeyValues.newBuilder();
      kvBuilder.setKey(value.getKey());
      kvBuilder.setValue(value.getValue());
      evtBuilder.addValue(kvBuilder);
    }

    return evtBuilder.build();
  }

  private  StatsProto.ClientInfo createClientInfo(String uuid, String appVersion) {
    StatsProto.DesktopClientInfo.Builder desktop = StatsProto.DesktopClientInfo.newBuilder();

    desktop.setClientId(uuid);
    OsInfo info = getOsName();
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

  private static class OsInfo {
    private String myOsName;
    private String myOsVersion;

    public OsInfo setOsName(String osName) {
      myOsName = osName;
      return this;
    }

    public OsInfo setOsVersion(String osVersion) {
      myOsVersion = osVersion;
      return this;
    }

    public String getOsName() {
      return myOsName;
    }

    public String getOsVersion() {
      return myOsVersion;
    }

    public String getOsFull() {
      String os = myOsName;
      if (myOsVersion != null) {
        os += "-" + myOsVersion;
      }
      return os;
    }
  }

  /**
   * Detects and reports the host OS: "linux", "win" or "mac".
   * For Windows and Mac also append the version, so for example
   * Win XP will return win-5.1.
   * <p/>
   * Extracted from sdkstats/src/main/java/com/android/sdkstats/SdkStatsService.java
   */
  private  OsInfo getOsName() {                                    // made protected for testing
    String os = getSystemProperty(SYS_PROP_OS_NAME);

    OsInfo info = new OsInfo();

    if (os == null || os.length() == 0) {
      return info.setOsName("unknown");
    }

    String os2 = os.toLowerCase(Locale.US);
    String osVers = null;

    if (os2.startsWith("mac")) {
      os = "mac";
      osVers = getOsVersion();

    } else if (os2.startsWith("win")) {
      os = "win";
      osVers = getOsVersion();

    } else if (os2.startsWith("linux")) {
      os = "linux";

    } else if (os.length() > 32) {
      // Unknown -- send it verbatim so we can see it
      // but protect against arbitrarily long values
      os = os.substring(0, 32);
    }

    info.setOsName(os);
    info.setOsVersion(osVers);

    return info;
  }

  /**
   * Detects and returns the OS architecture: x86, x86_64, ppc.
   * This may differ or be equal to the JVM architecture in the sense that
   * a 64-bit OS can run a 32-bit JVM.
   * <p/>
   * Extracted from sdkstats/src/main/java/com/android/sdkstats/SdkStatsService.java
   */
  private String getOsArch() {
    String arch = getJvmArch();

    if ("x86_64".equals(arch)) {
      // This is a simple case: the JVM runs in 64-bit so the
      // OS must be a 64-bit one.
      return arch;

    } else if ("x86".equals(arch)) {
      // This is the misleading case: the JVM is 32-bit but the OS
      // might be either 32 or 64. We can't tell just from this
      // property.
      // Macs are always on 64-bit, so we just need to figure it
      // out for Windows and Linux.

      String os = getOsName().getOsName();
      if (os.startsWith("win")) {
        // When WOW64 emulates a 32-bit environment under a 64-bit OS,
        // it sets PROCESSOR_ARCHITEW6432 to AMD64 or IA64 accordingly.
        // Ref: http://msdn.microsoft.com/en-us/library/aa384274(v=vs.85).aspx

        String w6432 = getSystemEnv("PROCESSOR_ARCHITEW6432");
        if (w6432 != null && w6432.contains("64")) {
          return "x86_64";
        }
      } else if (os.startsWith("linux")) {
        // Let's try the obvious. This works in Ubuntu and Debian
        String s = getSystemEnv("HOSTTYPE");

        s = sanitizeOsArch(s);
        if (s.contains("86")) {
          arch = s;
        }
      }
    }

    return arch;
  }

  /**
   * Returns the version of the OS version if it is defined as X.Y, or null otherwise.
   * <p/>
   * Example of returned versions can be found at http://lopica.sourceforge.net/os.html
   * <p/>
   * This method removes any exiting micro versions.
   * Returns null if the version doesn't match X.Y.Z.
   * <p/>
   * Extracted from sdkstats/src/main/java/com/android/sdkstats/SdkStatsService.java
   */
  private String getOsVersion() {
    Pattern p = Pattern.compile("(\\d+)\\.(\\d+).*");
    String osVers = getSystemProperty(SYS_PROP_OS_VERSION);
    if (osVers != null && osVers.length() > 0) {
      Matcher m = p.matcher(osVers);
      if (m.matches()) {
        return m.group(1) + '.' + m.group(2);
      }
    }
    return null;
  }

  /**
   * Detects and returns the JVM info: version + architecture.
   * Examples: 1.4-ppc, 1.6-x86, 1.7-x86_64
   * <p/>
   * Extracted from sdkstats/src/main/java/com/android/sdkstats/SdkStatsService.java
   */
  private String getJvmInfo() {
    return getJvmVersion() + '-' + getJvmArch();
  }

  /**
   * Returns the major.minor Java version.
   * <p/>
   * The "java.version" property returns something like "1.6.0_20"
   * of which we want to return "1.6".
   * <p/>
   * Extracted from sdkstats/src/main/java/com/android/sdkstats/SdkStatsService.java
   */
  private String getJvmVersion() {
    String version = getSystemProperty(SYS_PROP_JAVA_VERSION);

    if (version == null || version.length() == 0) {
      return "unknown";
    }

    Pattern p = Pattern.compile("(\\d+)\\.(\\d+).*");
    Matcher m = p.matcher(version);
    if (m.matches()) {
      return m.group(1) + '.' + m.group(2);
    }

    // Unknown version. Send it as-is within a reasonable size limit.
    if (version.length() > 8) {
      version = version.substring(0, 8);
    }
    return version;
  }

  /**
   * Detects and returns the JVM architecture.
   * <p/>
   * The HotSpot JVM has a private property for this, "sun.arch.data.model",
   * which returns either "32" or "64". However it's not in any kind of spec.
   * <p/>
   * What we want is to know whether the JVM is running in 32-bit or 64-bit and
   * the best indicator is to use the "os.arch" property.
   * - On a 32-bit system, only a 32-bit JVM can run so it will be x86 or ppc.<br/>
   * - On a 64-bit system, a 32-bit JVM will also return x86 since the OS needs
   *   to masquerade as a 32-bit OS for backward compatibility.<br/>
   * - On a 64-bit system, a 64-bit JVM will properly return x86_64.
   * <pre>
   * JVM:       Java 32-bit   Java 64-bit
   * Windows:   x86           x86_64
   * Linux:     x86           x86_64
   * Mac        untested      x86_64
   * </pre>
   * <p/>
   * Extracted from sdkstats/src/main/java/com/android/sdkstats/SdkStatsService.java
   */
  private String getJvmArch() {
    String arch = getSystemProperty(SYS_PROP_OS_ARCH);
    return sanitizeOsArch(arch);
  }

  private String sanitizeOsArch(String arch) {
    if (arch == null || arch.length() == 0) {
      return "unknown";
    }

    if (arch.equalsIgnoreCase("x86_64") ||
        arch.equalsIgnoreCase("ia64") ||
        arch.equalsIgnoreCase("amd64")) {
      return "x86_64";
    }

    if (arch.length() >= 4 && arch.charAt(0) == 'i' && arch.indexOf("86") == 2) {
      // Any variation of iX86 counts as x86 (i386, i486, i686).
      return "x86";
    }

    if (arch.equalsIgnoreCase("PowerPC")) {
      return "ppc";
    }

    // Unknown arch. Send it as-is but protect against arbitrarily long values.
    if (arch.length() > 32) {
      arch = arch.substring(0, 32);
    }
    return arch;
  }

  /**
   * Helper to call {@link System#getProperty(String)}.
   * @see System#getProperty(String)
   */
  private String getSystemProperty(String name) {
    return System.getProperty(name);
  }

  /**
   * Helper to call {@link System#getenv(String)}.
   * @see System#getenv(String)
   */
  private String getSystemEnv(String name) {
    return System.getenv(name);
  }
}
