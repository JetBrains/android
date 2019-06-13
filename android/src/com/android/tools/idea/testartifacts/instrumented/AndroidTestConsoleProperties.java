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
package com.android.tools.idea.testartifacts.instrumented;

import com.android.tools.idea.projectsystem.TestArtifactSearchScopes;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.module.Module;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 * @since Aug 28, 2009
 */
public class AndroidTestConsoleProperties extends SMTRunnerConsoleProperties {
  public AndroidTestConsoleProperties(@NotNull RunConfiguration configuration, @NotNull Executor executor) {
    super(configuration, "Android", executor);
  }

  @Override
  public SMTestLocator getTestLocator() {
    return AndroidTestLocationProvider.INSTANCE;
  }

  @NotNull
  @Override
  protected GlobalSearchScope initScope() {
    GlobalSearchScope scope = super.initScope();

    Module[] modules = ((ModuleRunProfile)getConfiguration()).getModules();
    for (Module each : modules) {
      // UnitTest scope in each module is excluded from the scope used to find JUnitTests
      TestArtifactSearchScopes testArtifactSearchScopes = TestArtifactSearchScopes.getInstance(each);
      if (testArtifactSearchScopes != null) {
        scope = scope.intersectWith(GlobalSearchScope.notScope(testArtifactSearchScopes.getUnitTestSourceScope()));
      }
    }
    return scope;
  }
}
