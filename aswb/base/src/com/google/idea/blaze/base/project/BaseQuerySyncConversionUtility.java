/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.project;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.idea.blaze.base.logging.utils.querysync.QuerySyncAutoConversionStats.ShardingType.MULTI_SHARD_MULTI_COUNT;
import static com.google.idea.blaze.base.logging.utils.querysync.QuerySyncAutoConversionStats.ShardingType.MULTI_SHARD_SINGLE_COUNT;
import static com.google.idea.blaze.base.logging.utils.querysync.QuerySyncAutoConversionStats.ShardingType.MULTI_SHARD_NO_FULL_SYNC;
import static com.google.idea.blaze.base.logging.utils.querysync.QuerySyncAutoConversionStats.ShardingType.SINGLE_SHARD;

import com.android.utils.FileUtils;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncAutoConversionStats;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.sections.ImportSection;
import com.google.idea.blaze.base.projectview.section.sections.ShardBlazeBuildsSection;
import com.google.idea.blaze.base.projectview.section.sections.TextBlock;
import com.google.idea.blaze.base.projectview.section.sections.TextBlockSection;
import com.google.idea.blaze.base.projectview.section.sections.UseQuerySyncSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.experiments.FeatureRolloutExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Convert Legacy sync project to Query sync.
 */
public class BaseQuerySyncConversionUtility implements QuerySyncConversionUtility {
  private static final Logger logger = Logger.getInstance(BaseQuerySyncConversionUtility.class);

  public static final FeatureRolloutExperiment AUTO_CONVERT_LEGACY_SYNC_TO_QUERY_SYNC_EXPERIMENT =
    new FeatureRolloutExperiment("aswb.auto.convert.legacy.sync.to.query.sync");
  public static final FeatureRolloutExperiment AUTO_CONVERT_MULTI_SHARD_LEGACY_SYNC_TO_QUERY_SYNC_EXPERIMENT =
    new FeatureRolloutExperiment("aswb.auto.convert.multi.shard.legacy.sync.to.query.sync");
  public static final BoolExperiment ENABLE_CODE_ANALYSIS_ON_SYNC_MULTI_SHARD_EXPERIMENT =
    new BoolExperiment("aswb.enable.code.analysis.on.sync.multi.shard", false);
  public static final String AUTO_CONVERSION_INDICATOR = "# Auto-converted to query sync mode.";
  public static final TextBlockSection AUTO_CONVERSION_SECTION =
    TextBlockSection.of(TextBlock.of(BaseQuerySyncConversionUtility.AUTO_CONVERSION_INDICATOR));
  public static final String BAZEL_PROJECT_DIRECTORY = ".bazel";
  public static final String BLAZE_PROJECT_DIRECTORY = ".blaze";
  public static final String BACKUP_BAZEL_PROJECT_DIRECTORY = ".bazel_backup";
  public static final String BACKUP_BLAZE_PROJECT_DIRECTORY = ".blaze_backup";
  public static final String IDEA_PROJECT_DIRECTORY = ".idea";
  public static final String BACKUP_IDEA_PROJECT_DIRECTORY = ".idea_backup";
  private final Project project;

  public BaseQuerySyncConversionUtility(Project project) {
    this.project = project;
  }

  @Override
  public boolean canConvert(BlazeImportSettings blazeImportSettings, Path projectViewFilePath) {
    var useQuerySync = parseUseQuerySync(blazeImportSettings, projectViewFilePath);
    return AUTO_CONVERT_LEGACY_SYNC_TO_QUERY_SYNC_EXPERIMENT.isEnabled() &&
           !hasConversionIndicator(projectViewFilePath) &&
           useQuerySync.isPresent() &&
           !useQuerySync.get() &&
           canConvertSharding(blazeImportSettings, projectViewFilePath, blazeImportSettings.getLegacySyncShardCount());
  }

  private boolean canConvertSharding(BlazeImportSettings blazeImportSettings, Path projectViewFilePath, int legacySyncShardCount) {
    return calculateShardingType(blazeImportSettings, projectViewFilePath, legacySyncShardCount) == SINGLE_SHARD
            || AUTO_CONVERT_MULTI_SHARD_LEGACY_SYNC_TO_QUERY_SYNC_EXPERIMENT.isEnabled();
  }

  @Override
  public QuerySyncAutoConversionStats.ShardingType calculateShardingType(BlazeImportSettings blazeImportSettings,
                                                                         Path projectViewFilePath,
                                                                         int legacySyncShardCount) {
    var shardSync = parseShardSync(blazeImportSettings, projectViewFilePath);
    if (shardSync) {
      if (legacySyncShardCount == 0) {
        return MULTI_SHARD_NO_FULL_SYNC;
      }
      if (legacySyncShardCount == 1) {
        return MULTI_SHARD_SINGLE_COUNT;
      }
      return MULTI_SHARD_MULTI_COUNT;
    }
    return SINGLE_SHARD;
  }

