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
@file:JvmName("ThemeUtils")
package com.android.tools.idea.configurations

import com.android.SdkConstants
import com.android.annotations.concurrency.Slow
import com.android.resources.ScreenSize
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.DefaultThemeProvider.computeDefaultThemeForConfiguration
import com.android.tools.configurations.ThemeInfoProvider
import com.android.tools.dom.ActivityAttributesSnapshot
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle
import com.android.tools.idea.model.AndroidManifestIndex
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.model.MergedManifestModificationTracker
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.model.logManifestIndexQueryError
import com.android.tools.idea.model.queryActivitiesFromManifestIndex
import com.android.tools.idea.model.queryApplicationThemeFromManifestIndex
import com.android.tools.idea.model.queryIsMainManifestIndexReady
import com.android.tools.idea.run.activity.DefaultActivityLocator
import com.android.tools.idea.util.uiSafeRunReadActionInSmartMode
import com.android.tools.module.AndroidModuleInfo
import com.android.utils.cache.ChangeTracker
import com.android.utils.cache.ChangeTrackerCachedValue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.SlowOperations
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.facet.AndroidFacet
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.max

typealias ThemeStyleFilter = (ConfiguredThemeEditorStyle) -> Boolean

/**
 * [Exception] thrown when the main manifest index is not ready yet. This can happen in cases where the
 * project has just been created or the indexes have been cleaned.
 */
private class MainManifestIndexNotReadyException : Exception()

/**
 *  Try to get application theme from [AndroidManifestIndex]. And it falls back to the merged
 *  manifest snapshot if necessary.
 */
fun Module.getAppThemeName(): String? {
  try {
    val facet = AndroidFacet.getInstance(this)
    if (facet != null) {
      return uiSafeRunReadActionInSmartMode(this.project, Computable {
        SlowOperations.allowSlowOperations(ThrowableComputable {
          if (!facet.queryIsMainManifestIndexReady()) throw MainManifestIndexNotReadyException()
          facet.queryApplicationThemeFromManifestIndex()
        })
      })
    }
  }
  catch (e: MainManifestIndexNotReadyException) {
    // In this case, fallback to the merged manifest until the main manifest index is ready

  }
  catch (e: IndexNotReadyException) {
    // TODO(147116755): runReadActionInSmartMode doesn't work if we already have read access.
    //  We need to refactor the callers of this to require a *smart*
    //  read action, at which point we can remove this try-catch.
    logManifestIndexQueryError(e);
  }

  return MergedManifestManager.getFreshSnapshot(this).manifestTheme
}

/**
 *  Try to get activity themes from [AndroidManifestIndex]. And it falls back to the merged
 *  manifest snapshot if necessary.
 */
fun Module.getAllActivityThemeNames(): Set<String> {
  val activities = safeQueryActivitiesFromManifestIndex()
  if (activities != null) {
      return activities.asSequence()
        .mapNotNull(DefaultActivityLocator.ActivityWrapper::getTheme)
        .toSet()
    }
    val manifest = MergedManifestManager.getSnapshot(this)
    return manifest.activityAttributesMap.values.asSequence()
      .mapNotNull(ActivityAttributesSnapshot::getTheme)
      .toSet()
}

/**
 * Try to get value of theme corresponding to the given activity from {@link AndroidManifestIndex}.
 * And it falls back to merged manifest snapshot if necessary.
 */
fun Module.getThemeNameForActivity(activityFqcn: String): String? {
  val activities = safeQueryActivitiesFromManifestIndex()
  if (activities != null) {
    return activities.asSequence()
      .filter { it.qualifiedName == activityFqcn }
      .mapNotNull(DefaultActivityLocator.ActivityWrapper::getTheme)
      .filter { it.startsWith(SdkConstants.PREFIX_RESOURCE_REF) }
      .firstOrNull()
  }
  val manifest = MergedManifestManager.getSnapshot(this)
  return manifest.getActivityAttributes(activityFqcn)
    ?.theme
    ?.takeIf { it.startsWith(SdkConstants.PREFIX_RESOURCE_REF) }
}

fun Module.safeQueryActivitiesFromManifestIndex() : List<DefaultActivityLocator.ActivityWrapper>? {
  return safeQueryManifestIndex { facet -> facet.queryActivitiesFromManifestIndex().activities }
}

