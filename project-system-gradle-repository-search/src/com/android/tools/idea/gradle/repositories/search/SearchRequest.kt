/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.repositories.search

sealed class SearchQuery

sealed class GroupArtifactQuery(open val groupId: String?, open val artifactName: String?) : SearchQuery()
sealed class ModuleQuery(open val module: String) : SearchQuery()

data class SingleModuleSearchQuery(
  override val groupId: String,
  override val artifactName: String,
) : GroupArtifactQuery(groupId, artifactName)

data class ArbitraryModulesSearchQuery(
  override val groupId: String?,
  override val artifactName: String?,
) : GroupArtifactQuery(groupId, artifactName)

// Module is `groupId:artifactName`
data class ArbitraryModulesSearchByModuleQuery(override val module: String) : ModuleQuery(module)

data class SearchRequest(val query: SearchQuery, val rowCount: Int, val start: Int)

