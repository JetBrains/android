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

import com.android.tools.idea.testartifacts.scopes.TestArtifactSearchScopes;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.TestObject;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Android implementation of {@link TestObject} so the method {@link #getSourceScope()} can be overridden. Since {@link TestObject} is not
 * a final class (many others inherit from it and override its methods), this class receives an instance of another {@link TestObject} in
 * the constructor and uses it to call the right methods of the subclasses. Uses delegation.
 */
public class AndroidTestObject extends TestObject {
  private final TestObject myTestObject;

  public AndroidTestObject(@NotNull TestObject testObject) {
    super(testObject.getConfiguration(), testObject.getEnvironment());
    myTestObject = testObject;
  }

  @Override
  public Module[] getModulesToCompile() {
    return myTestObject.getModulesToCompile();
  }

  @Override
  public String suggestActionName() {
    return myTestObject.suggestActionName();
  }

  @Override
  public RefactoringElementListener getListener(@NotNull PsiElement element, @NotNull JUnitConfiguration configuration) {
    return myTestObject.getListener(element, configuration);
  }

  @Override
  public boolean isConfiguredByElement(@NotNull JUnitConfiguration configuration,
                                       @Nullable PsiClass testClass,
                                       @Nullable PsiMethod testMethod,
                                       @Nullable PsiPackage testPackage,
                                       @Nullable PsiDirectory testDir) {
    return myTestObject.isConfiguredByElement(configuration, testClass, testMethod, testPackage, testDir);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    myTestObject.checkConfiguration();
  }

  @Nullable
  @Override
  public SourceScope getSourceScope() {
    SourceScope original = myTestObject.getSourceScope();
    return original == null ? null : new SourceScope() {
      @Override
      public GlobalSearchScope getGlobalSearchScope() {
        GlobalSearchScope scope = original.getGlobalSearchScope();
        JUnitConfiguration configuration = getConfiguration();
        Module[] modules = configuration instanceof AndroidJUnitConfiguration ?
                           ((AndroidJUnitConfiguration)configuration).getModulesToCompile() : configuration.getModules();
        for (Module module : modules) {
          TestArtifactSearchScopes testArtifactSearchScopes = TestArtifactSearchScopes.get(module);
          if (testArtifactSearchScopes != null) {
            scope = scope.intersectWith(testArtifactSearchScopes.getAndroidTestExcludeScope());
          }
        }
        return scope;
      }

      @Override
      public Project getProject() {
        return original.getProject();
      }

      @Override
      public GlobalSearchScope getLibrariesScope() {
        GlobalSearchScope scope = original.getGlobalSearchScope();
        JUnitConfiguration configuration = getConfiguration();
        Module[] modules = configuration instanceof AndroidJUnitConfiguration ?
                           ((AndroidJUnitConfiguration)configuration).getModulesToCompile() : configuration.getModules();
        for (Module module : modules) {
          TestArtifactSearchScopes testArtifactSearchScopes = TestArtifactSearchScopes.get(module);
          if (testArtifactSearchScopes != null) {
            scope = scope.intersectWith(testArtifactSearchScopes.getAndroidTestExcludeScope());
          }
        }
        return scope;
      }

      @Override
      public Module[] getModulesToCompile() {
        return original.getModulesToCompile();
      }
    };
  }

  @NotNull
  @Override
  public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    return myTestObject.execute(executor, runner);
  }

  @NotNull
  @Override
  public JUnitConfiguration getConfiguration() {
    return myTestObject.getConfiguration();
  }
}
