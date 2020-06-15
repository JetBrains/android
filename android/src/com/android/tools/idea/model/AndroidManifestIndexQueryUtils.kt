/*
 * Copyright (C) 2019 The Android Open Source Project
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
@file:JvmName("AndroidManifestIndexQueryUtils")

package com.android.tools.idea.model

import com.android.sdklib.AndroidVersion
import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.model.AndroidManifestIndex.Companion.getDataForMergedManifestContributors
import com.android.tools.idea.projectsystem.ManifestOverrides
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.run.activity.DefaultActivityLocator
import com.android.tools.idea.run.activity.IndexedActivityWrapper.Companion.getActivities
import com.android.tools.idea.run.activity.IndexedActivityWrapper.Companion.getActivityAliases
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.android.facet.AndroidFacet
import java.util.LinkedList
import java.util.stream.Stream

/**
 * Applies [processContributors] to the data indexed for [facet]'s merged manifest contributors,
 * caches the result in the facet's user data as a CachedValue depending on [MergedManifestModificationTracker],
 * and then returns the result.
 */
private fun <T> AndroidFacet.queryManifestIndex(processContributors: (ManifestOverrides, Stream<AndroidManifestRawText>) -> T): T {
  val project = this.module.project
  assert(ApplicationManager.getApplication().isReadAccessAllowed)
  val modificationTracker = MergedManifestModificationTracker.getInstance(this.module)
  val provider = {
    val overrides = this.module.getModuleSystem().getManifestOverrides()
    val result = processContributors(overrides, getDataForMergedManifestContributors(this))
    CachedValueProvider.Result.create(result, modificationTracker)
  }
  val manager = CachedValuesManager.getManager(project)
  val key = manager.getKeyForClass<T>(processContributors::class.java)
  return manager.getCachedValue(this, key, provider, false)
}

/**
 * Returns the union set of activities and aliases, instead of merged results with merging rules applied.
 * (i.e. the result may include more activities than are actually in the final APK's manifest)
 * This is because run config validation can tolerate false-positives. For an accurate launching activity,
 * it's fetched in AndroidManifest.xml in generated Apk
 */
fun AndroidFacet.queryActivitiesFromManifestIndex() = queryManifestIndex { overrides, contributors ->
  assert(!DumbService.isDumb(this.module.project) && ApplicationManager.getApplication().isReadAccessAllowed)
  val activityWrappers = LinkedList<DefaultActivityLocator.ActivityWrapper>()
  val activityAliasWrappers = LinkedList<DefaultActivityLocator.ActivityWrapper>()

  contributors.forEach { manifest ->
    activityWrappers.addAll(getActivities(manifest, overrides))
    activityAliasWrappers.addAll(getActivityAliases(manifest, overrides))
  }
  activityWrappers.addAll(activityAliasWrappers)
  activityWrappers
}

/**
 * Returns the first non-null minSdk and targetSdk, or AndroidVersion.DEFAULT if none of the contributors specifies them,
 * instead of merged results with merging rules applied.
 */
fun AndroidFacet.queryMinSdkAndTargetSdkFromManifestIndex() = queryManifestIndex { overrides, contributors ->
  var minSdkLevel: String? = null
  var targetSdkLevel: String? = null

  contributors.forEach { manifest ->
    if (minSdkLevel == null && manifest.minSdkLevel != null) {
      minSdkLevel = overrides.resolvePlaceholders(manifest.minSdkLevel)
    }

    if (targetSdkLevel == null && manifest.targetSdkLevel != null) {
      targetSdkLevel = overrides.resolvePlaceholders(manifest.targetSdkLevel)
    }

    if (minSdkLevel != null && targetSdkLevel != null) return@forEach
  }

  MinSdkAndTargetSdk(SdkVersionInfo.getVersion(minSdkLevel, null) ?: AndroidVersion.DEFAULT,
                     SdkVersionInfo.getVersion(targetSdkLevel, null) ?: AndroidVersion.DEFAULT)
}

data class MinSdkAndTargetSdk(val minSdk: AndroidVersion, val targetSdk: AndroidVersion)