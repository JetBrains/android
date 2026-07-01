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

import com.android.tools.idea.findings.model.AppFinding
import com.android.tools.idea.insights.LoadingState

/**
 * Client interface for interacting with the Play Console Findings service.
 *
 * Provides API to retrieve diagnostics and alerts related to app quality, security, and policy.
 */
interface FindingsClient {
  /**
   * Fetches findings based on the provided request.
   *
   * @param request The request parameters containing package name and filters.
   * @return A [LoadingState.Done] containing either [LoadingState.Ready] with the list of [AppFinding]s, or a failure state
   *   ([LoadingState.NetworkFailure] or [LoadingState.UnknownFailure]).
   */
  suspend fun fetchFindings(request: FetchFindingsRequest): LoadingState.Done<List<AppFinding>>
}
