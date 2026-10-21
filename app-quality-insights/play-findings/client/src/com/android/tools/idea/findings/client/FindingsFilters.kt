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

import com.android.tools.idea.findings.model.FindingSeverity
import com.android.tools.idea.findings.model.FindingType

/**
 * Structured filters for querying findings.
 *
 * By default, properties are initialized to empty sets. **An empty set represents the "ALL" state**, meaning no filtering restrictions are
 * applied for that category.
 *
 * @property findingTypes Filter by specific finding types (e.g., [FindingType.DRM_APP_COMPAT]). If empty, findings of all types are
 *   returned.
 * @property severities Filter by finding severity (e.g., [FindingSeverity.WARNING]). If empty, findings of all severities are returned.
 */
data class FindingsFilters(val findingTypes: Set<FindingType> = emptySet(), val severities: Set<FindingSeverity> = emptySet()) {
  companion object {
    /** Represents filters that select all findings (i.e., no filtering applied). */
    val ALL = FindingsFilters()
  }
}

/**
 * Extension to convert [FindingsFilters] to the serialized AIP-160 compatible filter string.
 *
 * Returns an empty string if no filters are defined (representing "ALL"). Individual categories (types, severities) are joined with AND,
 * while multiple values within a category are joined with OR. [FindingType.UNKNOWN] values are filtered out.
 */
fun FindingsFilters.toFilterString(): String {
  val parts = mutableListOf<String>()

  val typeNames = findingTypes.mapNotNull { it.toProto()?.name }.sorted()
  if (typeNames.isNotEmpty()) {
    val typesStr = typeNames.joinToString(separator = " OR ") { "finding_type = \"$it\"" }
    parts.add("($typesStr)")
  }

  val severityNames = severities.map { it.toProto().name }.sorted()
  if (severityNames.isNotEmpty()) {
    val severitiesStr = severityNames.joinToString(separator = " OR ") { "finding_severity = \"$it\"" }
    parts.add("($severitiesStr)")
  }

  return parts.joinToString(separator = " AND ")
}
