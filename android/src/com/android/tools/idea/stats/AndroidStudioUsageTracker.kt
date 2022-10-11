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
package com.android.tools.idea.stats

import com.android.ddmlib.IDevice
import com.android.ide.common.util.isMdnsAutoConnectTls
import com.android.ide.common.util.isMdnsAutoConnectUnencrypted
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.AnalyticsSettings.optedIn
import com.android.tools.analytics.CommonMetricsData
import com.android.tools.analytics.HostData
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.diagnostics.report.DefaultMetricsLogFileProvider
import com.android.tools.idea.serverflags.FOLLOWUP_SURVEY
import com.android.tools.idea.serverflags.SATISFACTION_SURVEY
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.Survey
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Charsets
import com.google.common.base.Strings
import com.google.common.hash.Hashing
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DisplayDetails
import com.google.wireless.android.sdk.stats.IdePlugin
import com.google.wireless.android.sdk.stats.IdePluginInfo
import com.google.wireless.android.sdk.stats.MachineDetails
import com.google.wireless.android.sdk.stats.ProductDetails
import com.google.wireless.android.sdk.stats.ProductDetails.SoftwareLifeCycleChannel
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.actionSystem.LatencyListener
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.updateSettings.impl.ChannelStatus
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.util.Disposer
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.jetbrains.android.AndroidPluginDisposable
import java.io.File
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Tracks Android Studio specific metrics
 */
object AndroidStudioUsageTracker {
  private const val IDLE_TIME_BEFORE_SHOWING_DIALOG = 3 * 60 * 1000
  const val STUDIO_EXPERIMENTS_OVERRIDE = "studio.experiments.override"

  private const val DAYS_IN_LEAP_YEAR = 366
  private const val DAYS_IN_NON_LEAP_YEAR = 365
  private const val DAYS_TO_WAIT_FOR_REQUESTING_SENTIMENT_AGAIN = 7

  // Broadcast channel for Android Studio events being logged
  val channel = BroadcastChannel<AndroidStudioEvent.Builder>(BUFFERED)

  @JvmStatic
  val productDetails: ProductDetails
    get() {
      val application = ApplicationInfo.getInstance()
      val productKind =
        if (IdeInfo.isGameTool()) {
          ProductDetails.ProductKind.GAME_TOOLS
        }
        else {
          ProductDetails.ProductKind.STUDIO
        }
      return ProductDetails.newBuilder().apply {
        product = productKind
        build = application.build.asString()
        version = application.strictVersion
        osArchitecture = CommonMetricsData.osArchitecture
        channel = lifecycleChannelFromUpdateSettings()
        theme = currentIdeTheme()
        serverFlagsChangelist = ServerFlagService.instance.configurationVersion
        addAllExperimentId(buildActiveExperimentList().union(ServerFlagService.instance.names))
      }.build()
    }

  /** Gets list of active experiments. */
  @JvmStatic
  fun buildActiveExperimentList(): Collection<String> {
    val experimentOverrides = System.getProperty(STUDIO_EXPERIMENTS_OVERRIDE)
    if (experimentOverrides.isNullOrEmpty()) {
      return listOf()
    }
    return experimentOverrides.split(',')
  }

  /** Gets information about all the displays connected to this machine.  */
  private val displayDetails: Iterable<DisplayDetails>
    get() {
      val displays = ArrayList<DisplayDetails>()

      val graphics = HostData.graphicsEnvironment!!
      if (!graphics.isHeadlessInstance) {
        for (device in graphics.screenDevices) {
          val defaultConfiguration = device.defaultConfiguration
          val bounds = defaultConfiguration.bounds
          displays.add(
            DisplayDetails.newBuilder()
              .setHeight(bounds.height.toLong())
              .setWidth(bounds.width.toLong())
              .setSystemScale(JBUIScale.sysScale(defaultConfiguration))
              .build())
        }
      }
      return displays
    }

