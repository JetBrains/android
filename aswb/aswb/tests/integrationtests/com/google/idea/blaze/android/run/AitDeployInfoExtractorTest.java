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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Resources;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.Artifact;
import com.google.idea.blaze.android.run.runner.AitDeployInfoExtractor;
import com.google.idea.blaze.base.run.RuntimeArtifactCache;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.runner.InstrumentationInfo;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.bazel.BepUtils.FileArtifact;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider.BuildEventStreamException;
import com.google.idea.blaze.base.command.buildresult.bepparser.ParsedBepOutput;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.RuntimeArtifactKind;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AitDeployInfoExtractorTest extends BlazeIntegrationTestCase {
  private static final Label TEST_APP = Label.create("//javatests/com/foo/test:binary");
  private static final Label TARGET_APP = Label.create("//javatests/com/foo/target:binary");

  private static final String MNEMONIC = "k9-opt";
  private static final ImmutableList<String> BIN_PREFIXES =
      ImmutableList.of("blaze-out", MNEMONIC, "bin");
  private static final FileArtifact TEST_APK_ARTIFACT_FILE =
      new FileArtifact(BIN_PREFIXES, "some/random/test.apk", new File("/some/random/test.apk"));
  private static final FileArtifact TARGET_APK_ARTIFACT_FILE =
      new FileArtifact(BIN_PREFIXES, "some/random/target.apk", new File("/some/random/target.apk"));

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private BlazeContext context;

  @Before
  public void setup() {
    context = BlazeContext.create();
    registerProjectService(RuntimeArtifactCache.class, new TestRuntimeArtifactCache());
  }

  @Test
  public void extract_testWithNoTarget_deployInfoContainsDataFromTestTarget()
      throws BuildEventStreamException, IOException {

    // Create build output that matches the output for a setup with a test target (TEST_APP)
    // that instruments itself (i.e. no target app).
    InstrumentationInfo instrumentationInfo = new InstrumentationInfo(null, TEST_APP);
    ParsedBepOutput bepOutput =
        new AitBepBuilder(folder)
            .addTargetComplete(
                TEST_APP.toString(),
                "tools/adt/idea/aswb/aswb/testres/AndroidManifest.xml",
                ImmutableList.of(TEST_APK_ARTIFACT_FILE))
            .build();
    BlazeBuildOutputs buildOutputs =
        BlazeBuildOutputs.fromParsedBepOutput(bepOutput);

    BlazeAndroidDeployInfo deployInfo =
        new AitDeployInfoExtractor(getProject(), instrumentationInfo)
            .extract(buildOutputs, "android-deploy-info", "default", context);

    assertThat(deployInfo).isNotNull();
    assertThat(deployInfo.getMergedManifest().packageName)
        .isEqualTo("com.google.android.buildsteptester");
    assertThat(deployInfo.getApksToDeploy()).hasSize(1);
    assertThat(deployInfo.getApksToDeploy().get(0).getPath())
        .startsWith(TestRuntimeArtifactCache.CACHE_BASE);
    assertThat(deployInfo.getTestTargetMergedManifest()).isNull();
  }

  @Test
  public void extract_testWithApp_deployInfoContainsDataFromTestAndTargetApp()
      throws BuildEventStreamException, IOException {
    // Create build output that matches the output for a setup with a test target (TEST_APP)
    // that instruments TARGET_APP
    InstrumentationInfo instrumentationInfo = new InstrumentationInfo(TARGET_APP, TEST_APP);
    ParsedBepOutput bepOutput =
        new AitBepBuilder(folder)
            .addTargetComplete(
                TEST_APP.toString(),
                "tools/adt/idea/aswb/aswb/testres/AndroidManifest.xml",
                ImmutableList.of(TEST_APK_ARTIFACT_FILE))
            .addTargetComplete(
                TARGET_APP.toString(),
                "tools/adt/idea/aswb/aswb/testres/testManifest.xml",
                ImmutableList.of(TARGET_APK_ARTIFACT_FILE))
            .build();
    BlazeBuildOutputs buildOutputs =
        BlazeBuildOutputs.fromParsedBepOutput(bepOutput);

    BlazeAndroidDeployInfo deployInfo =
        new AitDeployInfoExtractor(getProject(), instrumentationInfo)
            .extract(buildOutputs, "android-deploy-info", "default", context);

    assertThat(deployInfo).isNotNull();
    assertThat(deployInfo.getMergedManifest().packageName)
        .isEqualTo("com.google.android.buildsteptester");
    assertThat(deployInfo.getApksToDeploy()).hasSize(2);
    deployInfo.getApksToDeploy().stream()
        .map(File::getPath)
        .forEach(p -> assertThat(p).startsWith(TestRuntimeArtifactCache.CACHE_BASE));
    assertThat(deployInfo.getTestTargetMergedManifest()).isNotNull();
    assertThat(deployInfo.getTestTargetMergedManifest().packageName)
        .isEqualTo("com.google.android.libraries.foo");
  }

  private static class TestRuntimeArtifactCache implements RuntimeArtifactCache {
    private static final String CACHE_BASE = "/cache";

    @Override
    public ImmutableList<Path> fetchArtifacts(
      com.google.idea.blaze.common.Label label,
      List<? extends OutputArtifact> artifacts,
      BlazeContext context,
      RuntimeArtifactKind artifactKind) {
      return artifacts.stream()
          .map(a -> Paths.get(CACHE_BASE, a.getBazelOutRelativePath()))
          .collect(toImmutableList());
    }
  }

  private static class AitBepBuilder {
    private static final String CONFIG_ID = "config-id";

    private final TemporaryFolder temporaryFolder;
    private final HashFunction hasher = Hashing.goodFastHash(16);

    private final List<BuildEvent> events = new ArrayList<>();

    public AitBepBuilder(TemporaryFolder folder) {
      temporaryFolder = folder;
      events.add(started(UUID.randomUUID()));
      events.add(configuration(CONFIG_ID, MNEMONIC));
    }

    public AitBepBuilder addTargetComplete(
        String label, String manifestResource, List<FileArtifact> apks) throws IOException {
      HashCode labelHash = hasher.hashString(label, Charsets.UTF_8);

      String manifestName = "AndroidManifest" + labelHash + ".xml";
      FileArtifact manifest =
          new FileArtifact(
              BIN_PREFIXES, manifestName, newMergedManifestXml(manifestName, manifestResource));

      List<Artifact> apkArtifacts =
          apks.stream().map(TestUtil::toArtifact).collect(Collectors.toList());

      AndroidDeployInfo deployInfo =
          AndroidDeployInfo.newBuilder()
              .setMergedManifest(TestUtil.toArtifact(manifest))
              .addAllApksToDeploy(apkArtifacts)
              .build();

      String deployInfoPbFileName = labelHash + ".deployinfo.pb";
      FileArtifact deployInfoPb =
          new FileArtifact(
              BIN_PREFIXES, deployInfoPbFileName, temporaryFolder.newFile(deployInfoPbFileName));
      try (OutputStream os = new FileOutputStream(deployInfoPb.file)) {
        deployInfo.writeTo(os);
      }

      List<FileArtifact> filePaths = new ArrayList<>();
      filePaths.add(deployInfoPb);
      filePaths.add(manifest);

      // generate some random name for the fileset
      String infoFileset = "fileset-" + labelHash + "1";
      String apkFileset = "fileset-" + labelHash + "2";
      events.add(setOfFiles(filePaths, infoFileset, ImmutableList.of()));
      events.add(setOfFiles(apks, apkFileset, ImmutableList.of()));
      events.add(
          targetComplete(
              label,
              CONFIG_ID,
              ImmutableList.of(
                  outputGroup("default", ImmutableList.of(apkFileset)),
                  outputGroup("android-deploy-info", ImmutableList.of(infoFileset)))));

      return this;
    }

    private File newMergedManifestXml(String name, String path) throws IOException {
      File file = temporaryFolder.newFile(name);
      Resources.asByteSource(Resources.getResource(path))
          .copyTo(com.google.common.io.Files.asByteSink(file));
      return file;
    }

    public ParsedBepOutput build() throws BuildEventStreamException, IOException {
      return parsedBep(events);
    }
  }
}
