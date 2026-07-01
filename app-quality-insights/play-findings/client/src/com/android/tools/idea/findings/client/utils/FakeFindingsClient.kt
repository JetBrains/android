/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.findings.client.utils

import com.android.tools.idea.findings.client.FetchFindingsRequest
import com.android.tools.idea.findings.client.FindingsClient
import com.android.tools.idea.findings.model.AffectedScope
import com.android.tools.idea.findings.model.AppFinding
import com.android.tools.idea.findings.model.FindingData
import com.android.tools.idea.findings.model.FindingSeverity
import com.android.tools.idea.findings.model.FindingType
import com.android.tools.idea.insights.LoadingState

/**
 * Fake implementation of [com.android.tools.idea.findings.client.FindingsClient] returning hardcoded mock data.
 *
 * Simulates server-side filtering locally, including package name filtering. Contains mock data for two package names: [TEST_PACKAGE_NAME]
 * and [OTHER_PACKAGE_NAME].
 *
 * This is mainly to test UI and integration layers: Allows verifying UI components without requiring a real network connection or a running
 * backend server.
 */
class FakeFindingsClient : FindingsClient {
  companion object {
    /** Standard test package name used in mock data. */
    const val TEST_PACKAGE_NAME = "com.example.app"

    /** Alternative test package name used in mock data. */
    const val OTHER_PACKAGE_NAME = "com.other.app"
  }

  private val database =
    listOf(
      // Findings for com.example.app
      AppFinding.Supported(
        name = "applications/$TEST_PACKAGE_NAME/findings/1",
        type = FindingType.DRM_APP_COMPAT,
        severity = FindingSeverity.WARNING,
        findingData =
          FindingData.DrmAppCompat(
            drmPackageName = "com.bad.sdk",
            drmDisplayName = "Bad SDK",
            drmVersion = "1.0",
            recommendedDrmVersion = "1.1",
            learnMoreUrl = "http://example.com/drm",
          ),
        affectedScopes = listOf(AffectedScope.Artifact("101")),
      ),
      AppFinding.Supported(
        name = "applications/$TEST_PACKAGE_NAME/findings/2",
        type = FindingType.DRM_APP_COMPAT,
        severity = FindingSeverity.SEVERE,
        findingData =
          FindingData.DrmAppCompat(
            drmPackageName = "com.worst.sdk",
            drmDisplayName = "Worst SDK",
            drmVersion = "2.0",
            recommendedDrmVersion = null,
            learnMoreUrl = null,
          ),
        affectedScopes = listOf(AffectedScope.Artifact("102")),
      ),
      AppFinding.Supported(
        name = "applications/$TEST_PACKAGE_NAME/findings/3",
        type = FindingType.DRM_APP_COMPAT,
        severity = FindingSeverity.INFO,
        findingData =
          FindingData.DrmAppCompat(
            drmPackageName = "com.info.sdk",
            drmDisplayName = "Info SDK",
            drmVersion = "0.9",
            recommendedDrmVersion = "1.0",
            learnMoreUrl = null,
          ),
        affectedScopes = listOf(AffectedScope.Release("release-v1.0")),
      ),
      // Findings for com.other.app
      AppFinding.Supported(
        name = "applications/$OTHER_PACKAGE_NAME/findings/4",
        type = FindingType.DRM_APP_COMPAT,
        severity = FindingSeverity.WARNING,
        findingData =
          FindingData.DrmAppCompat(
            drmPackageName = "com.another.bad.sdk",
            drmDisplayName = "Another Bad SDK",
            drmVersion = "1.5",
            recommendedDrmVersion = "1.6",
            learnMoreUrl = null,
          ),
        affectedScopes = emptyList(),
      ),
      AppFinding.Unknown(
        name = "applications/$OTHER_PACKAGE_NAME/findings/5",
        severity = FindingSeverity.INFO,
        findingData = FindingData.Unknown("{\n  \"rawKey\": \"rawValue\"\n}"),
        affectedScopes = emptyList(),
      ),
    )

  /** Returns findings from the local mock database, filtered by the package name and request criteria. */
  override suspend fun fetchFindings(request: FetchFindingsRequest): LoadingState.Done<List<AppFinding>> {
    val filtered =
      database.filter { finding ->
        val matchesPackage = finding.name.startsWith("applications/${request.packageName}/findings/")
        val matchesType = request.filters.findingTypes.isEmpty() || request.filters.findingTypes.contains(finding.type)
        val matchesSeverity = request.filters.severities.isEmpty() || request.filters.severities.contains(finding.severity)
        matchesPackage && matchesType && matchesSeverity
      }
    return LoadingState.Ready(filtered)
  }
}
