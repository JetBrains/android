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

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.ide.android.IdeAndroidProject;
import com.intellij.openapi.module.Module;
import junit.framework.TestCase;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ModuleTypeComparator}.
 */
public class ModuleTypeComparatorTest extends TestCase {
  @Mock private Module myModule1;
  @Mock private Module myModule2;

  @Mock private AndroidModuleModel myGradleModel1;
  @Mock private AndroidModuleModel myGradleModel2;

  @Mock private IdeAndroidProject myAndroidProject1;
  @Mock private IdeAndroidProject myAndroidProject2;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);

    when(myModule1.getName()).thenReturn("a");
    when(myModule2.getName()).thenReturn("b");

    when(myGradleModel1.getAndroidProject()).thenReturn(myAndroidProject1);
    when(myGradleModel2.getAndroidProject()).thenReturn(myAndroidProject2);
  }

  public void testWithJavaModules() {
    assertTrue(ModuleTypeComparator.compareModules(myModule1, myModule2, null, null) < 0);
    assertTrue(ModuleTypeComparator.compareModules(myModule2, myModule1, null, null) > 0);
    assertEquals(0, ModuleTypeComparator.compareModules(myModule1, myModule1, null, null));
  }

  public void testWithAndroidApplicationModules() {
    when(myAndroidProject1.getProjectType()).thenReturn(PROJECT_TYPE_APP);
    when(myAndroidProject2.getProjectType()).thenReturn(PROJECT_TYPE_APP);

    assertTrue(ModuleTypeComparator.compareModules(myModule1, myModule2, myGradleModel1, myGradleModel2) < 0);
    assertTrue(ModuleTypeComparator.compareModules(myModule2, myModule1, myGradleModel2, myGradleModel1) > 0);
    assertEquals(0, ModuleTypeComparator.compareModules(myModule1, myModule1, myGradleModel1, myGradleModel1));
  }

  public void testWithAndroidLibraryModules() {
    when(myAndroidProject1.getProjectType()).thenReturn(PROJECT_TYPE_LIBRARY);
    when(myAndroidProject2.getProjectType()).thenReturn(PROJECT_TYPE_LIBRARY);

    assertTrue(ModuleTypeComparator.compareModules(myModule1, myModule2, myGradleModel1, myGradleModel2) < 0);
    assertTrue(ModuleTypeComparator.compareModules(myModule2, myModule1, myGradleModel2, myGradleModel1) > 0);
    assertEquals(0, ModuleTypeComparator.compareModules(myModule1, myModule1, myGradleModel1, myGradleModel1));
  }

  public void testWithAndroidApplicationModuleAndLibraryModule() {
    when(myAndroidProject1.getProjectType()).thenReturn(PROJECT_TYPE_APP);
    when(myAndroidProject2.getProjectType()).thenReturn(PROJECT_TYPE_LIBRARY);

    assertTrue(ModuleTypeComparator.compareModules(myModule1, myModule2, myGradleModel1, myGradleModel2) < 0);
    assertTrue(ModuleTypeComparator.compareModules(myModule2, myModule1, myGradleModel2, myGradleModel1) > 0);
  }

  public void testWithAndroidApplicationModuleAndJavaModule() {
    when(myAndroidProject1.getProjectType()).thenReturn(PROJECT_TYPE_APP);

    assertTrue(ModuleTypeComparator.compareModules(myModule1, myModule2, myGradleModel1, null) < 0);
    assertTrue(ModuleTypeComparator.compareModules(myModule2, myModule1, null, myGradleModel1) > 0);
  }

  public void testWithAndroidLibraryModuleAndJavaModule() {
    when(myAndroidProject1.getProjectType()).thenReturn(PROJECT_TYPE_LIBRARY);

    assertTrue(ModuleTypeComparator.compareModules(myModule1, myModule2, myGradleModel1, null) < 0);
    assertTrue(ModuleTypeComparator.compareModules(myModule2, myModule1, null, myGradleModel1) > 0);
  }
}
