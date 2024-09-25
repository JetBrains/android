/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.idea.blaze.android;

import static com.google.idea.blaze.android.AswbTestUtils.SANDBOX_IDEA_HOME;
import static com.google.idea.blaze.android.AswbTestUtils.symlinkToSandboxHome;

import org.junit.rules.ExternalResource;

/**
 * Sets up the test environment specific to running ASwB integration tests in a blaze/bazel
 * environment. This setup prepares the test environment for ASwB integration tests in the same way
 * {@link com.android.tools.idea.IdeaTestSuite} does for regular android studio integration tests
 * but this rule does not pull in all the dependencies IdeaTestSuite does and should be modified
 * incrementally if the need for those dependencies arise. Should be instantiated as a @ClassRule in
 * the outermost test class/suite.
 */
public class AswbIntegrationTestSetupRule extends ExternalResource {
  @Override
  protected void before() throws Throwable {
    symlinkRequiredLibraries();
  }

  private void symlinkRequiredLibraries() {
    /*
     * Android annotation requires a different path to match one of the candidate paths in
     * {@link com.android.tools.idea.startup.ExternalAnnotationsSupport.DEVELOPMENT_ANNOTATIONS_PATHS}
     */
    symlinkToSandboxHome(
        "tools/adt/idea/android/annotations", SANDBOX_IDEA_HOME + "android/android/annotations");
    symlinkToSandboxHome("prebuilts/studio/layoutlib", "prebuilts/studio/layoutlib");
    symlinkToSandboxHome("prebuilts/studio/sdk", "prebuilts/studio/sdk");
  }
}