  /**
   * Gets details about the machine this code is running on.
   *
   * @param homePath path to use to track total disk space.
   */
  @JvmStatic
  fun getMachineDetails(homePath: File): MachineDetails {
    val osBean = HostData.osBean!!

    return MachineDetails.newBuilder()
      .setAvailableProcessors(osBean.availableProcessors)
      .setTotalRam(osBean.totalPhysicalMemorySize)
      .setTotalDisk(homePath.totalSpace)
      .addAllDisplay(displayDetails)
      .build()
  }

  @JvmStatic
  fun setup(scheduler: ScheduledExecutorService) {
    scheduler.submit { runStartupReports() }
    // Send initial report immediately, daily from then on.
    scheduler.scheduleWithFixedDelay({ runDailyReports() }, 0, 1, TimeUnit.DAYS)
    // Send initial report immediately, hourly from then on.
    scheduler.scheduleWithFixedDelay({ runHourlyReports() }, 0, 1, TimeUnit.HOURS)
    subscribeToEvents()
    setupMetricsListener()

    // Studio ping is called immediately without scheduler to make sure
    // ping is not delayed based on scheduler logic, then it is scheduled
    // daily moving forward.
    studioPing()
    scheduler.scheduleWithFixedDelay({ studioPing() }, 1, 1, TimeUnit.DAYS)

  }

