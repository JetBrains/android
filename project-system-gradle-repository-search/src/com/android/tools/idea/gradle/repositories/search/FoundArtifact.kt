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

import com.android.ide.common.gradle.Version
import com.android.tools.idea.gradle.repositories.search.AndroidSdkRepositories.ANDROID_REPOSITORY_NAME
import com.android.tools.idea.gradle.repositories.search.AndroidSdkRepositories.GOOGLE_REPOSITORY_NAME

data class FoundArtifact(
  val repositoryNames: Set<String>,
  val groupId: String,
  val name: String,
  val unsortedVersions: Set<Version>
) : Comparable<FoundArtifact> {
  constructor(repositoryName: String, groupId: String, name: String, unsortedVersions: Collection<Version>) :
    this(setOf(repositoryName), groupId, name, unsortedVersions.toSet())

  constructor(repositoryName: String, groupId: String, name: String, version: Version) :
    this(setOf(repositoryName), groupId, name, setOf(version))

  val coordinates: List<String> get() = versions.map { "$groupId:$name:$it" }
  val versions: List<Version> = unsortedVersions.sortedByDescending { it }

  override fun compareTo(other: FoundArtifact): Int =
    compareValuesBy(
      this,
      other,
      { it.getRepositoryPriority() },
      { getPackagePriority(it.groupId) },
      { it.groupId },
      { it.name }
    )
}

private fun FoundArtifact.getRepositoryPriority(): Int =
  when {
    repositoryNames.contains(ANDROID_REPOSITORY_NAME) -> 0
    repositoryNames.contains(GOOGLE_REPOSITORY_NAME) -> 1
    else -> 2
  }

private fun getPackagePriority(packageName: String): Int =
  when {
    packageName.startsWith("com.android") -> 0
    packageName.startsWith("com.google") -> 1
    else -> 2
  }
