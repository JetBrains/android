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
package com.android.tools.idea.gradle.project;

import com.android.builder.model.AndroidProject;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.junit.Before;
import org.junit.Test;

import static com.android.builder.model.AndroidProject.GENERATION_COMPONENT;
import static com.android.builder.model.AndroidProject.GENERATION_ORIGINAL;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PostProjectSetupTasksExecutorTest {
  private AndroidProject myAndroidProject;
  
  @Before
  public void setUp() {
    myAndroidProject = mock(AndroidProject.class);
  }

  @Test
  public void testCompareVersions1() {
    when(myAndroidProject.getModelVersion()).thenReturn("2.0.0");
    when(myAndroidProject.getPluginGeneration()).thenReturn(GENERATION_ORIGINAL);
    assertFalse(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0"));
    assertFalse(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0-alpha1"));
    assertFalse(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0-beta1"));
  }

  @Test
  public void testCompareVersions2() {
    when(myAndroidProject.getModelVersion()).thenReturn("2.0.0-alpha6");
    when(myAndroidProject.getPluginGeneration()).thenReturn(GENERATION_ORIGINAL);
    assertFalse(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0-alpha5"));
  }

  @Test
  public void testCompareVersions3() {
    when(myAndroidProject.getModelVersion()).thenReturn("2.0.0-alpha1");
    when(myAndroidProject.getPluginGeneration()).thenReturn(GENERATION_ORIGINAL);
    assertTrue(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0"));
    assertTrue(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0-alpha2"));
    assertTrue(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0-rc1"));
    assertTrue(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0-beta1"));
  }

  @Test
  public void testCompareVersions4() {
    when(myAndroidProject.getModelVersion()).thenReturn("2.0.0-alpha2");
    when(myAndroidProject.getPluginGeneration()).thenReturn(GENERATION_ORIGINAL);
    assertTrue(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0-rc1"));
  }

  @Test
  public void testCompareVersions5() {
    when(myAndroidProject.getModelVersion()).thenReturn("2.0.0-beta1");
    when(myAndroidProject.getPluginGeneration()).thenReturn(GENERATION_ORIGINAL);
    assertTrue(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0"));
  }

  @Test
  public void testCompareVersions6() {
    when(myAndroidProject.getModelVersion()).thenReturn("2.0.0");
    when(myAndroidProject.getPluginGeneration()).thenThrow(new UnsupportedMethodException("Method 'getPluginGeneration' not supported"));
    assertFalse(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0"));
    assertFalse(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0-alpha1"));
    assertFalse(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0-beta1"));
  }

  @Test
  public void testCompareVersions7() {
    when(myAndroidProject.getModelVersion()).thenReturn("2.0.0-alpha1");
    when(myAndroidProject.getPluginGeneration()).thenReturn(GENERATION_COMPONENT);
    assertFalse(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0"));
    assertFalse(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0-alpha2"));
    assertFalse(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0-rc1"));
    assertFalse(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(myAndroidProject, "2.0.0-beta1"));
  }
}