  private fun subscribeToEvents() {
    val app = ApplicationManager.getApplication()
    val connection = app.messageBus.connect()
    connection.subscribe(LatencyListener.TOPIC, TypingLatencyTracker)
    connection.subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
      override fun appWillBeClosed(isRestart: Boolean) {
        runShutdownReports()
      }
    })
  }

  private fun runStartupReports() {
    reportEnabledPlugins()
  }

  private fun runShutdownReports() {
    TypingLatencyTracker.reportTypingLatency()
    CompletionStats.reportCompletionStats()
    ManifestMergerStatsTracker.reportMergerStats()
  }

  private fun reportEnabledPlugins() {
    val plugins = PluginManagerCore.getLoadedPlugins()
    val pluginInfoProto = IdePluginInfo.newBuilder()

    for (plugin in plugins) {
      if (!plugin.isEnabled) continue
      val id = plugin.pluginId?.idString ?: continue

      val pluginProto = IdePlugin.newBuilder()
      pluginProto.id = id.take(256)
      plugin.version?.take(256)?.let { pluginProto.version = it }
      pluginProto.bundled = plugin.isBundled

      pluginInfoProto.addPlugins(pluginProto)
    }

    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(EventKind.IDE_PLUGIN_INFO)
        .setIdePluginInfo(pluginInfoProto))
  }

  private fun runDailyReports() {
    processUserSentiment()
  }

  private fun studioPing() {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setCategory(AndroidStudioEvent.EventCategory.PING)
        .setKind(EventKind.STUDIO_PING)
        .setProductDetails(productDetails)
        .setMachineDetails(getMachineDetails(File(PathManager.getHomePath())))
        .setJvmDetails(CommonMetricsData.jvmDetails))
  }

  private fun processUserSentiment() {
    if (!shouldRequestUserSentiment()) {
      return
    }
    requestUserSentiment()
  }

  @VisibleForTesting
  fun shouldRequestUserSentiment(): Boolean {
    if (!optedIn) {
      return false
    }

    val lastSentimentAnswerDate = AnalyticsSettings.lastSentimentAnswerDate
    val lastSentimentQuestionDate = AnalyticsSettings.lastSentimentQuestionDate

    val now = AnalyticsSettings.dateProvider.now()

    if (isWithinPastYear(now, lastSentimentAnswerDate)) {
      return false
    }

    // If we should ask the question based on dates, and asked but not answered then we should always prompt, even if this is
    // not the magic date for that user.
    if (lastSentimentQuestionDate != null) {
      val startOfWaitForRequest =
        daysFromNow(now, -DAYS_TO_WAIT_FOR_REQUESTING_SENTIMENT_AGAIN)
      return !lastSentimentQuestionDate.after(startOfWaitForRequest)
    }

    val startOfYear = GregorianCalendar(now.year + 1900, 0, 1)
    startOfYear.timeZone = TimeZone.getTimeZone(ZoneOffset.UTC)

    // Otherwise, only request on the magic date for the user, to spread user sentiment data throughout the year.
    val daysSinceJanFirst = ChronoUnit.DAYS.between(startOfYear.toInstant(), now.toInstant())
    val offset =
      abs(
        Hashing.farmHashFingerprint64()
          .hashString(AnalyticsSettings.userId, Charsets.UTF_8)
          .asLong()
      ) % daysInYear(now)
    return daysSinceJanFirst == offset
  }

  /**
   * returning UNKNOWN_SATISFACTION_LEVEL means that the user hit the Cancel button in the dialog.
   */
  fun requestUserSentiment() {
    val eventQueue = IdeEventQueue.getInstance()

    lateinit var runner: Runnable
    runner = Runnable {
      // Ensure we're invoked only once.
      eventQueue.removeIdleListener(runner)

      val now = AnalyticsSettings.dateProvider.now()
      val survey = ServerFlagService.instance.getProtoOrNull(SATISFACTION_SURVEY, DEFAULT_SATISFACTION_SURVEY)
      val followupSurvey = ServerFlagService.instance.getProtoOrNull(FOLLOWUP_SURVEY, DEFAULT_SATISFACTION_SURVEY)
      val hasFollowup = followupSurvey != null

      val dialog = survey?.let { createDialog(it, hasFollowup = hasFollowup) }
                   ?: SingleChoiceDialog(DEFAULT_SATISFACTION_SURVEY, LegacyChoiceLogger, hasFollowup)

      followupSurvey?.let {
        scheduleFollowup(dialog, it)
      }

      dialog.show()

      AnalyticsSettings.lastSentimentQuestionDate = now
      AnalyticsSettings.lastSentimentAnswerDate = now
      AnalyticsSettings.saveSettings()
    }

    eventQueue.addIdleListener(runner, IDLE_TIME_BEFORE_SHOWING_DIALOG)
  }

  private fun scheduleFollowup(dialog: DialogWrapper, survey: Survey) {
    Disposer.register(dialog.disposable, {
      val eventQueue = IdeEventQueue.getInstance()
      lateinit var runner: Runnable
      runner = Runnable {
        eventQueue.removeIdleListener(runner)

        if (dialog.isOK) {
          createDialog(survey, hasFollowup = false).show()
        }
      }

      eventQueue.addIdleListener(runner, 500)
    })
  }

  private fun runHourlyReports() {
    UsageTracker.log(AndroidStudioEvent.newBuilder()
                       .setCategory(AndroidStudioEvent.EventCategory.SYSTEM)
                       .setKind(EventKind.STUDIO_PROCESS_STATS)
                       .setJavaProcessStats(CommonMetricsData.javaProcessStats))

    TypingLatencyTracker.reportTypingLatency()
    CompletionStats.reportCompletionStats()
    ManifestMergerStatsTracker.reportMergerStats()
  }

  /**
   * Creates a [DeviceInfo] from a [IDevice] instance.
   */
  @JvmStatic
  fun deviceToDeviceInfo(device: IDevice): DeviceInfo {
    return DeviceInfo.newBuilder()
      .setAnonymizedSerialNumber(AnonymizerUtil.anonymizeUtf8(device.serialNumber))
      .setBuildTags(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_TAGS)))
      .setBuildType(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_TYPE)))
      .setBuildVersionRelease(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_VERSION)))
      .setBuildApiLevelFull(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_API_LEVEL)))
      .setCpuAbi(CommonMetricsData.applicationBinaryInterfaceFromString(device.getProperty(IDevice.PROP_DEVICE_CPU_ABI)))
      .setManufacturer(Strings.nullToEmpty(device.getProperty(IDevice.PROP_DEVICE_MANUFACTURER)))
      .setDeviceType(if (device.isEmulator) DeviceInfo.DeviceType.LOCAL_EMULATOR else DeviceInfo.DeviceType.LOCAL_PHYSICAL)
      .setMdnsConnectionType(when {
                               device.isMdnsAutoConnectUnencrypted -> DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_UNENCRYPTED
                               device.isMdnsAutoConnectTls -> DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_TLS
                               else -> DeviceInfo.MdnsConnectionType.MDNS_NONE
                             })
      .addAllCharacteristics(device.hardwareCharacteristics)
      .setModel(Strings.nullToEmpty(device.getProperty(IDevice.PROP_DEVICE_MODEL))).build()
  }

  /**
   * Retrieves the corresponding [ProductDetails.IdeTheme] based on current IDE's settings
   */
  private fun currentIdeTheme(): ProductDetails.IdeTheme {
    return when {
      UIUtil.isUnderDarcula() ->
        // IJ's custom theme are based off of Darcula. We look at the LafManager to determine whether the actual selected theme is
        // darcular, high contrast, or some other custom theme
        when (LafManager.getInstance().currentLookAndFeel?.name?.lowercase(Locale.US)) {
          "darcula" -> ProductDetails.IdeTheme.DARCULA
          "high contrast" -> ProductDetails.IdeTheme.HIGH_CONTRAST
          else -> ProductDetails.IdeTheme.CUSTOM
        }
      UIUtil.isUnderIntelliJLaF() ->
        // When the theme is IntelliJ, there are mac and window specific registries that govern whether the theme refers to the native
        // themes, or the newer, platform-agnostic Light theme. UIUtil.isUnderWin10LookAndFeel() and UIUtil.isUnderDefaultMacTheme() take
        // care of these checks for us.
        when {
          UIUtil.isUnderWin10LookAndFeel() -> ProductDetails.IdeTheme.LIGHT_WIN_NATIVE
          UIUtil.isUnderDefaultMacTheme() -> ProductDetails.IdeTheme.LIGHT_MAC_NATIVE
          else -> ProductDetails.IdeTheme.LIGHT
        }
      else -> ProductDetails.IdeTheme.UNKNOWN_THEME
    }
  }

  /**
   * Creates a [DeviceInfo] from a [IDevice] instance
   * containing api level only.
   */
  @JvmStatic
  fun deviceToDeviceInfoApiLevelOnly(device: IDevice): DeviceInfo {
    return DeviceInfo.newBuilder()
      .setBuildApiLevelFull(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_API_LEVEL)))
      .build()
  }

  /**
   * Reads the channel selected by the user from UpdateSettings and converts it into a [SoftwareLifeCycleChannel] value.
   */
  private fun lifecycleChannelFromUpdateSettings(): SoftwareLifeCycleChannel {
    return when (UpdateSettings.getInstance().selectedChannelStatus) {
      ChannelStatus.EAP -> SoftwareLifeCycleChannel.CANARY
      ChannelStatus.MILESTONE -> SoftwareLifeCycleChannel.DEV
      ChannelStatus.BETA -> SoftwareLifeCycleChannel.BETA
      ChannelStatus.RELEASE -> SoftwareLifeCycleChannel.STABLE
      else -> SoftwareLifeCycleChannel.UNKNOWN_LIFE_CYCLE_CHANNEL
    }
  }

  private fun setupMetricsListener() {
    val scope = AndroidCoroutineScope(AndroidPluginDisposable.getApplicationInstance())
    UsageTracker.listener = { event ->
      scope.launch {
        channel.send(event)
      }
    }

    scope.launch {
      channel.openSubscription().consumeEach {
        FeatureSurveys.processEvent(it)
        DefaultMetricsLogFileProvider.processEvent(it)
      }
    }
  }

  private fun isWithinPastYear(now: Date, date: Date?): Boolean {
    return isBeforeDayCount(now, date, -daysInYear(now))
  }

  private fun isBeforeDayCount(now: Date, date: Date?, days: Int): Boolean {
    date ?: return false
    val newDate = daysFromNow(now, days)
    return date.after(newDate)
  }

  fun daysFromNow(now: Date, days: Int): Date {
    val calendar = Calendar.getInstance()
    calendar.time = now
    calendar.add(Calendar.DATE, days)
    return calendar.time
  }

  private fun daysInYear(now: Date): Int {
    return if (GregorianCalendar().isLeapYear(now.year + 1900)) {
      DAYS_IN_LEAP_YEAR
    }
    else {
      DAYS_IN_NON_LEAP_YEAR
    }
  }
}