fun <T> Module.safeQueryManifestIndex(manifestIndexQueryAction: (AndroidFacet) -> T) : T? {
  try {
    val facet = AndroidFacet.getInstance(this)
    if (facet != null) {
      return uiSafeRunReadActionInSmartMode(this.project, Computable {
        SlowOperations.allowSlowOperations(ThrowableComputable {
          if (!facet.queryIsMainManifestIndexReady()) throw MainManifestIndexNotReadyException()
          manifestIndexQueryAction.invoke(facet)
        })
      })
    }
  }
  catch (e: MainManifestIndexNotReadyException) {
    // In this case, fallback to the merged manifest until the main manifest index is ready

  }
  catch (e: IndexNotReadyException) {
    // TODO(147116755): runReadActionInSmartMode doesn't work if we already have read access.
    //  We need to refactor the callers of this to require a *smart*
    //  read action, at which point we can remove this try-catch.
    logManifestIndexQueryError(e);
  }
  return null
}

/**
 * Returns a default theme
 */
fun Module.getDeviceDefaultTheme(renderingTarget: IAndroidTarget?, screenSize: ScreenSize?, device: Device?): String {
  // Facet being null should not happen, but has been observed to happen in rare scenarios (such as 73332530), probably
  // related to race condition between Gradle sync and layout rendering
  val moduleInfo = AndroidFacet.getInstance(this)?.let { StudioAndroidModuleInfo.getInstance(it) }
  return getDeviceDefaultTheme(moduleInfo, renderingTarget, screenSize, device)
}

/** Studio-specific implementation of [ThemeInfoProvider]. */
class StudioThemeInfoProvider(private val module: Module) : ThemeInfoProvider {
  private val cachedDefaultThemes = WeakHashMap<Configuration, ChangeTrackerCachedValue<String>>()
  override val appThemeName: String?
    @Slow
    get() = module.getAppThemeName()
  override val allActivityThemeNames: Set<String>
    get() = module.getAllActivityThemeNames()
  private var threadReported = false

  @Slow
  override fun getThemeNameForActivity(activityFqcn: String): String? = module.getThemeNameForActivity(activityFqcn)

  override fun getDeviceDefaultTheme(renderingTarget: IAndroidTarget?, screenSize: ScreenSize?, device: Device?): String =
    module.getDeviceDefaultTheme(renderingTarget, screenSize, device)

  @Slow
  override fun getDefaultTheme(configuration: Configuration): String {
    if (ApplicationManager.getApplication().isDispatchThread && !threadReported) {
      threadReported = true
      Logger.getInstance(StudioThemeInfoProvider::class.java).warn("getDefaultTheme should not be called in the dispatch thread")
    }
    val module = ConfigurationManager.getFromConfiguration(configuration).module

    val modificationTracker = MergedManifestModificationTracker.getInstance(module)
    val dumbServiceTracker = DumbService.getInstance(module.project)

    val defaultThemeCache = cachedDefaultThemes.getOrPut(configuration) { ChangeTrackerCachedValue.softReference() }
    val weakConfig = WeakReference(configuration)
    return runBlocking {
      ChangeTrackerCachedValue.get(defaultThemeCache, {
        computeDefaultThemeForConfiguration(configuration)
      }, ChangeTracker(
        ChangeTracker { weakConfig.get()?.modificationCount ?: 0 },
        ChangeTracker { modificationTracker.modificationCount },
        ChangeTracker { dumbServiceTracker.modificationTracker.modificationCount }
      ))
    }
  }
}

fun getDeviceDefaultTheme(moduleInfo: AndroidModuleInfo?, renderingTarget: IAndroidTarget?, screenSize: ScreenSize?, device: Device?): String {
  // For Android Wear and Android TV, the defaults differ
  if (device != null) {
    if (Device.isWear(device)) {
      return "@android:style/Theme.DeviceDefault"
    }
    else if (Device.isTv(device)) {
      return "@style/Theme.Leanback"
    }
  }

  if (moduleInfo == null) {
    return SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX + "Theme.Material.Light"
  }

  // From manifest theme documentation: "If that attribute is also not set, the default system theme is used."
  // We do use a max between targetSdk and minSdk because when targetSdk is not defined, targetSdkVersion.apiLevel is set to 1.
  val targetOrMinSdk = max(moduleInfo.targetSdkVersion.apiLevel, moduleInfo.minSdkVersion.apiLevel)

  val renderingTargetSdk = renderingTarget?.version?.apiLevel ?: targetOrMinSdk

  val apiLevel = targetOrMinSdk.coerceAtMost(renderingTargetSdk)
  return SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX + when {
    apiLevel >= 21 -> "Theme.Material.Light"
    apiLevel >= 14 || apiLevel >= 11 && screenSize == ScreenSize.XLARGE -> "Theme.Holo"
    else -> "Theme"
  }
}