/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.fd;

import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunArtifactType;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.idea.gradle.run.GradleInstantRunContext;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InstantRunBuildAnalyzerTest {
  private Project myProject;
  private GradleInstantRunContext myContext;
  private ProcessHandler mySession;
  private InstantRunBuildInfo myBuildInfo;

  @Before
  public void setUp() {
    myProject = mock(Project.class);
    myContext = mock(GradleInstantRunContext.class);
    mySession = mock(ProcessHandler.class);
    myBuildInfo = mock(InstantRunBuildInfo.class);
    when(myBuildInfo.isCompatibleFormat()).thenReturn(true);
    when(myContext.getInstantRunBuildInfo()).thenReturn(myBuildInfo);
  }

  @Test
  public void testReuseNoChanges() {
    // setup conditions
    BuildSelection buildSelection = new BuildSelection(BuildCause.INCREMENTAL_BUILD, false);
    when(myContext.getBuildSelection()).thenReturn(buildSelection);
    when(myBuildInfo.getBuildMode()).thenReturn("HOT_WARM");
    when(myBuildInfo.getVerifierStatus()).thenReturn("NO_CHANGES");

    // test
    InstantRunBuildAnalyzer buildAnalyzer = new InstantRunBuildAnalyzer(myProject, myContext, mySession, false);
    assertEquals(DeployType.NO_CHANGES, buildAnalyzer.getDeployType());
  }

  @Test
  public void testSplitApk() {
    // setup conditions
    when(mySession.isProcessTerminated()).thenReturn(true);
    when(myBuildInfo.getArtifacts()).thenReturn(getExampleArtifact());
    when(myBuildInfo.hasOneOf(InstantRunArtifactType.SPLIT)).thenReturn(true);

    // test
    InstantRunBuildAnalyzer buildAnalyzer = new InstantRunBuildAnalyzer(myProject, myContext, mySession, false);
    assertEquals(DeployType.SPLITAPK, buildAnalyzer.getDeployType());
  }

  @Test
  public void testRestart() {
    // setup conditions
    BuildSelection buildSelection = new BuildSelection(BuildCause.INCREMENTAL_BUILD, false);
    when(myContext.getBuildSelection()).thenReturn(buildSelection);
    when(myBuildInfo.getArtifacts()).thenReturn(Collections.emptyList());
    when(myBuildInfo.getVerifierStatus()).thenReturn("METHOD_ADDED");
    when(myBuildInfo.getBuildMode()).thenReturn("HOT_WARM");

    // test
    InstantRunBuildAnalyzer buildAnalyzer = new InstantRunBuildAnalyzer(myProject, myContext, mySession, false);
    assertEquals(DeployType.RESTART, buildAnalyzer.getDeployType());
  }

  @Test
  public void testHotSwap() {
    // setup conditions
    BuildSelection buildSelection = new BuildSelection(BuildCause.INCREMENTAL_BUILD, false);
    when(myContext.getBuildSelection()).thenReturn(buildSelection);
    when(myBuildInfo.canHotswap()).thenReturn(true);
    when(myBuildInfo.getVerifierStatus()).thenReturn("METHOD_ADDED");
    when(myBuildInfo.getBuildMode()).thenReturn("HOT_WARM");

    // test
    InstantRunBuildAnalyzer buildAnalyzer = new InstantRunBuildAnalyzer(myProject, myContext, mySession, false);
    assertEquals(DeployType.HOTSWAP, buildAnalyzer.getDeployType());
  }

  @Test
  public void testWarmSwap() {
    // setup conditions
    BuildSelection buildSelection = new BuildSelection(BuildCause.INCREMENTAL_BUILD, false);
    when(myContext.getBuildSelection()).thenReturn(buildSelection);
    when(myBuildInfo.canHotswap()).thenReturn(true);
    when(myBuildInfo.getVerifierStatus()).thenReturn("METHOD_ADDED");
    when(myBuildInfo.getBuildMode()).thenReturn("HOT_WARM");

    // test
    InstantRunBuildAnalyzer buildAnalyzer = new InstantRunBuildAnalyzer(myProject, myContext, mySession, true);
    assertEquals(DeployType.WARMSWAP, buildAnalyzer.getDeployType());
  }

  private List<InstantRunArtifact> getExampleArtifact() {
    return Arrays.asList(
      new InstantRunArtifact[]{new InstantRunArtifact(InstantRunArtifactType.SPLIT, mock(File.class), "123")});
  }
}
