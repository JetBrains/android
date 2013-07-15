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
package com.android.tools.idea.gradle.compiler;

import com.intellij.openapi.project.Project;
import junit.framework.TestCase;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.util.List;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link AndroidGradleBuildProcessParametersProvider}.
 */
public class AndroidGradleBuildProcessParametersProviderTest extends TestCase {
  private Project myProject;
  private GradleExecutionSettings myGradleExecutionSettings;

  private AndroidGradleBuildProcessParametersProvider myParametersProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProject = createMock(Project.class);
    myGradleExecutionSettings = createMock(GradleExecutionSettings.class);
    myParametersProvider = new AndroidGradleBuildProcessParametersProvider(myProject);
  }

  public void testGetGradleExecutionSettingsAsVmArgs() {
    expect(myProject.getBasePath()).andReturn("~/projects/project1");
    expect(myGradleExecutionSettings.getRemoteProcessIdleTtlInMs()).andReturn(55L);
    expect(myGradleExecutionSettings.getGradleHome()).andReturn("~/gradle-1.6");
    expect(myGradleExecutionSettings.isVerboseProcessing()).andReturn(true);
    expect(myGradleExecutionSettings.getServiceDirectory()).andReturn("~./gradle");

    replay(myProject, myGradleExecutionSettings);

    List<String> vmArgs = myParametersProvider.getGradleExecutionSettingsAsVmArgs(myGradleExecutionSettings);
    assertEquals(6, vmArgs.size());
    assertTrue(vmArgs.contains("-Dcom.android.studio.gradle.project.path=~/projects/project1"));
    assertTrue(vmArgs.contains("-Dcom.android.studio.gradle.daemon.max.idle.time=55"));
    assertTrue(vmArgs.contains("-Dcom.android.studio.gradle.home.path=~/gradle-1.6"));
    assertTrue(vmArgs.contains("-Dcom.android.studio.gradle.use.verbose.logging=true"));
    assertTrue(vmArgs.contains("-Dcom.android.studio.gradle.max.memory=256"));
    assertTrue(vmArgs.contains("-Dcom.android.studio.gradle.service.dir.path=~./gradle"));
  }
}
