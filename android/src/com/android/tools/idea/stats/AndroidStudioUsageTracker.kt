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
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.AnalyticsSettings.optedIn
import com.android.tools.analytics.CommonMetricsData
import com.android.tools.analytics.HostData
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.actions.FeatureSurveyNotificationAction
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.diagnostics.report.DefaultMetricsLogFileProvider
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.serverflags.FOLLOWUP_SURVEY
import com.android.tools.idea.serverflags.SATISFACTION_SURVEY
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.Survey
import com.android.tools.idea.stats.ConsentDialog.Companion.showConsentDialogIfNeeded
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
import com.google.wireless.android.sdk.stats.IntelliJNewUIState
import com.google.wireless.android.sdk.stats.MachineDetails
import com.google.wireless.android.sdk.stats.ProductDetails
import com.google.wireless.android.sdk.stats.ProductDetails.SoftwareLifeCycleChannel
import com.google.wireless.android.sdk.stats.SafeModeStatsEvent
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.LafManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.updateSettings.impl.ChannelStatus
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.ui.NewUI
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
  private const val DAYS_TO_WAIT_FOR_REQUESTING_SENTIMENT_AGAIN = 7
  private const val IDX_ENVIRONMENT_VARIABLE = "GOOGLE_CLOUD_WORKSTATIONS"

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
        runningInsideIdx = (System.getenv(IDX_ENVIRONMENT_VARIABLE) == "true")
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
    TypingLatencyTracker.ensureSubscribed()
    setupMetricsListener()

    // Studio ping is called in the appStarted event below, then it is scheduled
    // daily moving forward.
    scheduler.scheduleWithFixedDelay({ studioPing() }, 1, 1, TimeUnit.DAYS)

    updateNewUISettings()
  }

  private fun runStartupReports() {
    reportEnabledPlugins()
    reportSafeModeStats()
  }

  private fun runShutdownReports() {
    CompletionStats.reportCompletionStats()
    ManifestMergerStatsTracker.reportMergerStats()
  }

  private fun reportEnabledPlugins() {
    val plugins = PluginManagerCore.loadedPlugins
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

  private fun reportSafeModeStats() {
    System.getProperty("studio.safe.mode") ?: return
    val safeModeStatsProto = SafeModeStatsEvent.newBuilder()

    if (SystemInfo.isWindows) {
      safeModeStatsProto.setOs(SafeModeStatsEvent.OS.WINDOWS)
    }
    else if (SystemInfo.isMac) {
      safeModeStatsProto.setOs(SafeModeStatsEvent.OS.MAC)
    }
    else if (SystemInfo.isUnix) {
      safeModeStatsProto.setOs(SafeModeStatsEvent.OS.LINUX)
    }
    else {
      safeModeStatsProto.setOs(SafeModeStatsEvent.OS.UNKNOWN_OS)
    }

    safeModeStatsProto.setEntryPoint(SafeModeStatsEvent.EntryPoint.SCRIPT)
    safeModeStatsProto.setTrigger(if (System.getProperty("studio.safe.mode") == null) SafeModeStatsEvent.Trigger.UNKNOWN_TRIGGER
                                  else SafeModeStatsEvent.Trigger.STARTUP_FAILED)
    safeModeStatsProto.setStartUpResult(SafeModeStatsEvent.StartUpResult.SAFE_MODE_SUCCESS)
    safeModeStatsProto.setStudioVersion(ApplicationInfo.getInstance().strictVersion)
    safeModeStatsProto.setJdkModified(IdeSdks.getInstance().getRunningVersionOrDefault().toString())

    val plugins = PluginManagerCore.loadedPlugins
    for (plugin in plugins) {
      if (!plugin.isEnabled) continue
      val id = plugin.pluginId.idString

      if (id == "org.jetbrains.kotlin") {
        safeModeStatsProto.setKotlinModified(plugin.version)
        break
      }
    }

    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(EventKind.SAFE_MODE_STATS_EVENT)
        .setSafeModeStatsEvent(safeModeStatsProto))
  }

  private fun runDailyReports() {
    processUserSentiment()
    logNewUI()
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

  private fun logNewUI() {
    val enabled =
      try {
        NewUI.isEnabled()
      }
      catch (_: Throwable) {
        // Don't send the message if the new UI check fails
        return
      }
    UsageTracker.log(
      AndroidStudioEvent.newBuilder().apply {
        kind = EventKind.INTELLIJ_NEW_UI_STATE_EVENT
        intellijNewUiStateEvent = IntelliJNewUIState.newBuilder().apply {
          isEnabled = enabled
        }.build()
      })
  }

  // Persist the initial new UI state for this session, so the
  // next session can detect whether this session has disabled it
  private fun updateNewUISettings() {
    val enabled =
      try {
        NewUI.isEnabled()
      }
      catch (_: Throwable) {
        return
      }

    val instance = ExperimentalUISettings.getInstance()

    // If the user disabled the new UI during the previous
    // session, display a survey asking them why
    if (StudioFlags.EXPERIMENTAL_UI_SURVEY_ENABLED.get() && !enabled && instance.enabled) {
      showNewUISurvey();
    }

    instance.enabled = enabled
  }

  private fun showNewUISurvey() {
    val notificationGroup =
      NotificationGroupManager.getInstance().getNotificationGroup("Feature Survey") ?: return

    val notification = notificationGroup.createNotification(
      "New UI Survey",
      "We noticed that you recently disabled the New UI. Please tell us why so we can improve the experience.",
      NotificationType.INFORMATION
    )

    notification.addAction(FeatureSurveyNotificationAction(EXPERIMENTAL_UI_INTERACTION_SURVEY))

    ApplicationManager.getApplication().invokeLater { Notifications.Bus.notify(notification) }
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

    if (!exceedRefreshDeadline(now, lastSentimentAnswerDate)) {
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
      ) % AnalyticsSettings.popSentimentQuestionFrequency
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
    CompletionStats.reportCompletionStats()
    ManifestMergerStatsTracker.reportMergerStats()
  }


  /**
   * Retrieves the corresponding [ProductDetails.IdeTheme] based on current IDE's settings
   */
  private fun currentIdeTheme(): ProductDetails.IdeTheme {
    val theme = LafManager.getInstance().getCurrentUIThemeLookAndFeel()?.name?.lowercase(Locale.US)
                ?: return ProductDetails.IdeTheme.UNKNOWN_THEME
    val author = LafManager.getInstance().currentUIThemeLookAndFeel.author?.lowercase(Locale.US)
                 ?: return ProductDetails.IdeTheme.UNKNOWN_THEME
    return when {
      author == "jetbrains" ->
        when {
          theme == "darcula" -> ProductDetails.IdeTheme.DARCULA
          theme == "dark" -> ProductDetails.IdeTheme.DARK
          theme == "light" -> ProductDetails.IdeTheme.LIGHT
          theme == "light with light header" -> ProductDetails.IdeTheme.LIGHT_WITH_LIGHT_HEADER
          theme == "high contrast" -> ProductDetails.IdeTheme.HIGH_CONTRAST
          else -> ProductDetails.IdeTheme.UNKNOWN_THEME
        }
      else -> ProductDetails.IdeTheme.CUSTOM
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

  private fun exceedRefreshDeadline(now: Date, date: Date?): Boolean {
    return !isBeforeDayCount(now, date, -AnalyticsSettings.popSentimentQuestionFrequency)
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

  class UsageTrackerAppLifecycleListener : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
      showConsentDialogIfNeeded()
    }

    override fun appStarted() {
      studioPing()
    }

    override fun appWillBeClosed(isRestart: Boolean) {
      runShutdownReports()
    }
  }
}
