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
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.android.tools.idea.gradle.project.sync.setup.module.android.ContentRootsModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.android.DependenciesModuleSetupStep;
import com.intellij.testFramework.IdeaTestCase;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link AndroidModuleSetupStep}.
 */
public class AndroidModuleSetupStepTest extends IdeaTestCase {
  public void testGetExtensions() {
    int indexOfContentRootsModuleSetupStep = -1;
    int indexOfDependenciesModuleSetupStep = -1;
    AndroidModuleSetupStep[] setupSteps = AndroidModuleSetupStep.getExtensions();
    for (int i = 0; i < setupSteps.length; i++) {
      AndroidModuleSetupStep setupStep = setupSteps[i];
      if (setupStep instanceof ContentRootsModuleSetupStep) {
        indexOfContentRootsModuleSetupStep = i;
        continue;
      }
      if (setupStep instanceof DependenciesModuleSetupStep) {
        indexOfDependenciesModuleSetupStep = i;
      }
    }

    // ContentRootsModuleSetupStep should go before DependenciesModuleSetupStep, otherwise any excluded jars set up by
    // DependenciesModuleSetupStep will be ignored.
    assertThat(indexOfContentRootsModuleSetupStep).isLessThan(indexOfDependenciesModuleSetupStep);
  }
}