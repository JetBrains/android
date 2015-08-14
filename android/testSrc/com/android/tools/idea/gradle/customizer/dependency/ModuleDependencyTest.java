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
 * Tests for {@link ModuleDependency}.
 */
public class ModuleDependencyTest extends TestCase {
  public void testConstructorWithGradlePath() {
    String projectName = "module1";
    String gradlePath = "abc:xyz:" + projectName;
    ModuleDependency dependency = new ModuleDependency(gradlePath, DependencyScope.TEST);
    assertEquals(gradlePath, dependency.getGradlePath());
    assertEquals(DependencyScope.TEST, dependency.getScope());
  }

  public void testSetBackupDependency() {
    ModuleDependency dependency = new ModuleDependency("abc:module1", DependencyScope.COMPILE);
    LibraryDependency backup = new LibraryDependency("guava-11.0.2", DependencyScope.TEST);
    dependency.setBackupDependency(backup);
    assertSame(backup, dependency.getBackupDependency());
    assertEquals(DependencyScope.COMPILE, backup.getScope());
  }

  public void testSetScope() {
    ModuleDependency dependency = new ModuleDependency("abc:module1", DependencyScope.COMPILE);
    LibraryDependency backup = new LibraryDependency("guava-11.0.2", DependencyScope.COMPILE);
    dependency.setBackupDependency(backup);
    dependency.setScope(DependencyScope.TEST);
    assertEquals(DependencyScope.TEST, dependency.getScope());
    assertEquals(DependencyScope.TEST, backup.getScope());
  }
}
