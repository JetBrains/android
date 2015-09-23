/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.dependencies;

import com.android.tools.idea.gradle.dsl.parser.dependencies.NewExternalDependency;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link NewExternalDependency}.
 */
public class NewExternalDependencyTest {
  private NewExternalDependency myDependency;

  @Before
  public void setUp() {
    myDependency = new NewExternalDependency("configurationName", "group", "name", "version");
  }

  @Test
  public void testGetCompactNotationWithoutClassifierAndExtension() {
    assertEquals("group:name:version", myDependency.getCompactNotation());
  }

  @Test
  public void testGetCompactNotationWithoutExtension() {
    myDependency.classifier = "classifier";
    assertEquals("group:name:version:classifier", myDependency.getCompactNotation());
  }

  @Test
  public void testGetCompactNotationWithoutClassifier() {
    myDependency.extension = "ext";
    assertEquals("group:name:version@ext", myDependency.getCompactNotation());
  }

  @Test
  public void testGetCompactNotation() {
    myDependency.classifier = "classifier";
    myDependency.extension = "ext";
    assertEquals("group:name:version:classifier@ext", myDependency.getCompactNotation());
  }
}