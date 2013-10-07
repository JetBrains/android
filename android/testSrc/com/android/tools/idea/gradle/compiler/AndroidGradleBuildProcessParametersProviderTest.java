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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.util.List;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link AndroidGradleBuildProcessParametersProvider}.
 */
public class AndroidGradleBuildProcessParametersProviderTest extends IdeaTestCase {
  private AndroidGradleBuildProcessParametersProvider myParametersProvider;
  private Sdk myJdk;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myParametersProvider = new AndroidGradleBuildProcessParametersProvider(myProject);
    ExternalSystemApiUtil.executeProjectChangeAction(true, new Runnable() {
      @Override
      public void run() {
        NewProjectUtil.applyJdkToProject(myProject, getTestProjectJdk());
      }
    });
  }

  @Override
  @NotNull
  protected Sdk getTestProjectJdk() {
    if (myJdk == null) {
      String jdkHomePath = getSystemPropertyOrEnvironmentVariable("JAVA6_HOME");
      myJdk = SdkConfigurationUtil.createAndAddSDK(jdkHomePath, JavaSdk.getInstance());
    }
    assertNotNull(myJdk);
    return myJdk;
  }

  @NotNull
  private static String getSystemPropertyOrEnvironmentVariable(@NotNull String name) {
    String s = System.getProperty(name);
    if (Strings.isNullOrEmpty(s)) {
      s = System.getenv(name);
    }
    if (Strings.isNullOrEmpty(s)) {
      fail(String.format("Please set the system property or environment variable '%1$s'", name));
    }
    return s;
  }

  public void testPopulateJvmArgsWithGradleExecutionSettings() {
    GradleExecutionSettings settings = createMock(GradleExecutionSettings.class);

    expect(settings.getRemoteProcessIdleTtlInMs()).andReturn(55L);
    expect(settings.getGradleHome()).andReturn("~/gradle-1.6");
    expect(settings.isVerboseProcessing()).andReturn(true);
    expect(settings.getServiceDirectory()).andReturn("~./gradle");
    expect(settings.getDaemonVmOptions()).andReturn("-Xmx2048m -XX:MaxPermSize=512m");

    replay(settings);

    List<String> jvmArgs = Lists.newArrayList();
    myParametersProvider.populateJvmArgs(settings, jvmArgs);

    verify(settings);

    assertEquals(9, jvmArgs.size());
    String projectDirPath = FileUtil.toSystemDependentName(myProject.getBasePath());
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.project.path=" + projectDirPath));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.daemon.max.idle.time=55"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.home.path=~/gradle-1.6"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.use.verbose.logging=true"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.service.dir.path=~./gradle"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.daemon.gradle.vm.option.count=2"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.daemon.gradle.vm.option.0=-Xmx2048m"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.daemon.gradle.vm.option.1=-XX:MaxPermSize=512m"));
    String javaHomeDirPath = myJdk.getHomePath();
    assertNotNull(javaHomeDirPath);
    javaHomeDirPath = FileUtil.toSystemDependentName(javaHomeDirPath);
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.java.home.path=" + javaHomeDirPath));
  }
}
