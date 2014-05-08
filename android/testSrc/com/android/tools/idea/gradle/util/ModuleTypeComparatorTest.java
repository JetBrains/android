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
package com.android.tools.idea.gradle.util;

import com.android.builder.model.AndroidProject;
import com.intellij.openapi.module.Module;
import junit.framework.TestCase;

import static org.easymock.EasyMock.*;

/**
 * Tests for {@link ModuleTypeComparator}.
 */
public class ModuleTypeComparatorTest extends TestCase {
  private Module myModule1;
  private Module myModule2;

  private AndroidProject myProject1;
  private AndroidProject myProject2;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModule1 = createMock(Module.class);
    myModule2 = createMock(Module.class);

    myProject1 = createMock(AndroidProject.class);
    myProject2 = createMock(AndroidProject.class);

    expect(myModule1.getName()).andStubReturn("a");
    expect(myModule2.getName()).andStubReturn("b");
  }

  public void testWithJavaModules() {
    replay(myModule1, myModule2);

    assertTrue(ModuleTypeComparator.compareModules(myModule1, myModule2, null, null) < 0);
    assertTrue(ModuleTypeComparator.compareModules(myModule2, myModule1, null, null) > 0);
    assertEquals(0, ModuleTypeComparator.compareModules(myModule1, myModule1, null, null));

    verify(myModule1, myModule2);
  }

  public void testWithAndroidApplicationModules() {
    expect(myProject1.isLibrary()).andStubReturn(false);
    expect(myProject2.isLibrary()).andStubReturn(false);

    replay(myModule1, myModule2, myProject1, myProject2);

    assertTrue(ModuleTypeComparator.compareModules(myModule1, myModule2, myProject1, myProject2) < 0);
    assertTrue(ModuleTypeComparator.compareModules(myModule2, myModule1, myProject2, myProject1) > 0);
    assertEquals(0, ModuleTypeComparator.compareModules(myModule1, myModule1, myProject1, myProject1));

    verify(myModule1, myModule2, myProject1, myProject2);
  }

  public void testWithAndroidLibraryModules() {
    expect(myProject1.isLibrary()).andStubReturn(true);
    expect(myProject2.isLibrary()).andStubReturn(true);

    replay(myModule1, myModule2, myProject1, myProject2);

    assertTrue(ModuleTypeComparator.compareModules(myModule1, myModule2, myProject1, myProject2) < 0);
    assertTrue(ModuleTypeComparator.compareModules(myModule2, myModule1, myProject2, myProject1) > 0);
    assertEquals(0, ModuleTypeComparator.compareModules(myModule1, myModule1, myProject1, myProject1));

    verify(myModule1, myModule2, myProject1, myProject2);
  }

  public void testWithAndroidApplicationModuleAndLibraryModule() {
    expect(myProject1.isLibrary()).andStubReturn(false);
    expect(myProject2.isLibrary()).andStubReturn(true);

    replay(myModule1, myModule2, myProject1, myProject2);

    assertTrue(ModuleTypeComparator.compareModules(myModule1, myModule2, myProject1, myProject2) < 0);
    assertTrue(ModuleTypeComparator.compareModules(myModule2, myModule1, myProject2, myProject1) > 0);

    verify(myModule1, myModule2, myProject1, myProject2);
  }

  public void testWithAndroidApplicationModuleAndJavaModule() {
    expect(myProject1.isLibrary()).andStubReturn(false);

    replay(myModule1, myModule2, myProject1);

    assertTrue(ModuleTypeComparator.compareModules(myModule1, myModule2, myProject1, null) < 0);
    assertTrue(ModuleTypeComparator.compareModules(myModule2, myModule1, null, myProject1) > 0);

    verify(myModule1, myModule2, myProject1);
  }

  public void testWithAndroidLibraryModuleAndJavaModule() {
    expect(myProject1.isLibrary()).andStubReturn(true);

    replay(myModule1, myModule2, myProject1);

    assertTrue(ModuleTypeComparator.compareModules(myModule1, myModule2, myProject1, null) < 0);
    assertTrue(ModuleTypeComparator.compareModules(myModule2, myModule1, null, myProject1) > 0);

    verify(myModule1, myModule2, myProject1);
  }
}
