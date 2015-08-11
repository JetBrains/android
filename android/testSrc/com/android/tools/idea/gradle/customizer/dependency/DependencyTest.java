/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer.dependency;

import com.intellij.openapi.roots.DependencyScope;
import junit.framework.TestCase;

/**
 * Tests for {@link Dependency}.
 */
public class DependencyTest extends TestCase {
  public void testConstructorWithScope() {
    try {
      new Dependency(DependencyScope.RUNTIME) {
      };
      fail("Expecting an " + IllegalArgumentException.class.getSimpleName());
    }
    catch (IllegalArgumentException e) {
      assertEquals("'Runtime' is not a supported scope. Supported scopes are [Compile, Test].", e.getMessage());
    }
  }

  public void testSetScope() {
    Dependency dependency = new Dependency() {
    };
    try {
      dependency.setScope(DependencyScope.PROVIDED);
      fail("Expecting an " + IllegalArgumentException.class.getSimpleName());
    }
    catch (IllegalArgumentException e) {
      assertEquals("'Provided' is not a supported scope. Supported scopes are [Compile, Test].", e.getMessage());
    }
  }
}
