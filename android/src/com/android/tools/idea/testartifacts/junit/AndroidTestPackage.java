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
package com.android.tools.idea.testartifacts.junit;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.testartifacts.scopes.TestArtifactSearchScopes;
import com.intellij.execution.CantRunException;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.TestClassFilter;
import com.intellij.execution.junit.TestPackage;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;

/**
 * Android implementation of {@link TestPackage} so the method {@link #getClassFilter(JUnitConfiguration.Data)} can be overridden. This
 * imposes the right {@link com.intellij.psi.search.GlobalSearchScope} all around TestPackage configurations.
 */
public class AndroidTestPackage extends TestPackage {
  public AndroidTestPackage(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  @Override
  @VisibleForTesting
  public TestClassFilter getClassFilter(JUnitConfiguration.Data data) throws CantRunException {
    TestClassFilter classFilter = super.getClassFilter(data);
    JUnitConfiguration configuration = getConfiguration();
    Module[] modules = configuration instanceof AndroidJUnitConfiguration ?
                       ((AndroidJUnitConfiguration)configuration).getModulesToCompile() : configuration.getModules();
    for (Module module : modules) {
      TestArtifactSearchScopes testArtifactSearchScopes = TestArtifactSearchScopes.get(module);
      if (testArtifactSearchScopes != null) {
        classFilter = classFilter.intersectionWith(testArtifactSearchScopes.getAndroidTestExcludeScope());
      }
    }
    return classFilter;
  }
}
