/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

import com.intellij.openapi.Disposable;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.lang.JavaVersion;

/**
 * Provides SDK compatibility shims for base plugin API classes, available to IDEs working with Java
 * code during test-time.
 */
public class BaseJavaSdkTestCompat {

  /** #api213: inline into tests */
  public static TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(
      JavaTestFixtureFactory unused, String projectName) {
    return IdeaTestFixtureFactory.getFixtureFactory()
        .createLightFixtureBuilder(new DefaultLightProjectDescriptor(), projectName);
  }

  public static TestFixtureBuilder<IdeaProjectTestFixture> createIdeaFixtureBuilder(
      IdeaTestFixtureFactory factory, String name) {
    return factory.createLightFixtureBuilder(name);
  }

  public static void setUpMockJava(JavaVersion version, Disposable disposable) {}
}
