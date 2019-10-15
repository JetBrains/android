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
package com.android.tools.idea.run.activity

import com.android.SdkConstants.PREFIX_RESOURCE_REF
import com.android.tools.idea.model.AndroidManifestRawText
import com.android.tools.idea.model.IntentFilterRawText
import com.android.tools.idea.projectsystem.ManifestOverrides
import java.lang.UnsupportedOperationException

private fun AndroidManifestRawText.resolvePackageName(overrides: ManifestOverrides) = packageName?.let { overrides.resolvePlaceholders(it) }

data class IndexedActivityWrapper(
  private val enabled: String?,
  private val intentFilters: Set<IntentFilterRawText>,
  private val name: String?,
  private val overrides: ManifestOverrides,
  private val resolvedPackage: String?
) : DefaultActivityLocator.ActivityWrapper() {

  companion object {
    @JvmStatic
    fun getActivities(manifest: AndroidManifestRawText, overrides: ManifestOverrides): List<IndexedActivityWrapper> {
      val resolvedPackage = manifest.resolvePackageName(overrides)
      return manifest.activities.map { IndexedActivityWrapper(it.enabled, it.intentFilters, it.name, overrides, resolvedPackage) }
    }

    @JvmStatic
    fun getActivityAliases(manifest: AndroidManifestRawText, overrides: ManifestOverrides): List<IndexedActivityWrapper> {
      val resolvedPackage = manifest.resolvePackageName(overrides)
      return manifest.activityAliases.map { IndexedActivityWrapper(it.enabled, it.intentFilters, it.name, overrides, resolvedPackage) }
    }
  }

  override fun hasCategory(name: String): Boolean {
    return intentFilters
      .asSequence()
      .flatMap { it.categoryNames.asSequence() }
      .map(overrides::resolvePlaceholders)
      .contains(name)
  }

  override fun hasAction(name: String): Boolean {
    return intentFilters
      .asSequence()
      .flatMap { it.actionNames.asSequence() }
      .map(overrides::resolvePlaceholders)
      .contains(name)
  }

  override fun isEnabled(): Boolean {
    enabled ?: return true
    val resolvedEnabled = overrides.resolvePlaceholders(enabled)
    return resolvedEnabled.toBoolean() || resolvedEnabled.startsWith(PREFIX_RESOURCE_REF)
  }

  override fun getExported() = throw UnsupportedOperationException("AndroidManifestIndex doesn't track whether an activity is exported")

  override fun hasIntentFilter() = intentFilters.isNotEmpty()

  override fun getQualifiedName(): String? {
    name ?: return null
    val resolvedName = overrides.resolvePlaceholders(name)
    return when {
      resolvedPackage == null -> resolvedName
      resolvedName.startsWith('.') -> resolvedPackage + resolvedName
      resolvedName.contains('.') -> resolvedName // the activity name is fully-qualified
      else -> "$resolvedPackage.${resolvedName}"
    }
  }
}