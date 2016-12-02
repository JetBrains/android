/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.testartifacts;

import com.android.tools.idea.testartifacts.instrumented.AndroidTestConsoleProperties;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfiguration;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;

import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromDirectory;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createJUnitConfigurationFromDirectory;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_SAME_NAME_CLASSES;

/**
 * Tests for eventual conflicts between {@link AndroidTestRunConfiguration} and {@link AndroidJUnitConfiguration}
 */
public class AndroidTestAndJUnitConfigurationConflictsTest extends AndroidGradleTestCase {
  // See http://b.android.com/215255
  public void testConfigurationsAreDifferent() throws Exception {
    loadSimpleApplication();
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    RunConfiguration androidTestRunConfiguration = createAndroidTestConfigurationFromDirectory(getProject(), "app/src/androidTest/java");
    RunConfiguration jUnitConfiguration = createJUnitConfigurationFromDirectory(getProject(), "app/src/test/java");

    assertNotNull(jUnitConfiguration);
    assertNotNull(androidTestRunConfiguration);

    assertNotSame(androidTestRunConfiguration, jUnitConfiguration);
  }

  public void testDoubleClickRedirection() throws Exception {
    String commonTestClassName = "google.testartifacts.ExampleTest";
    loadProject(TEST_ARTIFACTS_SAME_NAME_CLASSES);

    Executor executor = DefaultRunExecutor.getRunExecutorInstance();

    RunConfiguration jUnitConfiguration = createJUnitConfigurationFromDirectory(getProject(), "app/src/test/java");
    RunConfiguration androidTestRunConfiguration = createAndroidTestConfigurationFromDirectory(getProject(), "app/src/androidTest/java");

    assertNotNull(jUnitConfiguration);
    assertNotNull(androidTestRunConfiguration);

    SMTRunnerConsoleProperties jUnitProperties = ((AndroidJUnitConfiguration)jUnitConfiguration).createTestConsoleProperties(executor);
    SMTRunnerConsoleProperties androidTestProperties = new AndroidTestConsoleProperties(androidTestRunConfiguration, executor);

    PsiClass[] jUnitClasses = JavaPsiFacade.getInstance(getProject()).findClasses(commonTestClassName, jUnitProperties.getScope());
    PsiClass[] aTestClasses = JavaPsiFacade.getInstance(getProject()).findClasses(commonTestClassName, androidTestProperties.getScope());

    assertSize(1, jUnitClasses);
    assertSize(1, aTestClasses);
    assertNotSame(jUnitClasses[0], aTestClasses[0]);
  }
}
