/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.deployinfo;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.Artifact;
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest;
import com.google.idea.blaze.android.manifest.ParsedManifestService;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.model.primitives.Label;
import java.io.File;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Unit tests for {@link BlazeApkDeployInfoProtoHelper}.
 *
 * <p>{@link BlazeApkDeployInfoProtoHelper#readDeployInfoProtoForTarget(Label, BuildResultHelper,
 * Predicate)} requires a integration test to test properly and is handled by {@link
 * com.google.idea.blaze.android.google3.run.deployinfo.BlazeApkDeployInfoTest}.
 */
@RunWith(JUnit4.class)
public class BlazeApkDeployInfoProtoHelperTest extends BlazeTestCase {
  private final ParsedManifestService mockParsedManifestService =
      Mockito.mock(ParsedManifestService.class);

  /**
   * Registers a mocked {@link ParsedManifestService} to return predetermined matching parsed
   * manifests and for verifying that manifest refreshes are called correctly.
   */
  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    projectServices.register(ParsedManifestService.class, mockParsedManifestService);
  }

  @Test
  public void readDeployInfoForNormalBuild_onlyMainManifest() throws Exception {
    // setup
    AndroidDeployInfo deployInfoProto =
        AndroidDeployInfo.newBuilder()
            .setMergedManifest(makeArtifact("path/to/manifest"))
            .addApksToDeploy(makeArtifact("path/to/apk"))
            .build();

    File mainApk = new File("execution_root/path/to/apk");
    File mainManifestFile = new File("execution_root/path/to/manifest");
    ParsedManifest parsedMainManifest = new ParsedManifest("main", null, null);
    when(mockParsedManifestService.getParsedManifest(mainManifestFile))
        .thenReturn(parsedMainManifest);

    // perform
    BlazeAndroidDeployInfo deployInfo =
        new BlazeApkDeployInfoProtoHelper()
            .extractDeployInfoAndInvalidateManifests(
                getProject(), new File("execution_root"), deployInfoProto);

    // verify
    assertThat(deployInfo.getApksToDeploy()).containsExactly(mainApk);
    assertThat(deployInfo.getMergedManifest()).isEqualTo(parsedMainManifest);
    assertThat(deployInfo.getTestTargetMergedManifest()).isNull();
    verify(mockParsedManifestService, times(1)).invalidateCachedManifest(mainManifestFile);
  }

  @Test
  public void readDeployInfoForNormalBuild_withTestTargetManifest() throws Exception {
    // setup
    AndroidDeployInfo deployInfoProto =
        AndroidDeployInfo.newBuilder()
            .setMergedManifest(makeArtifact("path/to/manifest"))
            .addAdditionalMergedManifests(makeArtifact("path/to/testtarget/manifest"))
            .addApksToDeploy(makeArtifact("path/to/apk"))
            .addApksToDeploy(makeArtifact("path/to/testtarget/apk"))
            .build();

    File mainApk = new File("execution_root/path/to/apk");
    File testApk = new File("execution_root/path/to/testtarget/apk");
    File mainManifest = new File("execution_root/path/to/manifest");
    File testTargetManifest = new File("execution_root/path/to/testtarget/manifest");
    ParsedManifest parsedMainManifest = new ParsedManifest("main", null, null);
    ParsedManifest parsedTestManifest = new ParsedManifest("testtarget", null, null);
    when(mockParsedManifestService.getParsedManifest(mainManifest)).thenReturn(parsedMainManifest);
    when(mockParsedManifestService.getParsedManifest(testTargetManifest))
        .thenReturn(parsedTestManifest);

    // perform
    BlazeAndroidDeployInfo deployInfo =
        new BlazeApkDeployInfoProtoHelper()
            .extractDeployInfoAndInvalidateManifests(
                getProject(), new File("execution_root"), deployInfoProto);

    // verify
    assertThat(deployInfo.getApksToDeploy()).containsExactly(mainApk, testApk).inOrder();
    assertThat(deployInfo.getMergedManifest()).isEqualTo(parsedMainManifest);
    assertThat(deployInfo.getTestTargetMergedManifest()).isEqualTo(parsedTestManifest);

    ArgumentCaptor<File> expectedArgs = ArgumentCaptor.forClass(File.class);
    verify(mockParsedManifestService, times(2)).invalidateCachedManifest(expectedArgs.capture());
    assertThat(expectedArgs.getAllValues())
        .containsExactly(mainManifest, testTargetManifest)
        .inOrder();
  }

  private static Artifact makeArtifact(String execRootPath) {
    return AndroidDeployInfoOuterClass.Artifact.newBuilder().setExecRootPath(execRootPath).build();
  }
}
