/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.startup.AndroidStudioInitializer;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.StatisticsUploadAssistant;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Android Studio Usage Tracker.
 */
public abstract class UsageTracker {
  /**
   * A session id that is unique for every instance of Android Studio.
   * Note: if you need an id that is unique per installation, use {@link UpdateChecker#getInstallationUID(PropertiesComponent)}.
   */
  public static final String SESSION_ID = UUID.randomUUID().toString();

  /**
   * Maximum length of the URL constructed when uploading data to tools.google.com. This is simply the max allowed HTTP URL length,
   * which depending on the source seems to be from 2k to 4k. We use a conservative value here.
   */
  public static final int MAX_URL_LENGTH = 2000;

  /**
   * GA only allows sending a single <category,action,value> tuple per event
   * However, we'd like to track different components of the avd such as its version, arch, etc
   * So this category will consist of info events, but note that the total event count is somewhat meaningless
   * Note: Custom dimensions could possibly alleviate this issue, and we should consider switching to
   * that when we have more info on the sorts of custom dimensions we'd need.
   */
  public static final String CATEGORY_AVDINFO = "avdInfo";
  public static final String ACTION_AVDINFO_ABI = "abi";
  public static final String ACTION_AVDINFO_TARGET_VERSION = "version";

  // Similar to CATEGORY_AVDINFO, this tracks info about the device during deployment
  public static final String CATEGORY_DEVICE_INFO = "deviceInfo";
  public static final String DEVICE_INFO_BUILD_TAGS = IDevice.PROP_BUILD_TAGS; // "unsigned,debug" or "dev-keys"
  public static final String DEVICE_INFO_BUILD_TYPE = IDevice.PROP_BUILD_TYPE; // "user" or "eng"
  public static final String DEVICE_INFO_BUILD_VERSION_RELEASE = IDevice.PROP_BUILD_VERSION; // "4.4.4"
  public static final String DEVICE_INFO_BUILD_API_LEVEL = IDevice.PROP_BUILD_API_LEVEL; // "22"
  public static final String DEVICE_INFO_MANUFACTURER = IDevice.PROP_DEVICE_MANUFACTURER;
  public static final String DEVICE_INFO_MODEL = IDevice.PROP_DEVICE_MODEL;
  public static final String DEVICE_INFO_MANUFACTURER_MODEL = "deviceModel";
  public static final String DEVICE_INFO_SERIAL_HASH = "ro.serialno.hashed";
  public static final String DEVICE_INFO_CPU_ABI = IDevice.PROP_DEVICE_CPU_ABI;

  public static final String CATEGORY_DEPLOYMENT = "deployment";
  public static final String ACTION_DEPLOYMENT_APK = "apkDeployed";
  public static final String ACTION_DEPLOYMENT_EMULATOR = "emulatorLaunch";

  public static final String CATEGORY_INSTANTRUN = "instantrun";
  public static final String ACTION_INSTANTRUN_FULLBUILD = "buildCause";

  public static final String CATEGORY_DEVELOPER_SERVICES = "devServices";
  public static final String ACTION_DEVELOPER_SERVICES_INSTALLED = "installed";
  public static final String ACTION_DEVELOPER_SERVICES_REMOVED = "removed";

  public static final String CATEGORY_GRADLE = "gradle";
  public static final String ACTION_GRADLE_SYNC_STARTED = "syncStarted";
  public static final String ACTION_GRADLE_SYNC_ENDED = "syncEnded";
  public static final String ACTION_GRADLE_SYNC_SKIPPED = "syncSkipped";
  public static final String ACTION_GRADLE_SYNC_FAILED = "syncFailed";
  public static final String ACTION_GRADLE_CPP_SYNC_COMPLETED = "cppSyncCompleted";
  public static final String ACTION_GRADLE_VERSION = "gradleVersion";

