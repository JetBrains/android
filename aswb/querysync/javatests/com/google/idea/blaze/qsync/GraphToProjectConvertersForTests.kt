/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync

import com.google.idea.blaze.qsync.QuerySyncTestUtils.NOOP_CONTEXT
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import java.nio.file.Path

/** Test utility for building [GraphToProjectConverter] instances  */
object GraphToProjectConvertersForTests {
  fun create(
    javaPackagePrefixReader: JavaPackagePrefixReader = QuerySyncTestUtils.EMPTY_PREFIX_READER,
    projectIncludes: Set<Path> = emptySet(),
    projectExcludes: Set<Path> = emptySet(),
    languageClasses: Set<QuerySyncLanguage> = emptySet(),
    testSources: Set<String> = emptySet(),
    systemExcludes: Set<Path> = emptySet(),
  ): GraphToProjectConverter {
    return GraphToProjectConverter(
      javaPackagePrefixReader = javaPackagePrefixReader,
      context = NOOP_CONTEXT,
      projectDefinition = ProjectDefinition(
        projectIncludes = projectIncludes,
        projectExcludes = projectExcludes,
        deriveTargetsFromDirectories = false,
        targetPatterns = emptyList(),
        isAndroidWorkspace = true,
        languageClasses = languageClasses,
        testSources = testSources,
        systemExcludes = systemExcludes
      )
    )
  }
}