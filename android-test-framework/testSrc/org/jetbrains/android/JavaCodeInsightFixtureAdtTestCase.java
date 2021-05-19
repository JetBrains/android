/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android;

import com.android.testutils.TestUtils;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

/**
 * A specialized version of JavaCodeInsightFixtureTestCase that uses a custom
 * java SDJ. Use this class instead if your test needs a java SDK setup.
 */
public abstract class JavaCodeInsightFixtureAdtTestCase extends JavaCodeInsightFixtureTestCase {

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    super.tuneFixture(builder);
    addJdk(builder);
  }

  public static void addJdk(JavaModuleFixtureBuilder<?> builder) {
    builder.addJdk(TestUtils.getMockJdk().getAbsolutePath());
  }
}
