/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

/**
 * A holder class through which the results of a "blaze test" are communicated to the test console
 * responsible for displaying the results of the test.
 */
public class BlazeTestResultHolder implements BlazeTestResultFinderStrategy {
  private BlazeTestResults results;

  public void setTestResults(BlazeTestResults results) {
    this.results = results;
  }

  @Override
  public BlazeTestResults findTestResults() {
    if (results == null) {
      throw new IllegalStateException("Cannot obtain test results before they are available");
    }
    return results;
  }

  @Override
  public void deleteTemporaryOutputFiles() {}
}
