/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.testing;

import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

/** Base test class for integration tests. */
public abstract class AbstractIntegrationTestCase {

  @Rule public final IntellijTestSetupRule intellijTestSetupRule = new IntellijTestSetupRule();
  @Rule public final EdtRule edtRule = new EdtRule();

  protected CodeInsightTestFixture testFixture;
  protected MockExperimentService experimentService = new MockExperimentService();

  @Before
  public void setUp() throws Exception {
    IdeaTestFixtureFactory testFixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder =
        testFixtureFactory.createLightFixtureBuilder(getClass().getName());
    testFixture =
        testFixtureFactory.createCodeInsightFixture(
            testFixtureBuilder.getFixture(), new LightTempDirTestFixtureImpl(true));
    testFixture.setUp();

    verifyRequiredPluginsEnabled();

    ServiceHelper.registerApplicationComponent(
        ExperimentService.class, experimentService, testFixture.getTestRootDisposable());
  }

  @After
  public void tearDown() throws Exception {
    testFixture.tearDown();
    testFixture = null;
  }

  private static void verifyRequiredPluginsEnabled() {
    String requiredPlugins = System.getProperty("idea.required.plugins.id");
    if (requiredPlugins != null) {
      VerifyRequiredPluginsEnabled.runCheck(requiredPlugins.split(","));
    }
  }
}
