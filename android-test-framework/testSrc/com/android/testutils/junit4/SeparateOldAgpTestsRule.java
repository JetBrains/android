/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.testutils.junit4;

import com.android.test.testutils.TestUtils;
import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A rule to allow classes to have a mixture of tests, some included in an OldAgpSuite, and some in another suite.
 *
 * All tests marked with @OldAgpTest are ignored when run from bazel, but not from an old AGP suite
 * (i.e. AGP_VERSION is not injected)
 */
public class SeparateOldAgpTestsRule implements TestRule {
  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        boolean runningInOldAgpSuite = OldAgpSuite.AGP_VERSION != null;
        if (TestUtils.runningFromBazel() && !runningInOldAgpSuite && OldAgpFilter.isOldAgpTest(description)) {
          throw new AssumptionViolatedException("Old AGP tests are ignored when run from bazel not in an OldAgpSuite");
        }
        base.evaluate();
      }
    };
  }}
