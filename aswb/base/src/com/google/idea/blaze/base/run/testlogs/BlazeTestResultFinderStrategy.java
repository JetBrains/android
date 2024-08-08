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

import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;

/** A strategy for locating results from 'blaze test' invocation (e.g. output XML files). */
public interface BlazeTestResultFinderStrategy {

  /**
   * Attempt to find test results corresponding to the most recent blaze invocation. Called after
   * the 'blaze test' process completes. Returns BlazeTestResults.NO_RESULTS if it cannot find test
   * results
   */
  BlazeTestResults findTestResults() throws GetArtifactsException;

  /** Remove any temporary files used by this result finder. */
  void deleteTemporaryOutputFiles();
}
