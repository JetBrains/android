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
package com.android.tools.idea.gradle.project.sync;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.gradle.IdeaModuleStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaProjectStub;
import com.google.common.collect.Lists;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SyncAction}.
 */
public class SyncActionTest {
  private BuildController myBuildController;
  private IdeaProjectStub myProject;
  private AndroidProject myAndroidProject;
  private IdeaModuleStub myModule;

  @Before
  public void setUp() {
    myBuildController = mock(BuildController.class);
    myProject = new IdeaProjectStub("myProject");
    myAndroidProject = TestProjects.createBasicProject(myProject.getRootDir());
    myModule = myProject.addModule(myAndroidProject.getName(), "androidTask");
  }

  @Test
  public void executeWithExistingModel() {
    SyncAction action = new SyncAction(Lists.newArrayList(AndroidProject.class));

    when(myBuildController.getModel(IdeaProject.class)).thenReturn(myProject);
    when(myBuildController.findModel(myModule, AndroidProject.class)).thenReturn(myAndroidProject);

    SyncAction.ProjectModels projectModels = action.execute(myBuildController);
    assertNotNull(projectModels);

    SyncAction.ModuleModels moduleModels = projectModels.getModels(myModule);
    AndroidProject actual = moduleModels.findModel(AndroidProject.class);

    assertSame(myAndroidProject, actual);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void executeWithNotExistingModel() {
    SyncAction action = new SyncAction(Lists.newArrayList(ModuleExtendedModel.class));

    when(myBuildController.getModel(IdeaProject.class)).thenReturn(myProject);
    when(myBuildController.findModel(myModule, ModuleExtendedModel.class)).thenReturn(null);

    SyncAction.ProjectModels projectModels = action.execute(myBuildController);
    assertNotNull(projectModels);

    SyncAction.ModuleModels moduleModels = projectModels.getModels(myModule);
    AndroidProject actual = moduleModels.findModel(AndroidProject.class);

    assertNull(actual);
  }
}