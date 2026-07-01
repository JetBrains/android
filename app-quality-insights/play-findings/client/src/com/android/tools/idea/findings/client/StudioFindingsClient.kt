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
package com.android.tools.idea.findings.client

import com.android.tools.findings.client.HttpException
import com.android.tools.findings.client.PlayFindingsClient
import com.android.tools.idea.findings.model.AffectedScope
import com.android.tools.idea.findings.model.AppFinding
import com.android.tools.idea.findings.model.FindingData
import com.android.tools.idea.findings.model.FindingSeverity
import com.android.tools.idea.findings.model.FindingType
import com.android.tools.idea.insights.LoadingState
import com.google.play.androidpublisher.v3.ComputeFindingsResponse as ProtoComputeFindingsResponse
import com.google.play.androidpublisher.v3.Finding as ProtoFinding
import com.google.play.androidpublisher.v3.FindingData as ProtoFindingData
import com.google.play.androidpublisher.v3.InAppLocation as ProtoInAppLocation
import com.google.protobuf.util.JsonFormat
import com.intellij.openapi.diagnostic.Logger
import java.io.IOException
import java.net.http.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LOG = Logger.getInstance(StudioFindingsClient::class.java)

/**
 * Production implementation of [FindingsClient] that communicates with the Play Developer API over HTTP.
 *
 * Delegates the actual HTTP call to [PlayFindingsClient] and handles parsing and mapping to domain models.
 */
class StudioFindingsClient(private val playClient: PlayFindingsClient) : FindingsClient {

  constructor(
    httpClient: HttpClient = HttpClient.newHttpClient(),
    baseUrl: String,
    tokenProvider: suspend () -> String?,
  ) : this(PlayFindingsClient(httpClient, baseUrl, tokenProvider))

  /** Fetches the findings for the specified application package from the Play Console. Logs any unknown finding types encountered. */
  override suspend fun fetchFindings(request: FetchFindingsRequest): LoadingState.Done<List<AppFinding>> {
    val filterStr = request.filters.toFilterString()

    return withContext(Dispatchers.IO) {
      try {
        val responseBody = playClient.fetchFindings(request.packageName, filterStr.takeIf { it.isNotEmpty() })
        val builder = ProtoComputeFindingsResponse.newBuilder()
        JsonFormat.parser().ignoringUnknownFields().merge(responseBody, builder)
        val responseProto = builder.build()

        val findings = responseProto.findingsList.map { protoFinding -> protoFinding.toDomain() }

        LoadingState.Ready(findings)
      } catch (e: HttpException) {
        // TODO(b/517599039): Narrow down error categorization and map to more specific LoadingState.Failure subclasses (e.g. Unauthorized,
        // PermissionDenied, ServerFailure) based on the HTTP status code for better error handling and display.
        LoadingState.NetworkFailure("Failed to fetch findings: ${e.statusCode} ${e.responseBody}", e)
      } catch (e: IOException) {
        LoadingState.NetworkFailure("Network error fetching findings", e)
      } catch (e: Exception) {
        LoadingState.UnknownFailure("Unknown error fetching findings", e)
      }
    }
  }
}

private fun ProtoFinding.toDomain(): AppFinding {
  val domainSeverity =
    when (findingSeverity) {
      ProtoFinding.Severity.INFO -> FindingSeverity.INFO
      ProtoFinding.Severity.WARNING -> FindingSeverity.WARNING
      ProtoFinding.Severity.SEVERE -> FindingSeverity.SEVERE
      ProtoFinding.Severity.BLOCKING -> FindingSeverity.BLOCKING
      else -> {
        LOG.warn("Encountered unknown severity: $findingSeverity (name: $name)")
        // The Severity enum is officially frozen in the API.
        // Fall back to INFO as a safe default for robustness.
        FindingSeverity.INFO
      }
    }

  val domainScopes = inAppLocationsList.mapNotNull { it.toDomain() }
  val domainData = if (hasFindingData()) findingData.toDomain() else FindingData.Empty

  return when (findingType) {
    ProtoFinding.Type.DRM_APP_COMPAT -> {
      AppFinding.Supported(name, FindingType.DRM_APP_COMPAT, domainSeverity, domainData, domainScopes)
    }
    else -> {
      LOG.warn("Encountered unknown finding type: $findingType (name: $name)")
      AppFinding.Unknown(name, domainSeverity, FindingData.Empty, domainScopes)
    }
  }
}

private fun ProtoFindingData.toDomain(): FindingData =
  when (dataTypeCase) {
    ProtoFindingData.DataTypeCase.DRM_APP_COMPAT ->
      drmAppCompat.run {
        FindingData.DrmAppCompat(
          drmPackageName = drmPackageName,
          drmDisplayName = drmDisplayName,
          drmVersion = drmVersion,
          recommendedDrmVersion = recommendedDrmVersion.takeIf { it.isNotEmpty() },
          learnMoreUrl = learnMoreUrl.takeIf { it.isNotEmpty() },
        )
      }
    else -> FindingData.Empty
  }

private fun ProtoInAppLocation.toDomain(): AffectedScope? =
  when (locationTypeCase) {
    ProtoInAppLocation.LocationTypeCase.ARTIFACT -> AffectedScope.Artifact(artifact.versionCode)
    ProtoInAppLocation.LocationTypeCase.RELEASE -> AffectedScope.Release(release.releaseName)
    else -> {
      LOG.warn("Encountered unknown location type: $locationTypeCase")
      null
    }
  }
