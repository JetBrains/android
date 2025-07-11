/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.wear.wff.WFFVersion
import com.android.tools.wear.wff.WFFVersion.WFFVersion1
import com.android.tools.wear.wff.WFFVersion.WFFVersion2
import com.android.tools.wear.wff.WFFVersionExtractor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module

/**
 * Represents the current [WFFVersion] used in the editor. [isFallback] is true if the version in
 * the merged manifest was invalid or missing.
 */
data class CurrentWFFVersion(val wffVersion: WFFVersion, val isFallback: Boolean)

@Service
class CurrentWFFVersionService(
  private val wffVersionExtractor: WFFVersionExtractor = WFFVersionExtractor()
) {
  /**
   * Returns a [CurrentWFFVersion] for a given [Module].
   *
   * If there is no merged manifest, the method will return `null`. If the version specified in the
   * merged manifest is missing or invalid, it will return a fallback version.
   *
   * @see getFallbackVersion
   */
  fun getCurrentWFFVersion(module: Module): CurrentWFFVersion? {
    val manifestDocument =
      MergedManifestManager.getMergedManifestSupplier(module).now?.document ?: return null
    val manifestVersion = wffVersionExtractor.extractFromManifest(manifestDocument)
    return CurrentWFFVersion(
      wffVersion = manifestVersion ?: getFallbackVersion(module),
      isFallback = manifestVersion == null,
    )
  }

  private fun getFallbackVersion(module: Module): WFFVersion {
    val minSdk = AndroidModel.get(module)?.minSdkVersion
    return if (minSdk?.isAtLeast(AndroidVersion.VersionCodes.UPSIDE_DOWN_CAKE) == true) WFFVersion2
    else WFFVersion1
  }

  companion object {
    fun getInstance() = service<CurrentWFFVersionService>()
  }
}
