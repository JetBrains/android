/*
 * Copyright (C) 2021 The Android Open Source Project
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
@file:JvmName("TestRunUtil")

package com.android.tools.idea.stats

import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeBaseArtifact
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.analytics.recordTestLibrary
import com.google.common.collect.Iterables
import com.google.wireless.android.sdk.stats.TestLibraries

/**
 * Constructs the [TestLibraries] protocol buffer based on dependencies in the given [IdeAndroidArtifact].
 */
fun findTestLibrariesVersions(artifact: IdeBaseArtifact): TestLibraries {
  return TestLibraries.newBuilder()
    .also { recordTestLibraries(it, artifact) }
    .build()
}

/**
 * Fills in a [TestLibraries] protocol buffer based on dependencies in the given [IdeAndroidArtifact].
 */
fun recordTestLibraries(builder: TestLibraries.Builder, artifact: IdeBaseArtifact) {
  val dependencies = artifact.compileClasspath

  for (lib in Iterables.concat(dependencies.androidLibraries, dependencies.javaLibraries)) {
    val coordinate = GradleCoordinate.parseCoordinateString(lib.target.artifactAddress) ?: continue
    val groupId = coordinate.groupId
    val artifactId = coordinate.artifactId
    val version = coordinate.lowerBoundVersion?.toString() ?: continue
    builder.recordTestLibrary(groupId, artifactId, version)
  }
}