  public static final String CATEGORY_GRADLE_SYNC_FAILURE = "gradleSyncFailure";
  public static final String ACTION_GRADLE_SYNC_FAILURE_UNKNOWN = "syncFailedCauseUnknown";
  public static final String ACTION_GRADLE_SYNC_CONNECTION_DENIED = "syncFailedConnectionDenied";
  public static final String ACTION_GRADLE_SYNC_CLASS_NOT_FOUND = "syncFailedClassNotFound";
  public static final String ACTION_GRADLE_SYNC_DSL_METHOD_NOT_FOUND = "syncFailedDslMethodNotFound";
  public static final String ACTION_GRADLE_SYNC_FAILED_TO_PARSE_SDK = "syncFailedCannotParseSdk";
  public static final String ACTION_GRADLE_SYNC_METHOD_NOT_FOUND = "syncFailedMethodNotFound";
  public static final String ACTION_GRADLE_SYNC_MISSING_ANDROID_PLATFORM = "syncFailedMissingAndroidPlatform";
  public static final String ACTION_GRADLE_SYNC_MISSING_ANDROID_SUPPORT_REPO = "syncFailedMissingAndroidSupportRepo";
  public static final String ACTION_GRADLE_SYNC_MISSING_BUILD_TOOLS = "syncFailedMissingBuildTools";
  public static final String ACTION_GRADLE_SYNC_OUT_OF_MEMORY = "syncFailedOutOfMemory";
  public static final String ACTION_GRADLE_SYNC_SDK_NOT_FOUND = "syncFailedSdkNotFound";
  public static final String ACTION_GRADLE_SYNC_UNKNOWN_HOST = "syncFailedUnknownHost";
  public static final String ACTION_GRADLE_SYNC_UNSUPPORTED_ANDROID_MODEL_VERSION = "syncFailedUnsupportedAndroidModelVersion";
  public static final String ACTION_GRADLE_SYNC_UNSUPPORTED_GRADLE_VERSION = "syncFailedUnsupportedGradleVersion";

  public static final String CATEGORY_PROFILING = "profiling";
  public static final String ACTION_PROFILING_CAPTURE = "captureCreated";
  public static final String ACTION_PROFILING_OPEN = "captureOpened";
  public static final String ACTION_PROFILING_CONVERT_HPROF = "hprofConversion";
  public static final String ACTION_PROFILING_ANALYSIS_RUN = "analysisRan";

  public static final String CATEGORY_MONITOR = "monitors";
  public static final String ACTION_MONITOR_ACTIVATED = "activateMonitor";
  public static final String ACTION_MONITOR_RUNNING = "runningMonitor";

  public static final String CATEGORY_SDK_MANAGER = "sdkManager";
  public static final String ACTION_SDK_MANAGER_TOOLBAR_CLICKED = "toolbarButtonClicked";
  public static final String ACTION_SDK_MANAGER_STANDALONE_LAUNCHED = "standaloneLaunched";
  public static final String ACTION_SDK_MANAGER_LOADED = "sdkManagerLoaded";

  public static final String CATEGORY_PROJECT_STRUCTURE_DIALOG = "projectStructureDialog";
  public static final String ACTION_PROJECT_STRUCTURE_DIALOG_OPEN = "open";
  public static final String ACTION_PROJECT_STRUCTURE_DIALOG_SAVE = "save";
  public static final String ACTION_PROJECT_STRUCTURE_DIALOG_TOP_TAB_CLICK = "topTabClick";
  public static final String ACTION_PROJECT_STRUCTURE_DIALOG_TOP_TAB_SAVE = "topTabSave";
  public static final String ACTION_PROJECT_STRUCTURE_DIALOG_LEFT_NAV_CLICK = "leftNavClick";
  public static final String ACTION_PROJECT_STRUCTURE_DIALOG_LEFT_NAV_SAVE = "leftNavSave";

  /**
   * Tracking when a template.xml file is rendered (instantiated) into the project.
   */
  public static final String CATEGORY_TEMPLATE = "template";
  public static final String ACTION_TEMPLATE_RENDER = "render";

  /**
   * Tracking category for the Theme Editor
   */
  public static final String CATEGORY_THEME_EDITOR = "themeEditor";
  public static final String ACTION_THEME_EDITOR_OPEN = "themeEditorOpened";

  /**
   * Tracking category for the GPU Debugger
   */
  public static final String CATEGORY_GFX_TRACE = "gfxTrace";
  public static final String ACTION_GFX_TRACE_OPEN = "gfxTraceOpened";
  public static final String ACTION_GFX_TRACE_CLOSED = "gfxTraceClosed";
  public static final String ACTION_GFX_TRACE_STARTED = "gfxTraceStarted";
  public static final String ACTION_GFX_TRACE_STOPPED = "gfxTraceStopped";
  public static final String ACTION_GFX_TRACE_COMMAND_SELECTED = "gfxTraceCommandSelected";
  public static final String ACTION_GFX_TRACE_LINK_CLICKED = "gfxTraceLinkClicked";
  public static final String ACTION_GFX_TRACE_PARAMETER_EDITED = "gfxTraceParameterEdited";
  public static final String ACTION_GFX_TRACE_TEXTURE_VIEWED = "gfxTraceTextureViewed";
  public static final String ACTION_GFX_TRACE_MEMORY_VIEWED = "gfxTraceMemoryViewed";
  public static final String ACTION_GFX_TRACE_RPC = "gfxTraceRPC";

