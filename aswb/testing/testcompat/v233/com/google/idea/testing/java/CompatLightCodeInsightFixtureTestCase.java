/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.testing.java;

import com.google.idea.testing.IntellijTestSetupRule;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.junit.Rule;

/** Compat base class for LightCodeInsightFixtureTestCase */
public class CompatLightCodeInsightFixtureTestCase extends LightCodeInsightFixtureTestCase {
  @Rule public final IntellijTestSetupRule setupRule = new IntellijTestSetupRule();

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor();
  }
}