  @Override
  public boolean canEnableCodeAnalysisOnSync(BlazeImportSettings blazeImportSettings, Path projectViewFilePath, int legacySyncShardCount) {
    var shardingType = calculateShardingType(blazeImportSettings, projectViewFilePath, legacySyncShardCount);
    if (shardingType == SINGLE_SHARD) {
      return true;
    }
    return ENABLE_CODE_ANALYSIS_ON_SYNC_MULTI_SHARD_EXPERIMENT.getValue();
  }


  private boolean hasConversionIndicator(Path projectViewFilePath) {
    try {
      return Files.readAllLines(projectViewFilePath).contains(BaseQuerySyncConversionUtility.AUTO_CONVERSION_INDICATOR);
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public boolean isConverted(ProjectViewSet.ProjectViewFile projectViewFile) {
    return projectViewFile.projectView.getSections().stream().anyMatch(x -> x.equals(AUTO_CONVERSION_SECTION));
  }

  @Override
  public QuerySyncAutoConversionStats.Status calculateStatus(BlazeImportSettings blazeImportSettings, Path projectViewFilePath) {
    if (hasConversionIndicator(projectViewFilePath)) {
      return blazeImportSettings.getProjectType() == BlazeImportSettings.ProjectType.QUERY_SYNC ?
             QuerySyncAutoConversionStats.Status.CONVERTED : QuerySyncAutoConversionStats.Status.REVERTED;
    }
    // Though conversion is not yet complete, the CONVERTED status is returned as this method is invoked by
    // `BlazeImportSettingsManager::initImportSettings` which runs before the conversion happens eventually
    if (canConvert(blazeImportSettings, projectViewFilePath)) {
      return QuerySyncAutoConversionStats.Status.CONVERTED;
    }
    return blazeImportSettings.getProjectType() == BlazeImportSettings.ProjectType.ASPECT_SYNC ?
           QuerySyncAutoConversionStats.Status.NOT_CONVERTED : QuerySyncAutoConversionStats.Status.NOT_NEEDED;
  }

 private Optional<Boolean> parseUseQuerySync(BlazeImportSettings blazeImportSettings, Path projectViewFilePath) {
    final var workspacePathResolver = new WorkspacePathResolverImpl(new WorkspaceRoot(new File(blazeImportSettings.getWorkspaceRoot())));
    ProjectViewParser parser = new ProjectViewParser(BlazeContext.create(), workspacePathResolver);
    parser.parseProjectViewFile(projectViewFilePath.toFile(), List.of(ImportSection.PARSER, UseQuerySyncSection.PARSER));
    return parser.getResult().getScalarValue(UseQuerySyncSection.KEY);
  }

  private Boolean parseShardSync(BlazeImportSettings blazeImportSettings, Path projectViewFilePath) {
    final var workspacePathResolver = new WorkspacePathResolverImpl(new WorkspaceRoot(new File(blazeImportSettings.getWorkspaceRoot())));
    ProjectViewParser parser = new ProjectViewParser(BlazeContext.create(), workspacePathResolver);
    parser.parseProjectViewFile(projectViewFilePath.toFile(), List.of(ImportSection.PARSER, ShardBlazeBuildsSection.PARSER));
    return parser.getResult().getScalarValue(ShardBlazeBuildsSection.KEY).orElse(false);
  }

  @Override
  public void backupExistingProjectDirectories() {
    String projectBasePath = project.getBasePath();
    Path bazelprojectDirectory = Paths.get(checkNotNull(projectBasePath)).resolve(BAZEL_PROJECT_DIRECTORY);
    Path backupBazelprojectDirectory = Paths.get(checkNotNull(projectBasePath)).resolve(BACKUP_BAZEL_PROJECT_DIRECTORY);
    Path blazeprojectDirectory = Paths.get(checkNotNull(projectBasePath)).resolve(BLAZE_PROJECT_DIRECTORY);
    Path backupBlazeprojectDirectory = Paths.get(checkNotNull(projectBasePath)).resolve(BACKUP_BLAZE_PROJECT_DIRECTORY);
    Path ideaDirectory = Paths.get(checkNotNull(projectBasePath)).resolve(IDEA_PROJECT_DIRECTORY);
    Path backupIdeaDirectory = Paths.get(checkNotNull(projectBasePath)).resolve(BACKUP_IDEA_PROJECT_DIRECTORY);
    if (bazelprojectDirectory.toFile().exists()) {
      bazelprojectDirectory.toFile().renameTo(backupBazelprojectDirectory.toFile());
    }
    if (blazeprojectDirectory.toFile().exists()) {
      blazeprojectDirectory.toFile().renameTo(backupBlazeprojectDirectory.toFile());
    }
    if (ideaDirectory.toFile().exists()) {
      try {
        FileUtils.copyDirectory(ideaDirectory.toFile(), backupIdeaDirectory.toFile());
      }
      catch (IOException e) {
        logger.error("Error while creating a copy of .idea directory", e);
      }
    }
  }
}
