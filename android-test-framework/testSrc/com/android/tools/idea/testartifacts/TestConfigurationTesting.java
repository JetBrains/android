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
package com.android.tools.idea.testartifacts;

import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.createAndroidTestRunConfiguration;
import static com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.getPsiElement;

/**
 * Collection of utility methods for testing {@link AndroidTestRunConfiguration}s.
 */
public class TestConfigurationTesting {
  @Nullable
  public static AndroidTestRunConfiguration createAndroidTestConfigurationFromMethod(
    @NotNull Project project, @NotNull String qualifiedName, @NotNull String methodName) {
    PsiElement element = getPsiElement(project, new TestConfigurationTestingUtil.Method(qualifiedName, methodName));
    return createAndroidTestRunConfiguration(element);
  }

  @Nullable
  public static AndroidTestRunConfiguration createAndroidTestConfigurationFromClass(@NotNull Project project, @NotNull String qualifiedName) {
    PsiElement element = getPsiElement(project, new TestConfigurationTestingUtil.Class(qualifiedName));
    return createAndroidTestRunConfiguration(element);
  }

  @Nullable
  public static AndroidTestRunConfiguration createAndroidTestConfigurationFromDirectory(@NotNull Project project, @NotNull String directory) {
    PsiElement element = getPsiElement(project, new TestConfigurationTestingUtil.Directory(directory));
    return createAndroidTestRunConfiguration(element);
  }

  @Nullable
  public static AndroidTestRunConfiguration createAndroidTestConfigurationFromFile(@NotNull Project project, @NotNull String file) {
    PsiElement element = getPsiElement(project, new TestConfigurationTestingUtil.File(file));
    return createAndroidTestRunConfiguration(element);
  }
}
