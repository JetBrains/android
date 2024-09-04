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
package com.google.idea.blaze.base.run.smrunner;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultFinderStrategy;

/**
 * Created during a single blaze test invocation, to manage test result finding and UI.
 *
 * <p>Unlike {@link BlazeTestEventsHandler}, this can be stateful, retaining information shared
 * between all stages of the test (e.g. an output file path used for both the initial blaze
 * invocation and required when parsing test results).
 */
@AutoValue
public abstract class BlazeTestUiSession {

  public static BlazeTestUiSession create(
      ImmutableList<String> blazeFlags, BlazeTestResultFinderStrategy testResultFinderStrategy) {
    return new AutoValue_BlazeTestUiSession(blazeFlags, testResultFinderStrategy);
  }

  /**
   * Blaze flags required for test UI.<br>
   * Forces local test execution, without retries.
   */
  public abstract ImmutableList<String> getBlazeFlags();

  /** Returns a {@link BlazeTestResultFinderStrategy} for this blaze test invocation. */
  public abstract BlazeTestResultFinderStrategy getTestResultFinderStrategy();
}