  /**
   * Tracking category for AppIndexing
   */
  public static final String CATEGORY_APP_INDEXING = "appIndexing";
  public static final String ACTION_APP_INDEXING_DEEP_LINK_CREATED = "deepLinkCreated";
  public static final String ACTION_APP_INDEXING_API_CODE_CREATED = "apiCodeCreated";
  public static final String ACTION_APP_INDEXING_DEEP_LINK_LAUNCHED = "deepLinkLaunched";
  public static final String ACTION_APP_INDEXING_TRIGGER_QUICKFIX = "triggerQuickfix";
  public static final String ACTION_APP_INDEXING_SHOW_FEAG_DIALOG = "showFeagDialog";
  public static final String ACTION_APP_INDEXING_START_FEAG_TASK = "startFeagTask";

  /**
   * Tracking category for LLDB native debugger
   */
  public static final String CATEGORY_LLDB = "lldb";
  public static final String ACTION_LLDB_DEVICE_MODEL = IDevice.PROP_DEVICE_MODEL;
  public static final String ACTION_LLDB_DEVICE_API_LEVEL = IDevice.PROP_BUILD_API_LEVEL;
  public static final String ACTION_LLDB_SESSION_STARTED = "sessionStarted";
  public static final String ACTION_LLDB_SESSION_FAILED = "sessionFailed";
  public static final String ACTION_LLDB_INSTALL_STARTED = "installStarted";
  public static final String ACTION_LLDB_INSTALL_FAILED = "installFailed";
  public static final String ACTION_LLDB_SESSION_USED_WATCHPOINTS = "sessionUsedWatchpoints";
  public static final String ACTION_LLDB_FRONTEND_EXITED = "frontendExited";

  @SuppressWarnings("unused") // literal value used in AnalyticsUploader.trackException (under tools/idea)
  public static final String CATEGORY_THROWABLE_DETAIL_MESSAGE = "Throwable.detailMessage";

  /**
   * When using the usage tracker, do NOT include any information that can identify the user
   */
  @NotNull
  public static UsageTracker getInstance() {
    return ServiceManager.getService(UsageTracker.class);
  }

  public boolean canTrack() {
    return AndroidStudioInitializer.isAndroidStudio() && StatisticsUploadAssistant.isSendAllowed();
  }

  /**
   * When tracking events, do NOT include any information that can identify the user
   */
  public abstract void trackEvent(@NotNull String eventCategory,
                                  @NotNull String eventAction,
                                  @Nullable String eventLabel,
                                  @Nullable Integer eventValue);

  /**
   * Track the count of external dependencies (# of jars and # of aars per project). The application Id will be anonymized before upload.
   */
  public abstract void trackLibraryCount(@NotNull String applicationId, int jarDependencyCount, int aarDependencyCount);

  public abstract void trackModuleCount(@NotNull String applicationId, int total, int appModuleCount, int libModuleCount);

  public abstract void trackAndroidModule(@NotNull String applicationId, @NotNull String moduleName, boolean isLibrary,
                                          int signingConfigCount, int buildTypeCount, int flavorCount, int flavorDimension);

  public abstract void trackNativeBuildSystem(@NotNull String applicationId, @NotNull String moduleName, @NotNull String buildSystem);

  public abstract void trackGradleArtifactVersions(@NotNull String applicationId,
                                                   @NotNull String androidPluginVersion,
                                                   @NotNull String gradleVersion,
                                                   @NotNull Map<String, String> instantRunSettings);

  public abstract void trackLegacyIdeaAndroidProject(@NotNull String applicationId);

  public abstract void trackInstantRunStats(@NotNull Map<String,String> kv);
  public abstract void trackInstantRunTimings(@NotNull Map<String, String> kv);

  public abstract void trackSystemInfo(@Nullable String hyperVState, @Nullable String cpuInfoFlags);
}