/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.importer.problems;

import com.android.SdkConstants;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.resources.ResourceFolderType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Classifies generated resource directories as "interesting" or not.
 *
 * <p>Uninteresting resources: It is very common to have string translations as the only generated
 * resources for a rule. If that's the case, we can ignore them since we likely already have a
 * source variant of the same resource as a baseline for resolving references in Java or XML.
 *
 * <p>Other generated resources are interesting.
 */
class GeneratedResourceClassifier {

  private static final Logger logger = Logger.getInstance(GeneratedResourceClassifier.class);

  private final ImmutableSortedMap<ArtifactLocation, Integer> interestingDirectories;

  GeneratedResourceClassifier(
      Project project,
      Collection<ArtifactLocation> generatedResourceLocations,
      ArtifactLocationDecoder artifactLocationDecoder,
      ListeningExecutorService executorService) {
    FileOperationProvider fileOperationProvider = FileOperationProvider.getInstance();
    List<ListenableFuture<GenResourceClassification>> jobs =
        generatedResourceLocations.stream()
            .map(
                location ->
                    executorService.submit(
                        () ->
                            classifyLocation(
                                project, location, artifactLocationDecoder, fileOperationProvider)))
            .collect(Collectors.toList());

    ImmutableSortedMap.Builder<ArtifactLocation, Integer> interesting =
        ImmutableSortedMap.naturalOrder();
    try {
      for (GenResourceClassification classification : Futures.allAsList(jobs).get()) {
        if (classification.isInteresting) {
          interesting.put(classification.artifactLocation, classification.numSubDirs);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      interesting = considerAllInteresting(generatedResourceLocations);
    } catch (ExecutionException e) {
      logger.error(e);
      interesting = considerAllInteresting(generatedResourceLocations);
    } finally {
      interestingDirectories = interesting.build();
    }
  }

  /**
   * Returns a collection of interesting directories as a sorted map from directory to estimated
   * size of subdirectory (to estimate cost of indexing).
   *
   * @return map of interesting directories and associated sizes
   */
  public ImmutableSortedMap<ArtifactLocation, Integer> getInterestingDirectories() {
    return interestingDirectories;
  }

  private static ImmutableSortedMap.Builder<ArtifactLocation, Integer> considerAllInteresting(
      Collection<ArtifactLocation> generatedResourceLocations) {
    ImmutableSortedMap.Builder<ArtifactLocation, Integer> builder =
        ImmutableSortedMap.naturalOrder();
    for (ArtifactLocation location : generatedResourceLocations) {
      builder.put(location, -1);
    }
    return builder;
  }

  private static class GenResourceClassification {
    final boolean isInteresting;
    final ArtifactLocation artifactLocation;
    final int numSubDirs;

    GenResourceClassification(
        boolean isInteresting, ArtifactLocation artifactLocation, int numSubDirs) {
      this.isInteresting = isInteresting;
      this.artifactLocation = artifactLocation;
      this.numSubDirs = numSubDirs;
    }

    static GenResourceClassification uninteresting(
        ArtifactLocation artifactLocation, int numSubDirs) {
      return new GenResourceClassification(false, artifactLocation, numSubDirs);
    }

    static GenResourceClassification interesting(
        ArtifactLocation artifactLocation, int numSubDirs) {
      return new GenResourceClassification(true, artifactLocation, numSubDirs);
    }
  }

  private static GenResourceClassification classifyLocation(
      Project project,
      ArtifactLocation artifactLocation,
      ArtifactLocationDecoder artifactLocationDecoder,
      FileOperationProvider fileOperationProvider) {
    File resDirectory =
        OutputArtifactResolver.resolve(project, artifactLocationDecoder, artifactLocation);
    if (resDirectory == null) {
      logger.warn(
          "Output artifact resolved to null. Artifact location: "
              + artifactLocation.getRelativePath());
      return GenResourceClassification.uninteresting(artifactLocation, 0);
    }
    File[] children = fileOperationProvider.listFiles(resDirectory);
    if (children == null) {
      return GenResourceClassification.uninteresting(artifactLocation, 0);
    }
    if (mayHaveNonStringTranslations(children)) {
      return GenResourceClassification.interesting(artifactLocation, children.length);
    } else {
      return GenResourceClassification.uninteresting(artifactLocation, children.length);
    }
  }

  @VisibleForTesting
  static boolean mayHaveNonStringTranslations(File[] resDirectoryChildren) {
    return Arrays.stream(resDirectoryChildren)
        .anyMatch(child -> mayHaveNonStringTranslations(child.getName()));
  }

  private static boolean mayHaveNonStringTranslations(String dirName) {
    // String translations only sit in the values-xx-rYY directories, so we can rule out other
    // directories quickly.
    if (!dirName.contains(SdkConstants.RES_QUALIFIER_SEP)) {
      return true;
    }
    if (ResourceFolderType.getFolderType(dirName) != ResourceFolderType.VALUES) {
      return true;
    }
    FolderConfiguration config = FolderConfiguration.getConfigForFolder(dirName);
    // Conservatively say it's interesting if there is an unrecognized configuration.
    if (config == null) {
      return true;
    }
    // If this is a translation mixed with something else, consider it a translation directory.
    boolean hasTranslation = false;
    for (ResourceQualifier qualifier : config.getQualifiers()) {
      if (qualifier instanceof LocaleQualifier) {
        hasTranslation = true;
      }
    }
    return !hasTranslation;
  }
}
