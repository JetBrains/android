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
package com.google.idea.blaze.qsync.testdata

import com.google.common.util.concurrent.MoreExecutors
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.GraphToProjectConverter
import com.google.idea.blaze.qsync.QuerySyncTestUtils
import com.google.idea.blaze.qsync.java.PackageReader
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import java.io.IOException
import java.nio.file.Path

/**
 * Test utility class to build simple project proto instances based on a [TestData] project.
 *
 *
 * The returned project protos may not be fully valid and should be relied upon only to provide
 * the basic structure.
 */
object ProjectProtos {
  @Throws(IOException::class, BuildException::class)
  fun forTestProject(project: TestData): ProjectProto.Project {
    val workspaceImportDirectory = project.getQueryOutputPath()
    val converter =
      GraphToProjectConverter(
        javaPackagePrefixReader = QuerySyncTestUtils.EMPTY_PREFIX_READER,
        context = QuerySyncTestUtils.NOOP_CONTEXT,
        projectDefinition = ProjectDefinition(
          projectIncludes = setOf(workspaceImportDirectory),
          projectExcludes = emptySet(),
          targetPatterns = emptyList(),
          systemExcludes = emptySet(),
          testSources = emptySet(),
          isAndroidWorkspace = true,
          languageClasses = setOf(QuerySyncLanguage.JVM),
          deriveTargetsFromDirectories = false,
        )
      )
    return converter.createProject(BuildGraphs.forTestProject(project), ProjectPath.ExternalRepositoryFinder.createEmptyForTests())
  }
}
