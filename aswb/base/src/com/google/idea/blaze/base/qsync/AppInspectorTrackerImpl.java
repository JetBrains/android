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

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.bazel.BazelExitCode;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

/**
 * An implementation of {@link AppInspectorTracker} service responsible for management of artifacts
 * used by app inspectors.
 */
public class AppInspectorTrackerImpl implements AppInspectorTracker {

  private final AppInspectorBuilder appInspectorBuilder;
  private final AppInspectorArtifactTracker appInspectorArtifactTracker;

  public AppInspectorTrackerImpl(
      AppInspectorBuilder appInspectorBuilder,
      AppInspectorArtifactTracker appInspectorArtifactTracker) {
    this.appInspectorBuilder = appInspectorBuilder;
    this.appInspectorArtifactTracker = appInspectorArtifactTracker;
  }

  @Override
  public ImmutableCollection<Path> buildAppInspector(
      BlazeContext context, List<Label> appInspectors) throws IOException, BuildException {
    HashSet<Label> targets = Sets.newHashSet(appInspectors);
    AppInspectorInfo appInspectorInfo = appInspectorBuilder.buildAppInspector(context, targets);
    if (appInspectorInfo.isEmpty()) {
      throw new NoAppInspectorBuiltException(
          String.format("Building %s produced no jars.", labelsToDisplayText(appInspectors)));
    }

    if (appInspectorInfo.getExitCode() != BazelExitCode.SUCCESS) {
      // This will happen if there is an error in a build file, as no build actions are attempted
      // in that case.
      context.setHasWarnings();
      context.output(
          PrintOutput.error(
              String.format(
                  "There were build errors when building %s app inspector jar.",
                  labelsToDisplayText(appInspectors))));
    }

    return appInspectorArtifactTracker.update(targets, appInspectorInfo, context);
  }

  private static String labelsToDisplayText(List<Label> labels) {
    return labels.stream().map(Label::toString).collect(joining(", "));
  }
}
