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

import com.google.common.collect.Lists;
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
  private AndroidGradleBuildProcessParametersProvider myParametersProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProject = createMock(Project.class);
    myParametersProvider = new AndroidGradleBuildProcessParametersProvider(myProject);
  }

  public void testPopulateJvmArgsWithGradleExecutionSettings() {
    GradleExecutionSettings settings = createMock(GradleExecutionSettings.class);

    expect(myProject.getBasePath()).andReturn("~/projects/project1");
    expect(settings.getRemoteProcessIdleTtlInMs()).andReturn(55L);
    expect(settings.getGradleHome()).andReturn("~/gradle-1.6");
    expect(settings.isVerboseProcessing()).andReturn(true);
    expect(settings.getServiceDirectory()).andReturn("~./gradle");
    expect(settings.getDaemonVmOptions()).andReturn("-Xmx2048m -XX:MaxPermSize=512m");
    expect(settings.getJavaHome()).andReturn("~/Libraries/Java Home");

    replay(myProject, settings);

    List<String> jvmArgs = Lists.newArrayList();
    myParametersProvider.populateJvmArgs(settings, jvmArgs);
    assertEquals(9, jvmArgs.size());
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.project.path=~/projects/project1"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.daemon.max.idle.time=55"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.home.path=~/gradle-1.6"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.use.verbose.logging=true"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.service.dir.path=~./gradle"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.daemon.gradle.vm.option.count=2"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.daemon.gradle.vm.option.0=-Xmx2048m"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.daemon.gradle.vm.option.1=-XX:MaxPermSize=512m"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.java.home.path=~/Libraries/Java Home"));
  }
}
