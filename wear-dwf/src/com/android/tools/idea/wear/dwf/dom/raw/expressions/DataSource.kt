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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.tools.idea.wear.dwf.WFFConstants.DataSources
import com.android.tools.wear.wff.WFFVersion

/**
 * Represents a data source that can be used in an expression. A data source can either be
 * [StaticDataSource], when the ID is fixed or [PatternedDataSource] when it follows a pattern.
 *
 * @see DataSources
 * @see <a
 *   href="https://developer.android.com/reference/wear-os/wff/common/attributes/source-type">Source
 *   Type</a>
 */
sealed class DataSource(open val requiredVersion: WFFVersion)

data class StaticDataSource(val id: String, override val requiredVersion: WFFVersion) :
  DataSource(requiredVersion)

/**
 * a [DataSource] that follows a specific pattern.
 *
 * For example `WEATHER.HOURS.<integer>.IS_AVAILABLE`, where <integer> is not pre-defined.
 */
data class PatternedDataSource(val pattern: Regex, override val requiredVersion: WFFVersion) :
  DataSource(requiredVersion)

/** Finds a [DataSource] by an ID or a pattern. */
fun findDataSource(idOrPattern: String) =
  DataSources.ALL_STATIC_BY_ID[idOrPattern]
    ?: DataSources.ALL_PATTERNS.find { it.pattern.matches(idOrPattern) }
