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
package com.android.tools.idea.gradle.project.sync.validation.android;

import com.android.tools.idea.gradle.project.sync.validation.android.AndroidModuleValidator.AndroidModuleValidatorImpl;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link AndroidModuleValidatorImpl}.
 */
public class AndroidModuleValidatorImplIntegrationTest extends AndroidGradleTestCase {
  public void testDefaultConstructor() {
    Project project = getProject();
    AndroidModuleValidatorImpl validator = new AndroidModuleValidatorImpl(project);

    Class<?>[] expectedStrategyTypes = new Class[]{
      EncodingValidationStrategy.class,
      BuildTools23Rc1ValidationStrategy.class
    };
    int strategyCount = expectedStrategyTypes.length;

    AndroidProjectValidationStrategy[] strategies = validator.getStrategies();
    assertThat(strategies).hasLength(strategyCount);

    for (int i = 0; i < strategyCount; i++) {
      AndroidProjectValidationStrategy strategy = strategies[i];
      assertThat(strategy).isInstanceOf(expectedStrategyTypes[i]);
      assertSame(project, strategy.getProject());
    }
  }
}
