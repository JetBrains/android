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
package com.google.idea.blaze.base.qsync;

import com.google.common.base.Stopwatch;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.logging.utils.querysync.SyncQueryStats;
import com.google.idea.blaze.base.logging.utils.querysync.SyncQueryStatsScope;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** The default implementation of QueryRunner. */
public class BazelQueryRunner implements QueryRunner {

  private static final BoolExperiment PREFER_REMOTE_QUERIES =
      new BoolExperiment("query.sync.run.query.remotely", true);

  private static final Logger logger = Logger.getInstance(BazelQueryRunner.class);

  private final Project project;
  private final BuildSystem buildSystem;

  public BazelQueryRunner(Project project, BuildSystem buildSystem) {
    this.project = project;
    this.buildSystem = buildSystem;
  }

  @Override
  public QuerySummary runQuery(QuerySpec query, BlazeContext context)
      throws IOException, BuildException {
    Stopwatch timer = Stopwatch.createStarted();
    BuildInvoker invoker;
    if (PREFER_REMOTE_QUERIES.getValue()) {
      // TODO(b/374906681) The "parallel" here is not really what we want: really we want to run the
      // query remotely if that's supported. But we know that the parallel invoker is also a remote
      // one so we use that for now. Once legacy sync code is deleted, cleaning all this up will
      // become much easier.
      invoker =
          buildSystem
              .getParallelBuildInvoker(project, context)
              .orElse(buildSystem.getDefaultInvoker(project, context));
    } else {
      invoker = buildSystem.getDefaultInvoker(project, context);
    }
    Optional<SyncQueryStats.Builder> syncQueryStatsBuilder =
        SyncQueryStatsScope.fromContext(context);
    syncQueryStatsBuilder.ifPresent(stats -> stats.setBlazeBinaryType(invoker.getType()));

    logger.info(
        String.format(
            "Running `%.250s` using invoker %s", query, invoker.getClass().getSimpleName()));

    BlazeCommand.Builder commandBuilder = BlazeCommand.builder(invoker, BlazeCommandName.QUERY);
    commandBuilder.addBlazeFlags(query.getQueryFlags());
    commandBuilder.addBlazeFlags("--keep_going");
    String queryExp = query.getQueryExpression().orElse(null);
    if (queryExp == null) {
      context.output(PrintOutput.output("Project is empty, not running a query"));
      return QuerySummary.EMPTY;
    }
    // TODO b/374906681 - The 130000 figure comes from the command runner. Move it to the invoker
    // instead of hardcoding.
    if (queryExp.length() > 130000) {
      // Query is too long, write it to a file.
      Path tmpFile =
          Files.createTempFile(
              Files.createDirectories(Path.of(project.getBasePath(), "tmp")), "query", ".txt");
      tmpFile.toFile().deleteOnExit();
      Files.writeString(tmpFile, queryExp, StandardOpenOption.WRITE);
      commandBuilder.addBlazeFlags("--query_file", tmpFile.toString());
    } else {
      commandBuilder.addBlazeFlags(queryExp);
    }
    commandBuilder.setWorkspaceRoot(query.workspaceRoot());
    addExtraFlags(commandBuilder, invoker);

    syncQueryStatsBuilder.ifPresent(
        stats -> stats.setQueryFlags(commandBuilder.build().toArgumentList()));
    try (InputStream queryStream = invoker.invokeQuery(commandBuilder, context)) {
      QuerySummary querySummary = readFrom(query.queryStrategy(), queryStream, context);
      int packagesWithErrorsCount = querySummary.getPackagesWithErrorsCount();
      context.output(
          PrintOutput.output("Total query time ms: " + timer.elapsed(TimeUnit.MILLISECONDS)));
      if (packagesWithErrorsCount > 0) {
        context.output(
            PrintOutput.error(
                "There were errors in %d packages; project will be incomplete. Please fix the above"
                    + " errors and try again.",
                packagesWithErrorsCount));
        context.setHasWarnings();
      }
      return querySummary;
    }
  }

  /** Allows derived classes to add proprietary flags to the query invocation. */
  protected void addExtraFlags(BlazeCommand.Builder commandBuilder, BuildInvoker invoker) {}

  protected QuerySummary readFrom(
      QuerySpec.QueryStrategy queryStrategy, InputStream in, BlazeContext context)
      throws BuildException {
    logger.info(String.format("Summarising query from %s", in));
    Instant start = Instant.now();
    try {
      QuerySummary summary = QuerySummary.create(queryStrategy, in);
      logger.info(
          String.format(
              "Summarised query in %ds", Duration.between(start, Instant.now()).toSeconds()));
      return summary;
    } catch (IOException e) {
      throw new BuildException("Failed to read query output", e);
    }
  }
}
