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
package com.android.tools.idea.gradle.dependency;

import com.intellij.openapi.roots.DependencyScope;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Tests for {@link DependencySet}.
 */
public class DependencySetTest extends TestCase {
  private DependencySet myDependencies;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDependencies = new DependencySet();
  }

  public void testAddWithNewDependency()  {
    TestDependency dependency = new TestDependency("asm-4.0.jar", DependencyScope.COMPILE);
    myDependencies.add(dependency);
    Collection<Dependency> all = myDependencies.getValues();
    assertEquals(1, all.size());
    assertSame(dependency,  ContainerUtil.getFirstItem(all));
  }

  public void testAddWithExistingDependencyWithNarrowerScope() {
    TestDependency dependency = new TestDependency("asm-4.0.jar", DependencyScope.COMPILE);
    myDependencies.add(dependency);
    myDependencies.add(new TestDependency("asm-4.0.jar", DependencyScope.TEST));
    Collection<Dependency> all = myDependencies.getValues();
    assertEquals(1, all.size());
    assertSame(dependency, ContainerUtil.getFirstItem(all));
  }

  public void testAddWithExistingDependencyWithWiderScope() {
    myDependencies.add(new TestDependency("asm-4.0.jar", DependencyScope.TEST));
    TestDependency dependency = new TestDependency("asm-4.0.jar", DependencyScope.COMPILE);
    myDependencies.add(dependency);
    Collection<Dependency> all = myDependencies.getValues();
    assertEquals(1, all.size());
    assertSame(dependency, ContainerUtil.getFirstItem(all));
  }

  private static class TestDependency extends Dependency {
    TestDependency(@NotNull String name, @NotNull DependencyScope scope) {
      super(name, scope);
    }
  }
}
