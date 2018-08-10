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
package com.android.tools.idea.gradle.structure.model.repositories.search

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.structure.model.repositories.search.AndroidSdkRepositories.ANDROID_REPOSITORY_NAME
import com.android.tools.idea.gradle.structure.model.repositories.search.AndroidSdkRepositories.GOOGLE_REPOSITORY_NAME

data class FoundArtifact(
  val repositoryName: String,
  val groupId: String,
  val name: String,
  val unsortedVersions: Set<GradleVersion>
) : Comparable<FoundArtifact> {
  constructor(repositoryName: String, groupId: String, name: String, version: GradleVersion) :
    this(repositoryName, groupId, name, setOf(version))

  constructor(repositoryName: String, groupId: String, name: String, versions: Collection<GradleVersion>) :
    this(repositoryName, groupId, name, versions.toSet())

  val coordinates: List<String> get() = versions.map { "$groupId:$name:$it" }
  val versions: List<GradleVersion> = unsortedVersions.sortedByDescending { it }

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
  when (repositoryName) {
    ANDROID_REPOSITORY_NAME -> 0
    GOOGLE_REPOSITORY_NAME -> 1
    else -> 2
  }

private fun getPackagePriority(packageName: String): Int =
  when {
    packageName.startsWith("com.android") -> 0
    packageName.startsWith("com.google") -> 1
    else -> 2
  }
