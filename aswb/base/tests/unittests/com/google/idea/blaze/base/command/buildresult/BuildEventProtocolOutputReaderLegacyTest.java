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
package com.google.idea.blaze.base.command.buildresult;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.ConfigurationId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.NamedSetOfFilesId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.TargetCompletedId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.Configuration;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.NamedSetOfFiles;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.OutputGroup;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.TargetComplete;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.command.buildresult.bepparser.BepArtifactData;
import com.google.idea.blaze.base.command.buildresult.bepparser.BepParser;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider;
import com.google.idea.blaze.base.command.buildresult.bepparser.OutputArtifactParser;
import com.google.idea.blaze.base.command.buildresult.bepparser.ParsedBepOutput;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.TestOnly;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Unit tests for {@link BuildEventProtocolOutputReader}. */
@RunWith(Parameterized.class)
public class BuildEventProtocolOutputReaderLegacyTest extends BlazeTestCase {

  @Parameters
  public static ImmutableList<Object[]> data() {
    return ImmutableList.of(new Object[] {true}, new Object[] {false});
  }

  // BEP file URI format changed from 'file://[abs_path]' to 'file:[abs_path]'
  @Parameter public boolean useOldFormatFileUri = false;

  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  /** Returns the set of artifacts directly produced by the given target. */
  @TestOnly
  public static ImmutableSet<OutputArtifact> getOutputGroupTargetArtifacts(ImmutableMap<String, ParsedBepOutput.Legacy.FileSet> fileSets,
                                                                           String outputGroup,
                                                                           String label) {
    return fileSets.values().stream()
      .filter(f -> f.getTargets().contains(label) && f.getOutputGroups().contains(outputGroup))
      .map(ParsedBepOutput.Legacy.FileSet::getParsedOutputs)
      .flatMap(List::stream)
      .distinct()
      .collect(toImmutableSet());
  }

