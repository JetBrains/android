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

/**
 * Request payload for fetching findings.
 *
 * Wraps the target package name and the filtering options.
 *
 * @property packageName The application package name (e.g., "com.example.app").
 * @property filters The filtering criteria to apply. Defaults to [FindingsFilters.ALL] (no filtering).
 */
data class FetchFindingsRequest(val packageName: String, val filters: FindingsFilters = FindingsFilters.ALL)
