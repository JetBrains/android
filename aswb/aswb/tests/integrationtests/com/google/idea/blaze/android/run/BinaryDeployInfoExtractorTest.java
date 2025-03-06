/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.base.bazel.BepUtils.configuration;
import static com.google.idea.blaze.base.bazel.BepUtils.outputGroup;
import static com.google.idea.blaze.base.bazel.BepUtils.parsedBep;
import static com.google.idea.blaze.base.bazel.BepUtils.setOfFiles;
import static com.google.idea.blaze.base.bazel.BepUtils.started;
import static com.google.idea.blaze.base.bazel.BepUtils.targetComplete;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.Artifact;
import com.google.idea.blaze.android.run.runner.BinaryDeployInfoExtractor;
import com.google.idea.blaze.base.run.RuntimeArtifactCache;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.bazel.BepUtils.FileArtifact;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider.BuildEventStreamException;
import com.google.idea.blaze.base.command.buildresult.bepparser.ParsedBepOutput;
import com.google.idea.blaze.base.run.RuntimeArtifactKind;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BinaryDeployInfoExtractor}. */
@RunWith(JUnit4.class)
public class BinaryDeployInfoExtractorTest extends BlazeIntegrationTestCase {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static final String MNEMONIC = "k9-opt";
  private static final ImmutableList<String> BIN_PREFIXES =
      ImmutableList.of("blaze-out", MNEMONIC, "bin");

  private BlazeContext context;

  @Before
  public void setup() {
    context = BlazeContext.create();
    registerProjectService(RuntimeArtifactCache.class, new TestRuntimeArtifactCache());
  }

  @Test
  public void parse_nominalOutput() throws BuildEventStreamException, IOException {
    NativeSymbolFinder mockSymbolFinder = mock(NativeSymbolFinder.class);
    File symbolFile = new File("/path/to/symbol");
    when(mockSymbolFinder.getNativeSymbolsForBuild(any(), any(), any(), any())).thenReturn(ImmutableList.of(symbolFile));
    registerExtension(NativeSymbolFinder.EP_NAME, mockSymbolFinder);
    BlazeBuildOutputs buildOutputs =
        BlazeBuildOutputs.fromParsedBepOutput(nominalApkBuildOutput());
    BlazeAndroidDeployInfo deployInfo =
        new BinaryDeployInfoExtractor(getProject(), Label.of("//some:target"), true, true)
            .extract(buildOutputs, "android-deploy-info", "default", context);

    assertThat(deployInfo).isNotNull();
    assertThat(deployInfo.getMergedManifest().packageName)
        .isEqualTo("com.google.android.buildsteptester");
    assertThat(deployInfo.getApksToDeploy()).isEmpty();
    assertThat(deployInfo.getSymbolFiles()).isEqualTo(ImmutableList.of(symbolFile));
    assertThat(deployInfo.getTestTargetMergedManifest()).isNull();
  }

  private ParsedBepOutput nominalApkBuildOutput() throws IOException, BuildEventStreamException {

    File mergedManifestFile = newMergedManifestXml();
    FileArtifact mergedManifestXml =
        new FileArtifact(BIN_PREFIXES, mergedManifestFile.getName(), mergedManifestFile);
    Artifact mergedManifestArtifact = TestUtil.toArtifact(mergedManifestXml);
    AndroidDeployInfo deployInfo =
        AndroidDeployInfo.newBuilder().setMergedManifest(mergedManifestArtifact).build();

    FileArtifact deployInfoPb =
        new FileArtifact(
            BIN_PREFIXES, "foo_mi.deployinfo.pb", folder.newFile("foo_mi.deployinfo.pb"));
    File file = deployInfoPb.file;
    try (OutputStream os = new FileOutputStream(file)) {
      deployInfo.writeTo(os);
    }

    ImmutableList<FileArtifact> filePaths = ImmutableList.of(deployInfoPb, mergedManifestXml);
    List<BuildEvent> events =
        ImmutableList.of(
            started(UUID.randomUUID()),
            configuration("config-id", MNEMONIC),
            setOfFiles(filePaths, "set-id", ImmutableList.of()),
            targetComplete(
                "//some:target",
                "config-id",
                ImmutableList.of(outputGroup("android-deploy-info", ImmutableList.of("set-id")))));
    return parsedBep(events);
  }

  private File newMergedManifestXml() throws IOException {
    File file = folder.newFile("merged_manifest.xml");
    Resources.asByteSource(
            Resources.getResource(
              "tools/adt/idea/aswb/aswb/testres/AndroidManifest.xml"))
        .copyTo(com.google.common.io.Files.asByteSink(file));
    return file;
  }

  private static class TestRuntimeArtifactCache implements RuntimeArtifactCache {
    @Override
    public ImmutableList<Path> fetchArtifacts(
      Label label, List<? extends OutputArtifact> artifacts, BlazeContext context, RuntimeArtifactKind artifactKind) {
      return artifacts.stream()
          .map(a -> Paths.get(a.getBazelOutRelativePath()))
          .collect(toImmutableList());
    }
  }
}
