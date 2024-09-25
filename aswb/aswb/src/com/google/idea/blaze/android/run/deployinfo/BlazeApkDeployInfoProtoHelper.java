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
package com.google.idea.blaze.android.run.deployinfo;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.Artifact;
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest;
import com.google.idea.blaze.android.manifest.ParsedManifestService;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.LocalFileArtifact;
import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utilities for reading and constructing {@link AndroidDeployInfo} and {@link
 * BlazeAndroidDeployInfo}.
 */
public class BlazeApkDeployInfoProtoHelper {
  public AndroidDeployInfo readDeployInfoProtoForTarget(
      Label target, BuildResultHelper buildResultHelper, Predicate<String> pathFilter)
      throws GetDeployInfoException {
    ImmutableList<OutputArtifact> outputArtifacts;
    try {
      outputArtifacts = buildResultHelper.getBuildArtifactsForTarget(target, pathFilter);
    } catch (GetArtifactsException e) {
      throw new GetDeployInfoException(e.getMessage());
    }

    if (outputArtifacts.isEmpty()) {
      Logger log = Logger.getInstance(BlazeApkDeployInfoProtoHelper.class.getName());
      try {
        ParsedBepOutput bepOutput = buildResultHelper.getBuildOutput();
        log.warn("Local execroot: " + bepOutput.getLocalExecRoot());
        log.warn("All output artifacts:");
        for (OutputArtifact outputArtifact : bepOutput.getAllOutputArtifacts(path -> true)) {
          log.warn(outputArtifact.getRelativePath() + " -> " + outputArtifact.getRelativePath());
        }
        log.warn("All local artifacts for " + target + ":");
        List<OutputArtifact> allBuildArtifacts =
            buildResultHelper.getBuildArtifactsForTarget(target, path -> true);
        List<File> allLocalFiles = LocalFileArtifact.getLocalFiles(allBuildArtifacts);
        for (File file : allLocalFiles) {
          String path = file.getPath();
          log.warn(path);
          if (pathFilter.test(path)) {
            log.warn("Note: " + path + " passes pathFilter but was not recognized!");
          }
        }
      } catch (GetArtifactsException e) {
        log.warn("Error occured when gathering logs:", e);
      }
      throw new GetDeployInfoException(
          "No deploy info proto artifact found.  Was android_deploy_info in the output groups?");
    }

    if (outputArtifacts.size() > 1) {
      throw new GetDeployInfoException(
          "More than one deploy info proto artifact found: "
              + outputArtifacts.stream()
                  .map(OutputArtifact::getRelativePath)
                  .collect(Collectors.joining(", ", "[", "]")));
    }

    try (InputStream inputStream = outputArtifacts.get(0).getInputStream()) {
      return AndroidDeployInfo.parseFrom(inputStream);
    } catch (IOException e) {
      throw new GetDeployInfoException(e.getMessage());
    }
  }

  public BlazeAndroidDeployInfo extractDeployInfoAndInvalidateManifests(
      Project project, File executionRoot, AndroidDeployInfo deployInfoProto)
      throws GetDeployInfoException {
    return extractDeployInfoAndInvalidateManifests(
        project, executionRoot, deployInfoProto, ImmutableList.of());
  }

  public BlazeAndroidDeployInfo extractDeployInfoAndInvalidateManifests(
      Project project,
      File executionRoot,
      AndroidDeployInfo deployInfoProto,
      ImmutableList<File> symbolFiles)
      throws GetDeployInfoException {
    File mergedManifestFile =
        new File(executionRoot, deployInfoProto.getMergedManifest().getExecRootPath());
    ParsedManifest mergedManifest = getParsedManifestSafe(project, mergedManifestFile);
    ParsedManifestService.getInstance(project).invalidateCachedManifest(mergedManifestFile);

    // android_test targets uses additional merged manifests field of the deploy info proto to hold
    // the manifest of the test target APK.
    ParsedManifest testTargetMergedManifest = null;
    List<Artifact> additionalManifests = deployInfoProto.getAdditionalMergedManifestsList();
    if (additionalManifests.size() == 1) {
      File testTargetMergedManifestFile =
          new File(executionRoot, additionalManifests.get(0).getExecRootPath());
      testTargetMergedManifest = getParsedManifestSafe(project, testTargetMergedManifestFile);
      ParsedManifestService.getInstance(project)
          .invalidateCachedManifest(testTargetMergedManifestFile);
    }

    ImmutableList<File> apksToDeploy =
        deployInfoProto.getApksToDeployList().stream()
            .map(artifact -> new File(executionRoot, artifact.getExecRootPath()))
            .collect(ImmutableList.toImmutableList());

    return new BlazeAndroidDeployInfo(
        mergedManifest, testTargetMergedManifest, apksToDeploy, symbolFiles);
  }

  public BlazeAndroidDeployInfo extractInstrumentationTestDeployInfoAndInvalidateManifests(
      Project project,
      File executionRoot,
      AndroidDeployInfo instrumentorProto,
      AndroidDeployInfo appProto)
      throws GetDeployInfoException {
    File instrumentorManifest =
        new File(executionRoot, instrumentorProto.getMergedManifest().getExecRootPath());
    ParsedManifest parsedInstrumentorManifest =
        getParsedManifestSafe(project, instrumentorManifest);
    ParsedManifestService.getInstance(project).invalidateCachedManifest(instrumentorManifest);

    File appManifest = new File(executionRoot, appProto.getMergedManifest().getExecRootPath());
    ParsedManifest parsedAppManifest = getParsedManifestSafe(project, appManifest);
    ParsedManifestService.getInstance(project).invalidateCachedManifest(appManifest);

    ImmutableList<File> apksToDeploy =
        Stream.concat(
                instrumentorProto.getApksToDeployList().stream(),
                appProto.getApksToDeployList().stream())
            .map(artifact -> new File(executionRoot, artifact.getExecRootPath()))
            .collect(ImmutableList.toImmutableList());

    return new BlazeAndroidDeployInfo(parsedInstrumentorManifest, parsedAppManifest, apksToDeploy);
  }

  /** Transforms thrown {@link IOException} to {@link GetDeployInfoException} */
  private static ParsedManifest getParsedManifestSafe(Project project, File manifestFile)
      throws GetDeployInfoException {
    try {
      return ParsedManifestService.getInstance(project).getParsedManifest(manifestFile);
    } catch (IOException e) {
      throw new GetDeployInfoException(
          "Could not read merged manifest file "
              + manifestFile
              + " due to error: "
              + e.getMessage());
    }
  }

  /** Indicates a failure when extracting deploy info. */
  public static class GetDeployInfoException extends Exception {
    @VisibleForTesting
    public GetDeployInfoException(String message) {
      super(message);
    }
  }
}
