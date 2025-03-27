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
package com.google.idea.blaze.qsync.testdata;

import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.EMPTY_PACKAGE_READER;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.NOOP_CONTEXT;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.GraphToProjectConverter;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectProto.Project;
import com.google.idea.blaze.qsync.project.QuerySyncLanguage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Test utility class to build simple project proto instances based on a {@link TestData} project.
 *
 * <p>The returned project protos may not be fully valid and should be relied upon only to provide
 * the basic structure.
 */
public class ProjectProtos {

  private ProjectProtos() {}

  public static Project forTestProject(TestData project) throws IOException, BuildException {
    Path workspaceImportDirectory = project.getQueryOutputPath();
    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            EMPTY_PACKAGE_READER,
            Predicates.alwaysTrue(),
            NOOP_CONTEXT,
            ProjectDefinition.builder()
                .setProjectIncludes(ImmutableSet.of(workspaceImportDirectory))
                .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JAVA))
                .build(),
            newDirectExecutorService());
    return converter.createProject(BuildGraphs.forTestProject(project));
  }
}
