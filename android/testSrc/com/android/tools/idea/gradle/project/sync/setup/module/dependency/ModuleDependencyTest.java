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
package com.android.tools.idea.gradle.project.sync.setup.module.dependency;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ModuleDependency}.
 */
public class ModuleDependencyTest {
  @Mock private Module myModule;

  @Before
  public void setUp() {
    initMocks(this);
  }

  @Test
  public void constructorWithGradlePath() {
    String projectName = "module1";
    String gradlePath = "abc:xyz:" + projectName;
    ModuleDependency dependency = new ModuleDependency(gradlePath, DependencyScope.TEST, myModule);
    assertEquals(gradlePath, dependency.getGradlePath());
    assertEquals(DependencyScope.TEST, dependency.getScope());
  }

  @Test
  public void setBackupDependency() {
    ModuleDependency dependency = new ModuleDependency("abc:module1", DependencyScope.COMPILE, myModule);
    LibraryDependency backup = new LibraryDependency(new File("guava-11.0.2.jar"), "guava-11.0.2", DependencyScope.TEST);
    dependency.setBackupDependency(backup);
    assertSame(backup, dependency.getBackupDependency());
    assertEquals(DependencyScope.COMPILE, backup.getScope());
  }

  @Test
  public void setScope() {
    ModuleDependency dependency = new ModuleDependency("abc:module1", DependencyScope.COMPILE, myModule);
    LibraryDependency backup = new LibraryDependency(new File("guava-11.0.2.jar"), "guava-11.0.2", DependencyScope.COMPILE);
    dependency.setBackupDependency(backup);
    dependency.setScope(DependencyScope.TEST);
    assertEquals(DependencyScope.TEST, dependency.getScope());
    assertEquals(DependencyScope.TEST, backup.getScope());
  }
}
