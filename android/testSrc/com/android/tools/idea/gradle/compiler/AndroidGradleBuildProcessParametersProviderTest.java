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

import com.android.tools.idea.AndroidTestCaseHelper;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.sdk.DefaultSdks;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.KeyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
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
    myJdk = AndroidTestCaseHelper.createAndSetJdk(myProject);
    myParametersProvider = new AndroidGradleBuildProcessParametersProvider(myProject);
  }

  public void testPopulateJvmArgsWithGradleExecutionSettings() {
    ExternalSystemApiUtil.executeProjectChangeAction(true, new DisposeAwareProjectChange(myProject) {
      @Override
      public void execute() {
        String jdkHome = myJdk.getHomePath();
        assertNotNull(jdkHome);
        File jdkHomePath = new File(FileUtil.toSystemDependentName(jdkHome));
        DefaultSdks.setDefaultJavaHome(jdkHomePath);
      }
    });

    GradleExecutionSettings settings = createMock(GradleExecutionSettings.class);

    expect(settings.getGradleHome()).andReturn("~/gradle-1.6");
    expect(settings.isVerboseProcessing()).andReturn(true);
    expect(settings.getServiceDirectory()).andReturn("~./gradle");
    expect(settings.getDaemonVmOptions()).andReturn("-Xmx2048m -XX:MaxPermSize=512m");

    replay(settings);

    List<String> jvmArgs = Lists.newArrayList();
    myParametersProvider.populateJvmArgs(settings, jvmArgs);

    verify(settings);

    String projectDirPath = FileUtil.toSystemDependentName(myProject.getBasePath());
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.project.path=" + projectDirPath));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.home.path=~" + File.separatorChar + "gradle-1.6"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.use.verbose.logging=true"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.service.dir.path=~." + File.separatorChar + "gradle"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.daemon.jvm.option.count=2"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.daemon.jvm.option.0=-Xmx2048m"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.daemon.jvm.option.1=-XX:MaxPermSize=512m"));
    String javaHomeDirPath = myJdk.getHomePath();
    assertNotNull(javaHomeDirPath);
    javaHomeDirPath = FileUtil.toSystemDependentName(javaHomeDirPath);
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.java.home.path=" + javaHomeDirPath));
  }

  public void testPopulateHttpProxyProperties() {
    List<KeyValue<String, String>> properties = Lists.newArrayList();
    properties.add(KeyValue.create("http.proxyHost", "proxy.android.com"));
    properties.add(KeyValue.create("http.proxyPort", "8080"));

    List<String> jvmArgs = Lists.newArrayList();
    AndroidGradleBuildProcessParametersProvider.populateHttpProxyProperties(jvmArgs, properties);

    assertEquals(3, jvmArgs.size());
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.proxy.property.count=2"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.proxy.property.0=http.proxyHost:proxy.android.com"));
    assertTrue(jvmArgs.contains("-Dcom.android.studio.gradle.proxy.property.1=http.proxyPort:8080"));
  }

  public void testPopulateModulesToBuildWithModuleNames() {
    BuildSettings.getInstance(myProject).setModulesToBuild(new Module[] {myModule});
    List<String> jvmArgs = Lists.newArrayList();
    myParametersProvider.populateModulesToBuild(BuildMode.CLEAN, jvmArgs);
    assertEquals(2, jvmArgs.size());
    assertEquals("-Dcom.android.studio.gradle.modules.count=1", jvmArgs.get(0));
    assertEquals("-Dcom.android.studio.gradle.modules.0=" + myModule.getName(), jvmArgs.get(1));
  }

  public void testPopulateModulesToBuildWithAssembleTranslate() {
    BuildSettings.getInstance(myProject).setModulesToBuild(new Module[] {myModule});
    List<String> jvmArgs = Lists.newArrayList();
    myParametersProvider.populateModulesToBuild(BuildMode.ASSEMBLE_TRANSLATE, jvmArgs);
    assertEquals(1, jvmArgs.size());
    assertEquals("-Dcom.android.studio.gradle.modules.count=0", jvmArgs.get(0));
  }

  public void testPopulateJvmArgsWithBuildConfiguration() {
    AndroidGradleBuildConfiguration configuration = new AndroidGradleBuildConfiguration();
    configuration.COMMAND_LINE_OPTIONS = "--stacktrace --offline";
    GradleSettings.getInstance(myProject).setOfflineWork(true);
    List<String> jvmArgs = Lists.newArrayList();
    AndroidGradleBuildProcessParametersProvider.populateJvmArgs(configuration, jvmArgs, myProject);
    assertEquals(5, jvmArgs.size());
    assertEquals("-Dcom.android.studio.gradle.offline.mode=true", jvmArgs.get(0));
    assertEquals("-Dcom.android.studio.gradle.configuration.on.demand=true", jvmArgs.get(1));
    assertEquals("-Dcom.android.studio.gradle.daemon.command.line.option.count=2", jvmArgs.get(2));
    assertEquals("-Dcom.android.studio.gradle.daemon.command.line.option.0=--stacktrace", jvmArgs.get(3));
    assertEquals("-Dcom.android.studio.gradle.daemon.command.line.option.1=--offline", jvmArgs.get(4));
  }
}
