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
package com.google.idea.blaze.qsync.util;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.BlazeQueryParser;
import com.google.idea.blaze.qsync.GraphToProjectConverter;
import com.google.idea.blaze.qsync.java.PackageReader;
import com.google.idea.blaze.qsync.java.PackageStatementParser;
import com.google.idea.blaze.qsync.java.ParallelPackageReader;
import com.google.idea.blaze.qsync.java.WorkspaceResolvingPackageReader;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.SnapshotDeserializer;
import com.google.protobuf.TextFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/**
 * Command line tool to run project proto creation logic on a project snapshot.
 *
 * <p>To use this, you current directory must be your workspace root. Then:
 *
 * <ol>
 *   <li>{@code blaze build
 *       //third_party/intellij/bazel/plugin/querysync/java/com/google/idea/blaze/qsync/util:project_spec_builder}
 *   <li>{@code
 *       blaze-bin/third_party/intellij/bazel/plugin/querysync/java/com/google/idea/blaze/qsync/util/project_spec_builder
 *       /path/to/project/.blaze/qsyncdata.gz}
 * </ol>
 *
 * <p>It will print the project proto to stdout in text format.
 *
 * <p>Note, you cannot just `blaze run` the target, as blaze runs it in a different directory. It
 * must be run from your workspace root for the package reading code to be able to find the source
 * files.
 */
public class ProjectSpecBuilder {

  private final CliContext context = new CliContext();

  private final File snapshotFile;
  private final Path workspaceRoot;
  private final PackageReader packageReader;
  private final ListeningExecutorService executor =
      MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

  public static void main(String[] args) throws Exception {
    System.exit(new ProjectSpecBuilder(new File(args[0])).run());
  }

  private ProjectSpecBuilder(File snapshotFile) {
    this.snapshotFile = snapshotFile;
    this.workspaceRoot = Paths.get("").toAbsolutePath();
    this.packageReader =
        new ParallelPackageReader(
            executor,
            new WorkspaceResolvingPackageReader(workspaceRoot, new PackageStatementParser()));
  }

  private int run() throws IOException, BuildException {
    PostQuerySyncData snapshot =
        new SnapshotDeserializer()
            .readFrom(new GZIPInputStream(new FileInputStream(snapshotFile)), context)
            .orElseThrow()
            .getSyncData();
    BuildGraphData buildGraph =
        new BlazeQueryParser(snapshot.querySummary(), context, ImmutableSet.of()).parse();
    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            packageReader,
            workspaceRoot,
            context,
            snapshot.projectDefinition(),
            executor);
    System.out.println(TextFormat.printer().printToString(converter.createProject(buildGraph)));
    return context.hasError() ? 1 : 0;
  }
}
