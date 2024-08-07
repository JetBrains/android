/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableCollection;
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
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.TargetConfiguredId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.TestResultId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.Configuration;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.NamedSetOfFiles;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.OutputGroup;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.TargetComplete;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.TargetConfigured;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.TestResult;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules.RuleTypes;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult.TestStatus;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
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
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Unit tests for {@link BuildEventProtocolOutputReader}. */
@RunWith(Parameterized.class)
public class BuildEventProtocolOutputReaderTest extends BlazeTestCase {

  @Parameters
  public static ImmutableList<Object[]> data() {
    return ImmutableList.of(new Object[] {true}, new Object[] {false});
  }

  // BEP file URI format changed from 'file://[abs_path]' to 'file:[abs_path]'
  @Parameter public boolean useOldFormatFileUri = false;

  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

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
    parserEp.registerExtension(new OutputArtifactParser.LocalFileParser());
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

    ImmutableSet<OutputArtifact> parsedFilenames =
        ParsedBepOutput.parseBepArtifacts(asInputStream(events))
            .getAllOutputArtifacts(path -> true);

    assertThat(LocalFileArtifact.getLocalFiles(parsedFilenames))
        .containsExactlyElementsIn(filePaths.stream().map(File::new).toArray())
        .inOrder();
  }

  @Test
  public void parseAllOutputsWithFilter_singleFileEvent_returnsFilteredOutputs() throws Exception {
    Predicate<String> filter = path -> path.endsWith(".py");
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

    ImmutableSet<OutputArtifact> parsedFilenames =
        ParsedBepOutput.parseBepArtifacts(asInputStream(events)).getAllOutputArtifacts(filter);

    assertThat(LocalFileArtifact.getLocalFiles(parsedFilenames))
        .containsExactly(new File("/usr/local/lib/File.py"));
  }

  @Test
  public void parseAllOutputs_nonFileEvent_returnsEmptyList() throws Exception {
    BuildEvent.Builder targetFinishedEvent =
        BuildEvent.newBuilder()
            .setCompleted(BuildEventStreamProtos.TargetComplete.getDefaultInstance());

    ImmutableSet<OutputArtifact> parsedFilenames =
        ParsedBepOutput.parseBepArtifacts(asInputStream(targetFinishedEvent))
            .getAllOutputArtifacts(path -> true);

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

    ImmutableSet<OutputArtifact> parsedFilenames =
        ParsedBepOutput.parseBepArtifacts(asInputStream(events))
            .getAllOutputArtifacts(path -> true);

    assertThat(LocalFileArtifact.getLocalFiles(parsedFilenames))
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

    ImmutableSet<OutputArtifact> parsedFilenames =
        ParsedBepOutput.parseBepArtifacts(asInputStream(events))
            .getAllOutputArtifacts(path -> true);

    assertThat(LocalFileArtifact.getLocalFiles(parsedFilenames))
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

    ImmutableSet<OutputArtifact> parsedFilenames =
        ParsedBepOutput.parseBepArtifacts(asInputStream(events))
            .getAllOutputArtifacts(path -> true);

    assertThat(LocalFileArtifact.getLocalFiles(parsedFilenames))
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
        ParsedBepOutput.parseBepArtifacts(asInputStream(events))
            .getDirectArtifactsForTarget(Label.create("//some:target"), path -> true);

    assertThat(LocalFileArtifact.getLocalFiles(parsedFilenames))
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
        ParsedBepOutput.parseBepArtifacts(asInputStream(events))
            .getDirectArtifactsForTarget(Label.create("//some:target"), path -> true);

    assertThat(LocalFileArtifact.getLocalFiles(parsedFilenames))
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
        ParsedBepOutput.parseBepArtifacts(asInputStream(events))
            .getOutputGroupArtifacts("group-name", path -> true);

    assertThat(LocalFileArtifact.getLocalFiles(parsedFilenames))
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
        ParsedBepOutput.parseBepArtifacts(asInputStream(events))
            .getOutputGroupArtifacts("group-1", path -> true);

    assertThat(LocalFileArtifact.getLocalFiles(parsedFilenames))
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
        ParsedBepOutput.parseBepArtifacts(asInputStream(events)).getFullArtifactData();
    ImmutableList<OutputArtifact> outputs =
        outputData.values().stream().map(d -> d.artifact).collect(toImmutableList());

    assertThat(LocalFileArtifact.getLocalFiles(outputs)).containsExactlyElementsIn(allOutputs);
  }

  @Test
  public void testStatusEnum_handlesAllProtoEnumValues() {
    ImmutableSet<String> protoValues =
        EnumSet.allOf(BuildEventStreamProtos.TestStatus.class).stream()
            .map(Enum::name)
            .filter(name -> !name.equals(BuildEventStreamProtos.TestStatus.UNRECOGNIZED.name()))
            .collect(toImmutableSet());
    ImmutableSet<String> handledValues =
        EnumSet.allOf(TestStatus.class).stream().map(Enum::name).collect(toImmutableSet());

    assertThat(protoValues).containsExactlyElementsIn(handledValues);
  }

  @Test
  public void parseTestResults_singleEvent_returnsTestResults() throws Exception {
    Label label = Label.create("//java/com/google:unit_tests");
    BuildEventStreamProtos.TestStatus status = BuildEventStreamProtos.TestStatus.FAILED;
    ImmutableList<String> filePaths = ImmutableList.of("/usr/local/tmp/_cache/test_result.xml");
    BuildEvent.Builder event = testResultEvent(label.toString(), status, filePaths);

    BlazeTestResults results =
        BuildEventProtocolOutputReader.parseTestResults(asInputStream(event));

    assertThat(results.perTargetResults.keySet()).containsExactly(label);
    assertThat(results.perTargetResults.get(label)).hasSize(1);
    BlazeTestResult result = results.perTargetResults.get(label).iterator().next();
    assertThat(result.getTestStatus()).isEqualTo(TestStatus.FAILED);
    assertThat(getOutputXmlFiles(result))
        .containsExactly(new File("/usr/local/tmp/_cache/test_result.xml"));
  }

  @Test
  public void parseTestResults_singleTestEventWithTargetConfigured_resultsIncludeTargetKind()
      throws Exception {
    Label label = Label.create("//java/com/google:unit_tests");
    BuildEventStreamProtos.TestStatus status = BuildEventStreamProtos.TestStatus.FAILED;
    ImmutableList<String> filePaths = ImmutableList.of("/usr/local/tmp/_cache/test_result.xml");
    InputStream events =
        asInputStream(
            targetConfiguredEvent(label.toString(), "sh_test rule"),
            testResultEvent(label.toString(), status, filePaths));

    BlazeTestResults results = BuildEventProtocolOutputReader.parseTestResults(events);

    assertThat(results.perTargetResults.keySet()).containsExactly(label);
    assertThat(results.perTargetResults.get(label)).hasSize(1);
    BlazeTestResult result = results.perTargetResults.get(label).iterator().next();
    assertThat(result.getTargetKind()).isEqualTo(RuleTypes.SH_TEST.getKind());
    assertThat(result.getTestStatus()).isEqualTo(TestStatus.FAILED);
    assertThat(getOutputXmlFiles(result))
        .containsExactly(new File("/usr/local/tmp/_cache/test_result.xml"));
  }

  @Test
  public void parseTestResults_singleTestEventWithTargetCompleted_resultsIncludeTargetKind()
      throws Exception {
    Label label = Label.create("//java/com/google:unit_tests");
    BuildEventStreamProtos.TestStatus status = BuildEventStreamProtos.TestStatus.FAILED;
    ImmutableList<String> filePaths = ImmutableList.of("/usr/local/tmp/_cache/test_result.xml");
    InputStream events =
        asInputStream(
            targetCompletedEvent(label.toString(), "sh_test rule"),
            testResultEvent(label.toString(), status, filePaths));

    BlazeTestResults results = BuildEventProtocolOutputReader.parseTestResults(events);

    assertThat(results.perTargetResults.keySet()).containsExactly(label);
    assertThat(results.perTargetResults.get(label)).hasSize(1);
    BlazeTestResult result = results.perTargetResults.get(label).iterator().next();
    assertThat(result.getTargetKind()).isEqualTo(RuleTypes.SH_TEST.getKind());
    assertThat(result.getTestStatus()).isEqualTo(TestStatus.FAILED);
    assertThat(getOutputXmlFiles(result))
        .containsExactly(new File("/usr/local/tmp/_cache/test_result.xml"));
  }

  @Test
  public void parseTestResults_multipleTargetKindSources_resultsIncludeCorrectTargetKind()
      throws Exception {
    Label label = Label.create("//java/com/google:unit_tests");
    BuildEventStreamProtos.TestStatus status = BuildEventStreamProtos.TestStatus.FAILED;
    ImmutableList<String> filePaths = ImmutableList.of("/usr/local/tmp/_cache/test_result.xml");
    InputStream events =
        asInputStream(
            targetConfiguredEvent(label.toString(), "sh_test rule"),
            targetCompletedEvent(label.toString(), "sh_test rule"),
            testResultEvent(label.toString(), status, filePaths));

    BlazeTestResults results = BuildEventProtocolOutputReader.parseTestResults(events);

    assertThat(results.perTargetResults.keySet()).containsExactly(label);
    assertThat(results.perTargetResults.get(label)).hasSize(1);
    BlazeTestResult result = results.perTargetResults.get(label).iterator().next();
    assertThat(result.getTargetKind()).isEqualTo(RuleTypes.SH_TEST.getKind());
    assertThat(result.getTestStatus()).isEqualTo(TestStatus.FAILED);
    assertThat(getOutputXmlFiles(result))
        .containsExactly(new File("/usr/local/tmp/_cache/test_result.xml"));
  }

  @Test
  public void parseTestResults_singleEvent_ignoresNonXmlOutputFiles() throws Exception {
    Label label = Label.create("//java/com/google:unit_tests");
    BuildEventStreamProtos.TestStatus status = BuildEventStreamProtos.TestStatus.FAILED;
    ImmutableList<String> filePaths =
        ImmutableList.of(
            "/usr/local/tmp/_cache/test_result.xml",
            "/usr/local/tmp/_cache/test_result.log",
            "/usr/local/tmp/other_output_file");
    BuildEvent.Builder event = testResultEvent(label.toString(), status, filePaths);

    BlazeTestResults results =
        BuildEventProtocolOutputReader.parseTestResults(asInputStream(event));

    BlazeTestResult result = results.perTargetResults.get(label).iterator().next();
    assertThat(getOutputXmlFiles(result))
        .containsExactly(new File("/usr/local/tmp/_cache/test_result.xml"));
  }

  @Test
  public void parseTestResults_singleTargetWithMultipleEvents_returnsTestResults()
      throws Exception {
    Label label = Label.create("//java/com/google:unit_tests");

    ImmutableList<BuildEvent.Builder> events =
        ImmutableList.of(
            configuration("config-id", "k8-opt"),
            targetComplete(label.toString(), "config-id", ImmutableList.of()),
            testResultEvent(
                label.toString(),
                BuildEventStreamProtos.TestStatus.PASSED,
                ImmutableList.of("/usr/local/tmp/_cache/shard1_of_2.xml")),
            testResultEvent(
                label.toString(),
                BuildEventStreamProtos.TestStatus.FAILED,
                ImmutableList.of("/usr/local/tmp/_cache/shard2_of_2.xml")));

    BlazeTestResults results =
        BuildEventProtocolOutputReader.parseTestResults(asInputStream(events));

    ImmutableCollection<BlazeTestResult> targetResults = results.perTargetResults.get(label);
    assertThat(targetResults).hasSize(2);

    Iterator<BlazeTestResult> iterator = targetResults.iterator();
    BlazeTestResult result1 = iterator.next();
    BlazeTestResult result2 = iterator.next();

    assertThat(result1.getTestStatus()).isEqualTo(TestStatus.PASSED);
    assertThat(result2.getTestStatus()).isEqualTo(TestStatus.FAILED);
    assertThat(getOutputXmlFiles(result1))
        .containsExactly(new File("/usr/local/tmp/_cache/shard1_of_2.xml"));
    assertThat(getOutputXmlFiles(result2))
        .containsExactly(new File("/usr/local/tmp/_cache/shard2_of_2.xml"));
  }

  @Test
  public void parseTestResults_multipleEvents_returnsAllResults() throws Exception {
    BuildEvent.Builder test1 =
        testResultEvent(
            "//java/com/google:Test1",
            BuildEventStreamProtos.TestStatus.PASSED,
            ImmutableList.of("/usr/local/tmp/_cache/test_result.xml"));
    BuildEvent.Builder test2 =
        testResultEvent(
            "//java/com/google:Test2",
            BuildEventStreamProtos.TestStatus.INCOMPLETE,
            ImmutableList.of("/usr/local/tmp/_cache/second_result.xml"));

    BlazeTestResults results =
        BuildEventProtocolOutputReader.parseTestResults(asInputStream(test1, test2));

    assertThat(results.perTargetResults).hasSize(2);
    assertThat(results.perTargetResults.get(Label.create("//java/com/google:Test1"))).hasSize(1);
    assertThat(results.perTargetResults.get(Label.create("//java/com/google:Test2"))).hasSize(1);
    BlazeTestResult result1 =
        results.perTargetResults.get(Label.create("//java/com/google:Test1")).iterator().next();
    assertThat(result1.getTestStatus()).isEqualTo(TestStatus.PASSED);
    assertThat(getOutputXmlFiles(result1))
        .containsExactly(new File("/usr/local/tmp/_cache/test_result.xml"));
    BlazeTestResult result2 =
        results.perTargetResults.get(Label.create("//java/com/google:Test2")).iterator().next();
    assertThat(result2.getTestStatus()).isEqualTo(TestStatus.INCOMPLETE);
    assertThat(getOutputXmlFiles(result2))
        .containsExactly(new File("/usr/local/tmp/_cache/second_result.xml"));
  }

  private static ImmutableList<File> getOutputXmlFiles(BlazeTestResult result) {
    return LocalFileArtifact.getLocalFiles(result.getOutputXmlFiles());
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

  private static BuildEvent.Builder targetConfiguredEvent(String label, String targetKind) {
    return BuildEvent.newBuilder()
        .setId(
            BuildEventId.newBuilder()
                .setTargetConfigured(TargetConfiguredId.newBuilder().setLabel(label)))
        .setConfigured(TargetConfigured.newBuilder().setTargetKind(targetKind));
  }

  private BuildEvent.Builder targetCompletedEvent(String label, String targetKind) {
    return BuildEvent.newBuilder()
        .setId(
            BuildEventId.newBuilder()
                .setTargetCompleted(TargetCompletedId.newBuilder().setLabel(label)))
        .setCompleted(TargetComplete.newBuilder().setTargetKind(targetKind));
  }

  private BuildEvent.Builder testResultEvent(
      String label, BuildEventStreamProtos.TestStatus status, List<String> filePaths) {
    return BuildEvent.newBuilder()
        .setId(BuildEventId.newBuilder().setTestResult(TestResultId.newBuilder().setLabel(label)))
        .setTestResult(
            TestResult.newBuilder()
                .setStatus(status)
                .addAllTestActionOutput(
                    filePaths.stream().map(this::toFileEvent).collect(toImmutableList())));
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
