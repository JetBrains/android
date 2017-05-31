/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.parser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;

import java.util.Map;

public class DependencyTest extends IdeaTestCase {

  public void testMavenMatching() throws Exception {
    Dependency one = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, "com.foo:artifact:1.0.0@jar");
    Dependency two = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, "com.foo:artifact:1.0.0@aar");
    Dependency three = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, "com.foo:artifact:2.0.0@jar");
    Dependency four = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, "com.foo:somethingelse:1.0.0@jar");
    Dependency five = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, "com.bar:artifact:1.0.0@jar");

    assertTrue(one.matches(two));
    assertTrue(one.matches(three));
    assertFalse(one.matches(four));
    assertFalse(one.matches(five));
  }

  public void testFiletreeListMatching() throws Exception {
    Map<String, Object> values = ImmutableMap.of("dir", (Object)"libs", "include", (Object)ImmutableList.of("*.jar", "*.aar"));
    Dependency one = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.FILETREE, values);

    Dependency two = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.FILES, "libs/foo.jar");
    Dependency three = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.FILES, "libs/foo.aar");
    Dependency four = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.FILES, "libs/foo.txt");
    Dependency five = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.FILES, "libs2/foo.jar");

    assertTrue(one.matches(two));
    assertTrue(one.matches(three));
    assertFalse(one.matches(four));
    assertFalse(one.matches(five));
  }

  public void testFiletreeSingleMatching() throws Exception {
    Map<String, Object> values = ImmutableMap.of("dir", (Object)"libs", "include", "*.jar");
    Dependency one = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.FILETREE, values);

    Dependency two = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.FILES, "libs/foo.jar");
    Dependency three = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.FILES, "libs/foo.txt");
    Dependency four = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.FILES, "libs2/foo.jar");

    assertTrue(one.matches(two));
    assertFalse(one.matches(three));
    assertFalse(one.matches(four));
  }

  public void testHardcodedAppcompatSupportMatching() throws Exception {
    Dependency one = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, "com.android.support:appcompat-v7:19.0.1");
    Dependency two = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, "com.android.support:support-v4:19.0.1");
    Dependency three = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, "com.android.support:support-v7:19.0.1");

    assertTrue(one.matches(two));
    assertFalse(one.matches(three));
  }

  public void testModulesWithLeadingColons() throws Exception {
    Dependency one = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.MODULE, ":one");
    Dependency two = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.MODULE, "one");

    assertTrue(one.matches(two));
  }

  public void testModulesWithNamedArguments() throws Exception {
    Map<String, Object> mapOne = ImmutableMap.of("path", (Object)"one", "configuration", (Object)"foo");
    Map<String, Object> mapTwo = ImmutableMap.of("path", (Object)"one", "configuration", (Object)"foo");
    Dependency one = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.MODULE, mapOne);
    Dependency two = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.MODULE, mapTwo);

    assertTrue(one.matches(two));
  }

  public void testModulesWithNamedArgumentsAndLeadingColons() throws Exception {
    Map<String, Object> mapOne = ImmutableMap.of("path", (Object)":one", "configuration", (Object)"foo");
    Map<String, Object> mapTwo = ImmutableMap.of("path", (Object)"one", "configuration", (Object)"foo");
    Dependency one = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.MODULE, mapOne);
    Dependency two = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.MODULE, mapTwo);

    assertTrue(one.matches(two));
  }
}