  @TestOnly
  public static ImmutableList<OutputArtifact> getOutputGroupArtifacts(ImmutableMap<String, ParsedBepOutput.Legacy.FileSet> fileSets, String outputGroup) {
    return fileSets.values().stream()
        .filter(f -> f.getOutputGroups().contains(outputGroup))
        .map(ParsedBepOutput.Legacy.FileSet::getParsedOutputs)
        .flatMap(List::stream)
        .distinct()
        .collect(toImmutableList());
  }

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);
    ExtensionPointImpl<Kind.Provider> ep =
        registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    ep.registerExtension(new GenericBlazeRules());
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(QuerySyncSettings.class, new QuerySyncSettings());

    ExtensionPointImpl<OutputArtifactParser> parserEp =
        registerExtensionPoint(OutputArtifactParser.EP_NAME, OutputArtifactParser.class);
    parserEp.registerExtension(new LocalFileParser());
  }

  @Test
  public void parseAllOutputs_singleTargetEvents_returnsAllOutputs() throws Exception {
    ImmutableList<String> filePaths =
        ImmutableList.of(
            "/usr/local/lib/File.py", "/usr/bin/python2.7", "/usr/local/home/script.sh");
    ImmutableList<BuildEvent.Builder> events =
        ImmutableList.of(
            configuration("config-id", "k8-opt"),
            setOfFiles(filePaths, "set-id"),
            targetComplete(
                "//some:target",
                "config-id",
                ImmutableList.of(outputGroup("name", ImmutableList.of("set-id")))));

    Set<OutputArtifact> parsedFilenames =
        BepParser.parseBepArtifactsForLegacySync(BuildEventStreamProvider.fromInputStream(asInputStream(events)), null)
            .getAllOutputArtifactsForTesting();

    assertThat(LocalFileArtifact.getLocalFilesForLegacySync(parsedFilenames))
        .containsExactlyElementsIn(filePaths.stream().map(File::new).toArray())
        .inOrder();
  }

  @Test
  public void parseAllOutputs_nonFileEvent_returnsEmptyList() throws Exception {
    BuildEvent.Builder targetFinishedEvent =
        BuildEvent.newBuilder()
            .setCompleted(TargetComplete.getDefaultInstance());

    Set<OutputArtifact> parsedFilenames =
        BepParser.parseBepArtifactsForLegacySync(BuildEventStreamProvider.fromInputStream(asInputStream(targetFinishedEvent)), null)
            .getAllOutputArtifactsForTesting();

    assertThat(parsedFilenames).isEmpty();
  }

  @Test
  public void parseAllOutputs_singleTargetEventsPlusExtras_returnsAllOutputs() throws Exception {
    ImmutableList<String> filePaths =
        ImmutableList.of(
            "/usr/local/lib/Provider.java",
            "/usr/local/home/Executor.java",
            "/google/code/script.sh");
    ImmutableList<BuildEvent.Builder> events =
        ImmutableList.of(
            BuildEvent.newBuilder()
                .setStarted(BuildEventStreamProtos.BuildStarted.getDefaultInstance()),
            BuildEvent.newBuilder()
                .setProgress(BuildEventStreamProtos.Progress.getDefaultInstance()),
            configuration("config-id", "k8-opt"),
            setOfFiles(filePaths, "set-id"),
            targetComplete(
                "//some:target",
                "config-id",
                ImmutableList.of(outputGroup("name", ImmutableList.of("set-id")))));

    Set<OutputArtifact> parsedFilenames =
        BepParser.parseBepArtifactsForLegacySync(BuildEventStreamProvider.fromInputStream(asInputStream(events)), null)
            .getAllOutputArtifactsForTesting();

    assertThat(LocalFileArtifact.getLocalFilesForLegacySync(parsedFilenames))
        .containsExactlyElementsIn(filePaths.stream().map(File::new).toArray())
        .inOrder();
  }

  @Test
  public void parseAllOutputs_singleTargetWithMultipleFileSets_returnsAllOutputs()
      throws Exception {
    ImmutableList<String> fileSet1 =
        ImmutableList.of(
            "/usr/local/lib/Provider.java",
            "/usr/local/home/Executor.java",
            "/google/code/script.sh");

    ImmutableList<String> fileSet2 =
        ImmutableList.of(
            "/usr/local/code/ParserTest.java",
            "/usr/local/code/action_output.bzl",
            "/usr/genfiles/BUILD.bazel");

    ImmutableList<BuildEvent.Builder> events =
        ImmutableList.of(
            BuildEvent.newBuilder()
                .setStarted(BuildEventStreamProtos.BuildStarted.getDefaultInstance()),
            BuildEvent.newBuilder()
                .setProgress(BuildEventStreamProtos.Progress.getDefaultInstance()),
            configuration("config-id", "k8-opt"),
            setOfFiles(fileSet1, "set-1"),
            setOfFiles(fileSet2, "set-2"),
            targetComplete(
                "//some:target",
                "config-id",
                ImmutableList.of(
                    outputGroup("name1", ImmutableList.of("set-1")),
                    outputGroup("name2", ImmutableList.of("set-2")))));

    ImmutableList<File> allFiles =
        ImmutableList.<String>builder().addAll(fileSet1).addAll(fileSet2).build().stream()
            .map(File::new)
            .collect(toImmutableList());

    Set<OutputArtifact> parsedFilenames =
        BepParser.parseBepArtifactsForLegacySync(BuildEventStreamProvider.fromInputStream(asInputStream(events)), null)
            .getAllOutputArtifactsForTesting();

    assertThat(LocalFileArtifact.getLocalFilesForLegacySync(parsedFilenames))
        .containsExactlyElementsIn(allFiles)
        .inOrder();
  }

  @Test
  public void parseAllOutputs_streamWithDuplicateFiles_returnsUniqueOutputs() throws Exception {
    ImmutableList<String> fileSet1 = ImmutableList.of("/usr/out/genfiles/foo.pb.h");

    ImmutableList<String> fileSet2 =
        ImmutableList.of("/usr/out/genfiles/foo.pb.h", "/usr/out/genfiles/foo.proto.h");

    ImmutableList<BuildEvent.Builder> events =
        ImmutableList.of(
            BuildEvent.newBuilder()
                .setStarted(BuildEventStreamProtos.BuildStarted.getDefaultInstance()),
            BuildEvent.newBuilder()
                .setProgress(BuildEventStreamProtos.Progress.getDefaultInstance()),
            configuration("config-id", "k8-opt"),
            setOfFiles(fileSet1, "set-1"),
            setOfFiles(fileSet2, "set-2"),
            targetComplete(
                "//some:target",
                "config-id",
                ImmutableList.of(
                    outputGroup("name1", ImmutableList.of("set-1")),
                    outputGroup("name2", ImmutableList.of("set-2")))));

    ImmutableList<File> allFiles =
        ImmutableSet.of("/usr/out/genfiles/foo.pb.h", "/usr/out/genfiles/foo.proto.h").stream()
            .map(File::new)
            .collect(toImmutableList());

    Set<OutputArtifact> parsedFilenames =
        BepParser.parseBepArtifactsForLegacySync(BuildEventStreamProvider.fromInputStream(asInputStream(events)), null)
            .getAllOutputArtifactsForTesting();

    assertThat(LocalFileArtifact.getLocalFilesForLegacySync(parsedFilenames))
        .containsExactlyElementsIn(allFiles)
        .inOrder();
  }

  @Test
  public void parseArtifactsForTarget_singleTarget_returnsTargetOutputs() throws Exception {
    ImmutableList<String> fileSet =
        ImmutableList.of("/usr/out/genfiles/foo.pb.h", "/usr/out/genfiles/foo.proto.h");

    ImmutableList<BuildEvent.Builder> events =
        ImmutableList.of(
            BuildEvent.newBuilder()
                .setStarted(BuildEventStreamProtos.BuildStarted.getDefaultInstance()),
            BuildEvent.newBuilder()
                .setProgress(BuildEventStreamProtos.Progress.getDefaultInstance()),
            configuration("config-id", "k8-opt"),
            setOfFiles(fileSet, "set-id"),
            targetComplete(
                "//some:target",
                "config-id",
                ImmutableList.of(outputGroup("group-name", ImmutableList.of("set-id")))));

    ImmutableList<File> allFiles =
        ImmutableSet.of("/usr/out/genfiles/foo.pb.h", "/usr/out/genfiles/foo.proto.h").stream()
            .map(File::new)
            .collect(toImmutableList());

    ImmutableSet<OutputArtifact> parsedFilenames =
        getOutputGroupTargetArtifacts(
              BepParser.parseBepArtifactsForLegacySync(BuildEventStreamProvider.fromInputStream(asInputStream(events)), null).fileSets, "group-name", "//some:target");

    assertThat(LocalFileArtifact.getLocalFilesForLegacySync(parsedFilenames))
        .containsExactlyElementsIn(allFiles)
        .inOrder();
  }

  @Test
  public void parseArtifactsForTarget_twoTargets_returnsCorrectTargetOutputs() throws Exception {
    ImmutableList<String> targetFileSet =
        ImmutableList.of("/usr/out/genfiles/foo.pb.h", "/usr/out/genfiles/foo.proto.h");
    ImmutableList<String> otherTargetFileSet =
        ImmutableList.of(
            "/usr/local/lib/File.py", "/usr/bin/python2.7", "/usr/local/home/script.sh");

    ImmutableList<BuildEvent.Builder> events =
        ImmutableList.of(
            BuildEvent.newBuilder()
                .setStarted(BuildEventStreamProtos.BuildStarted.getDefaultInstance()),
            BuildEvent.newBuilder()
                .setProgress(BuildEventStreamProtos.Progress.getDefaultInstance()),
            configuration("config-id", "k8-opt"),
            setOfFiles(targetFileSet, "target-set"),
            setOfFiles(otherTargetFileSet, "other-set"),
            targetComplete(
                "//some:target",
                "config-id",
                ImmutableList.of(outputGroup("group-name", ImmutableList.of("target-set")))),
            targetComplete(
                "//other:target",
                "config-id",
                ImmutableList.of(outputGroup("group-name", ImmutableList.of("other-set")))));

    ImmutableList<File> allFiles =
        ImmutableSet.of("/usr/out/genfiles/foo.pb.h", "/usr/out/genfiles/foo.proto.h").stream()
            .map(File::new)
            .collect(toImmutableList());

    ImmutableSet<OutputArtifact> parsedFilenames =
        getOutputGroupTargetArtifacts(
              BepParser.parseBepArtifactsForLegacySync(BuildEventStreamProvider.fromInputStream(asInputStream(events)), null).fileSets, "group-name", "//some:target");

    assertThat(LocalFileArtifact.getLocalFilesForLegacySync(parsedFilenames))
        .containsExactlyElementsIn(allFiles)
        .inOrder();
  }

  @Test
  public void parseAllArtifactsInOutputGroups_oneGroup_returnsAllOutputs() throws Exception {
    ImmutableList<String> fileSet1 =
        ImmutableList.of("/usr/out/genfiles/foo.pb.h", "/usr/out/genfiles/foo.proto.h");
    ImmutableList<String> fileSet2 =
        ImmutableList.of(
            "/usr/local/lib/File.py", "/usr/bin/python2.7", "/usr/local/home/script.sh");

    ImmutableList<BuildEvent.Builder> events =
        ImmutableList.of(
            BuildEvent.newBuilder()
                .setStarted(BuildEventStreamProtos.BuildStarted.getDefaultInstance()),
            BuildEvent.newBuilder()
                .setProgress(BuildEventStreamProtos.Progress.getDefaultInstance()),
            configuration("config-id", "k8-opt"),
            setOfFiles(fileSet1, "set-1"),
            setOfFiles(fileSet2, "set-2"),
            targetComplete(
                "//some:target",
                "config-id",
                ImmutableList.of(outputGroup("group-name", ImmutableList.of("set-1")))),
            targetComplete(
                "//other:target",
                "config-id",
                ImmutableList.of(outputGroup("group-name", ImmutableList.of("set-2")))));

    ImmutableList<File> allFiles =
        ImmutableSet.of(
                "/usr/out/genfiles/foo.pb.h",
                "/usr/out/genfiles/foo.proto.h",
                "/usr/local/lib/File.py",
                "/usr/bin/python2.7",
                "/usr/local/home/script.sh")
            .stream()
            .map(File::new)
            .collect(toImmutableList());

    ImmutableList<OutputArtifact> parsedFilenames =
        getOutputGroupArtifacts(BepParser.parseBepArtifactsForLegacySync(BuildEventStreamProvider.fromInputStream(asInputStream(events)), null).fileSets,
                                     "group-name");

    assertThat(LocalFileArtifact.getLocalFilesForLegacySync(parsedFilenames))
        .containsExactlyElementsIn(allFiles)
        .inOrder();
  }

  @Test
  public void parseAllArtifactsInOutputGroups_oneOfTwoGroups_returnsCorrectOutputs()
      throws Exception {
    ImmutableList<String> fileSet1 =
        ImmutableList.of("/usr/out/genfiles/foo.pb.h", "/usr/out/genfiles/foo.proto.h");
    ImmutableList<String> fileSet2 =
        ImmutableList.of(
            "/usr/local/lib/File.py", "/usr/bin/python2.7", "/usr/local/home/script.sh");

    ImmutableList<BuildEvent.Builder> events =
        ImmutableList.of(
            BuildEvent.newBuilder()
                .setStarted(BuildEventStreamProtos.BuildStarted.getDefaultInstance()),
            BuildEvent.newBuilder()
                .setProgress(BuildEventStreamProtos.Progress.getDefaultInstance()),
            configuration("config-id", "k8-opt"),
            setOfFiles(fileSet1, "set-1"),
            setOfFiles(fileSet2, "set-2"),
            targetComplete(
                "//some:target",
                "config-id",
                ImmutableList.of(outputGroup("group-1", ImmutableList.of("set-1")))),
            targetComplete(
                "//other:target",
                "config-id",
                ImmutableList.of(outputGroup("group-2", ImmutableList.of("set-2")))));

    ImmutableList<File> allFiles =
        ImmutableSet.of("/usr/out/genfiles/foo.pb.h", "/usr/out/genfiles/foo.proto.h").stream()
            .map(File::new)
            .collect(toImmutableList());

    ImmutableList<OutputArtifact> parsedFilenames =
        getOutputGroupArtifacts(BepParser.parseBepArtifactsForLegacySync(BuildEventStreamProvider.fromInputStream(asInputStream(events)), null).fileSets,
                                     "group-1");

    assertThat(LocalFileArtifact.getLocalFilesForLegacySync(parsedFilenames))
        .containsExactlyElementsIn(allFiles)
        .inOrder();
  }

  @Test
  public void getFullArtifactData_returnsTransitiveOutputs() throws Exception {
    ImmutableList<String> fileSet1 =
        ImmutableList.of("/usr/out/genfiles/foo.pb.h", "/usr/out/genfiles/foo.proto.h");
    ImmutableList<String> fileSet2 =
        ImmutableList.of(
            "/usr/local/lib/File.py", "/usr/bin/python2.7", "/usr/local/home/script.sh");

    ImmutableList<BuildEvent.Builder> events =
        ImmutableList.of(
            BuildEvent.newBuilder()
                .setStarted(BuildEventStreamProtos.BuildStarted.getDefaultInstance()),
            BuildEvent.newBuilder()
                .setProgress(BuildEventStreamProtos.Progress.getDefaultInstance()),
            configuration("config-id", "k8-opt"),
            setOfFiles(fileSet1, "set-1", ImmutableList.of("set-2")),
            setOfFiles(fileSet2, "set-2"),
            targetComplete(
                "//some:target",
                "config-id",
                ImmutableList.of(outputGroup("group-1", ImmutableList.of("set-1")))));

    ImmutableList<File> allOutputs =
        Streams.concat(fileSet1.stream(), fileSet2.stream())
            .map(File::new)
            .collect(toImmutableList());

    ImmutableMap<String, BepArtifactData> outputData =
        BepParser.parseBepArtifactsForLegacySync(BuildEventStreamProvider.fromInputStream(asInputStream(events)), null).getFullArtifactData();
    ImmutableList<OutputArtifact> outputs =
        outputData.values().stream().map(d -> d.artifact).collect(toImmutableList());

    assertThat(LocalFileArtifact.getLocalFilesForLegacySync(outputs)).containsExactlyElementsIn(allOutputs);
  }

  private static InputStream asInputStream(BuildEvent.Builder... events) throws Exception {
    return asInputStream(Arrays.asList(events));
  }

  private static InputStream asInputStream(Iterable<BuildEvent.Builder> events) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    for (BuildEvent.Builder event : events) {
      event.build().writeDelimitedTo(output);
    }
    return new ByteArrayInputStream(output.toByteArray());
  }
  private BuildEvent.Builder targetComplete(
      String label, String configId, List<OutputGroup> outputGroups) {
    return BuildEvent.newBuilder()
        .setId(
            BuildEventId.newBuilder()
                .setTargetCompleted(
                    TargetCompletedId.newBuilder()
                        .setConfiguration(ConfigurationId.newBuilder().setId(configId).build())
                        .setLabel(label)))
        .setCompleted(TargetComplete.newBuilder().addAllOutputGroup(outputGroups));
  }

  private OutputGroup outputGroup(String name, List<String> fileSets) {
    OutputGroup.Builder builder = OutputGroup.newBuilder().setName(name);
    fileSets.forEach(s -> builder.addFileSets(NamedSetOfFilesId.newBuilder().setId(s)));
    return builder.build();
  }

  private BuildEvent.Builder configuration(String name, String mnemonic) {
    return BuildEvent.newBuilder()
        .setId(BuildEventId.newBuilder().setConfiguration(ConfigurationId.newBuilder().setId(name)))
        .setConfiguration(Configuration.newBuilder().setMnemonic(mnemonic));
  }

  private BuildEvent.Builder setOfFiles(List<String> filePaths, String id) {
    return setOfFiles(filePaths, id, ImmutableList.of());
  }

  private BuildEvent.Builder setOfFiles(
      List<String> filePaths, String id, List<String> fileSetDeps) {
    return BuildEvent.newBuilder()
        .setId(BuildEventId.newBuilder().setNamedSet(NamedSetOfFilesId.newBuilder().setId(id)))
        .setNamedSetOfFiles(
            NamedSetOfFiles.newBuilder()
                .addAllFiles(filePaths.stream().map(this::toFileEvent).collect(toImmutableList()))
                .addAllFileSets(
                    fileSetDeps.stream()
                        .map(dep -> NamedSetOfFilesId.newBuilder().setId(dep).build())
                        .collect(toImmutableList())));
  }

  private BuildEventStreamProtos.File toFileEvent(String filePath) {
    return BuildEventStreamProtos.File.newBuilder()
        .setUri(fileUri(filePath))
        .setName(filePath)
        .build();
  }

  private String fileUri(String filePath) {
    return useOldFormatFileUri
        ? LocalFileSystem.PROTOCOL_PREFIX + filePath
        : new File(filePath).toURI().toString();
  }
}
