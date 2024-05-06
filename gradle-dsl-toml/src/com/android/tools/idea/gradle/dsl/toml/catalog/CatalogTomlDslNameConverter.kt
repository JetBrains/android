/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.toml.catalog

import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.UNKNOWN
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter.Kind.CATALOG_TOML
import com.android.tools.idea.gradle.dsl.toml.TomlDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement

interface CatalogTomlDslNameConverter: TomlDslNameConverter {
  override fun getKind() = CATALOG_TOML

  override fun externalNameForParent(modelName: String, context: GradleDslElement) = ExternalNameInfo(modelName, UNKNOWN)
}