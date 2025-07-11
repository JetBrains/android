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

import com.android.utils.FileUtils;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncAutoConversionStats;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.sections.ShardBlazeBuildsSection;
import com.google.idea.blaze.base.projectview.section.sections.TextBlock;
import com.google.idea.blaze.base.projectview.section.sections.TextBlockSection;
import com.google.idea.blaze.base.projectview.section.sections.UseQuerySyncSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.common.experiments.FeatureRolloutExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
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

  private record ConversionProjectFields(
    boolean useQuerySync, boolean shardSync
  ) {
  }

  public static final FeatureRolloutExperiment AUTO_CONVERT_LEGACY_SYNC_TO_QUERY_SYNC_EXPERIMENT =
    new FeatureRolloutExperiment("aswb.auto.convert.legacy.sync.to.query.sync");
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
  public boolean canConvert(Path projectViewFilePath, int legacySyncShardCount) {
    var conversionProjectFields = parseProjectFields(projectViewFilePath);
    return AUTO_CONVERT_LEGACY_SYNC_TO_QUERY_SYNC_EXPERIMENT.isEnabled() &&
           !isConverted(projectViewFilePath) &&
           conversionProjectFields.isPresent() &&
           !conversionProjectFields.get().useQuerySync() &&
           (!conversionProjectFields.get().shardSync() || legacySyncShardCount == 1);
  }

  private boolean isConverted(Path projectViewFilePath) {
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
    if (isConverted(projectViewFilePath) || canConvert(projectViewFilePath, blazeImportSettings.getLegacySyncShardCount())) {
      return blazeImportSettings.getProjectType() == BlazeImportSettings.ProjectType.QUERY_SYNC ?
             QuerySyncAutoConversionStats.Status.CONVERTED : QuerySyncAutoConversionStats.Status.REVERTED;
    }
    return QuerySyncAutoConversionStats.Status.NOT_CONVERTED;
  }

  private Optional<ConversionProjectFields> parseProjectFields(Path projectViewFilePath) {
    ProjectViewParser parser = new ProjectViewParser(BlazeContext.create(), null);
    parser.parseProjectViewFile(projectViewFilePath.toFile(), List.of(ShardBlazeBuildsSection.PARSER, UseQuerySyncSection.PARSER));
    // Ignore parsing errors as they are expected when parsing a subset of sections.
    ProjectView projectView = Objects.requireNonNull(parser.getResult().getTopLevelProjectViewFile()).projectView;
    Optional<Boolean> useQuerySync = Optional.ofNullable(projectView.getScalarValue(UseQuerySyncSection.KEY));
    Boolean shardSync = projectView.getScalarValue(ShardBlazeBuildsSection.KEY, false);

    return useQuerySync.map(querySync -> new ConversionProjectFields(querySync, shardSync));
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
