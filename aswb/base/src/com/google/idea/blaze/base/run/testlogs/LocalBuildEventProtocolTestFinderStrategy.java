/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.testlogs;

import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import java.util.Optional;

/**
 * A strategy for locating results from a single 'blaze test' invocation (e.g. output XML files).
 *
 * <p>Parses the output BEP proto written by blaze to locate the test XML files.
 */
public final class LocalBuildEventProtocolTestFinderStrategy
    implements BlazeTestResultFinderStrategy {
  private final BuildResultHelper buildResultHelper;

  public LocalBuildEventProtocolTestFinderStrategy(BuildResultHelper buildResultHelper) {
    this.buildResultHelper = buildResultHelper;
  }

  @Override
  public BlazeTestResults findTestResults() throws GetArtifactsException {
    return buildResultHelper.getTestResults(Optional.empty());
  }

  @Override
  public void deleteTemporaryOutputFiles() {
    buildResultHelper.deleteTemporaryOutputFiles();
  }
}
