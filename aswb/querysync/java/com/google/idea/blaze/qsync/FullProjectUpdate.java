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
package com.google.idea.blaze.qsync;

import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.qsync.project.BuildPackage;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectStructureData;
import com.google.idea.blaze.qsync.project.ProjectStructureDataKt;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.query.QuerySummaryImpl;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A project update based on a query of all targets in the project.
 *
 * <p>This strategy is used when creating a new project from scratch, or when updating the project
 * if a partial query cannot be used.
 */
public class FullProjectUpdate implements RefreshOperation {

  private final Context<?> context;
  private final Path workspaceRoot;
  private final ProjectDefinition projectDefinition;
  private final QuerySpec.QueryStrategy queryStrategy;
  private final ProjectStructureData projectStructureData;

  public FullProjectUpdate(
      Context<?> context,
      Path workspaceRoot,
      ProjectDefinition definition,
      QuerySpec.QueryStrategy queryStrategy,
      ProjectStructureData projectStructureData) {
    this.context = context;
    this.workspaceRoot = workspaceRoot;
    this.projectDefinition = definition;
    this.queryStrategy = queryStrategy;
    this.projectStructureData = projectStructureData;
  }

  @Override
  public Optional<QuerySpec> getQuerySpec() {
    return Optional.of(
        projectDefinition
            .deriveQuerySpec(context, queryStrategy, workspaceRoot)
            .supportedRuleClasses(BlazeQueryParser.getAllSupportedRuleClasses())
            .build());
  }

  @Override
  public PostQuerySyncData createPostQuerySyncData(QuerySummary output) {
    QuerySummaryImpl.Builder builder =
        QuerySummaryImpl.newBuilder()
            .putAllPackages(output.getBuildPackages())
            .setQueryStrategy(output.getQueryStrategy());
    for (QuerySummary.BuildPackage pkg : output.getBuildPackages()) {
      Path pkgPath = pkg.getPackageLabel().getBuildPackagePath();
      BuildPackage structPkg = ProjectStructureDataKt.getBuildPackage(projectStructureData, pkgPath);
      if (structPkg != null) {
        builder.putPackageStamp(pkg.getPackageLabel(), structPkg.getStamp());
      }
    }
    return PostQuerySyncData.builder().setQuerySummary(builder.build()).build();
  }
}
