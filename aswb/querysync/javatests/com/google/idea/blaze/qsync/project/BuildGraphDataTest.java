/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.NOOP_CONTEXT;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.getQuerySummary;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.BlazeQueryParser;
import com.google.idea.blaze.qsync.testdata.BuildGraphs;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildGraphDataTest {

  private static final Path TEST_ROOT =
      Path.of("tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync");

  private static final Path TESTDATA_ROOT = TEST_ROOT.resolve("testdata");

  @Test
  public void testJavaLibraryNoDeps() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_NO_DEPS_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parse();
    assertThat(graph.allTargets())
        .containsExactly(Label.of("//" + TESTDATA_ROOT + "/nodeps:nodeps"));
    assertThat(graph.getAllSourceFiles())
        .containsExactly(
            TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java"),
            TESTDATA_ROOT.resolve("nodeps/BUILD"));
    assertThat(graph.getJavaSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java"));
    assertThat(graph.getAndroidSourceFiles()).isEmpty();
    assertThat(graph.getTargetOwners(TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java")))
        .containsExactly(Label.of("//" + TESTDATA_ROOT + "/nodeps:nodeps"));
    assertThat(graph.getFileDependencies(TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java")))
        .isEmpty();
    assertThat(graph.targetMap().get(Label.of("//" + TESTDATA_ROOT + "/nodeps:nodeps")).languages())
        .containsExactly(QuerySyncLanguage.JAVA);
  }

  @Test
  public void testJavaLibraryExternalDep() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parse();
    assertThat(
            graph.getFileDependencies(
                TESTDATA_ROOT.resolve("externaldep/TestClassExternalDep.java")))
        .containsExactly(Label.of("@@maven//:com.google.guava.guava"));
  }

  @Test
  public void testJavaLibraryInternalDep() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_INTERNAL_DEP_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parse();
    // Sanity check:
    assertThat(graph.getAllSourceFiles())
        .contains(TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java"));
    assertThat(
            graph.getFileDependencies(
                TESTDATA_ROOT.resolve("internaldep/TestClassInternalDep.java")))
        .isEmpty();
  }

  @Test
  public void testJavaLibraryTransientDep() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_TRANSITIVE_DEP_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parse();
    // Sanity check:
    assertThat(graph.getAllSourceFiles())
        .contains(TESTDATA_ROOT.resolve("externaldep/TestClassExternalDep.java"));
    assertThat(
            graph.getFileDependencies(
                TESTDATA_ROOT.resolve("transitivedep/TestClassTransitiveDep.java")))
        .containsExactly(Label.of("@@maven//:com.google.guava.guava"));
  }

  @Test
  public void testJavaLibraryProtoDep() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_PROTO_DEP_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parse();
        assertThat(
                        graph.getFileDependencies(
                                TESTDATA_ROOT.resolve("protodep/TestClassProtoDep.java")))
                .containsExactly(
                        Label.of("//" + TESTDATA_ROOT + "/protodep:proto_java_proto"),
                        Label.of("//" + TESTDATA_ROOT + "/protodep:indirect_proto_java_proto"),
                        Label.of("@@bazel_tools//tools/proto:java_toolchain"));

        Path protoSourceFilePath = TESTDATA_ROOT.resolve("protodep/testproto.proto");
        assertThat(graph.getProtoSourceFiles()).containsExactly(protoSourceFilePath);

        Collection<ProjectTarget> firstConsumingTargets =
                graph.getFirstReverseDepsOfType(
                        protoSourceFilePath, ImmutableSet.of("java_proto_library"));
        assertThat(firstConsumingTargets.stream().map(ProjectTarget::label))
                .containsExactly(
                        Label.of("//" + TESTDATA_ROOT + "/protodep:proto_java_proto"),
                        Label.of("//" + TESTDATA_ROOT + "/protodep:indirect_proto_java_proto"));
  }

  @Test
  public void testDoesDependencyPathContainRules() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
            getQuerySummary(TestData.DOES_DEPENDENCY_PATH_CONTAIN_RULES),
            NOOP_CONTEXT,
            ImmutableSet.of())
            .parse();

    assertThat(graph.doesDependencyPathContainRules(
        TESTDATA_ROOT.resolve("deppathkinds/testproto.proto"),
        TESTDATA_ROOT.resolve("deppathkinds/TestClassProtoDep.java"),
        ImmutableSet.of("java_proto_library")
    )).isTrue();

    assertThat(graph.doesDependencyPathContainRules(
        TESTDATA_ROOT.resolve("deppathkinds/testproto.proto"),
        TESTDATA_ROOT.resolve("deppathkinds/TestClassProtoDep.java"),
        ImmutableSet.of("java_lite_proto_library")
    )).isFalse();

    assertThat(graph.doesDependencyPathContainRules(
        TESTDATA_ROOT.resolve("deppathkinds/testproto.proto"),
        TESTDATA_ROOT.resolve("deppathkinds/TestClassProtoDep.java"),
        ImmutableSet.of("android_library")
    )).isTrue();

    assertThat(graph.doesDependencyPathContainRules(
        TESTDATA_ROOT.resolve("deppathkinds/testproto.proto"),
        TESTDATA_ROOT.resolve("deppathkinds/TestClassProtoDep.java"),
        ImmutableSet.of("java_library")
    )).isTrue();

    assertThat(graph.doesDependencyPathContainRules(
        TESTDATA_ROOT.resolve("deppathkinds/testproto.proto"),
        TESTDATA_ROOT.resolve("deppathkinds/AndroidOnlyFile.java"),
        ImmutableSet.of("android_library")
    )).isTrue();

    assertThat(graph.doesDependencyPathContainRules(
        TESTDATA_ROOT.resolve("deppathkinds/testproto.proto"),
        TESTDATA_ROOT.resolve("deppathkinds/AndroidOnlyFile.java"),
        ImmutableSet.of("java_library")
    )).isFalse();

    assertThat(graph.doesDependencyPathContainRules(
        TESTDATA_ROOT.resolve("deppathkinds/testproto.proto"),
        TESTDATA_ROOT.resolve("deppathkinds/AndroidUsingJava.java"),
        ImmutableSet.of("java_library")
    )).isTrue();

    assertThat(graph.doesDependencyPathContainRules(
        TESTDATA_ROOT.resolve("deppathkinds/testproto.proto"),
        TESTDATA_ROOT.resolve("deppathkinds/TestClassProtoDep.java"),
        ImmutableSet.of("proto_library")
    )).isTrue();
  }

  @Test
  public void testJavaLibraryMultiTargets() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_MULTI_TARGETS),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parse();
    assertThat(graph.allTargets())
        .containsExactly(
            Label.of("//" + TESTDATA_ROOT + "/multitarget:nodeps"),
            Label.of("//" + TESTDATA_ROOT + "/multitarget:externaldep"));
    // Sanity check:
    assertThat(graph.getJavaSourceFiles())
        .contains(TESTDATA_ROOT.resolve("multitarget/TestClassSingleTarget.java"));
    assertThat(
            graph.getTargetOwners(TESTDATA_ROOT.resolve("multitarget/TestClassMultiTarget.java")))
        .containsExactly(
            Label.of("//" + TESTDATA_ROOT + "/multitarget:nodeps"),
            Label.of("//" + TESTDATA_ROOT + "/multitarget:externaldep"));
    assertThat(
            graph.getFileDependencies(
                TESTDATA_ROOT.resolve("multitarget/TestClassMultiTarget.java")))
        .contains(Label.of("@@maven//:com.google.guava.guava"));
  }

  @Test
  public void testJavaLibraryExportingExternalTargets() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_EXPORTED_DEP_QUERY), NOOP_CONTEXT, ImmutableSet.of())
            .parse();
    Path sourceFile = TESTDATA_ROOT.resolve("exports/TestClassUsingExport.java");
    assertThat(graph.getJavaSourceFiles()).containsExactly(sourceFile);
    assertThat(graph.getFileDependencies(sourceFile))
        .containsExactly(Label.of("@@maven//:com.google.guava.guava"));
  }

  @Test
  public void testAndroidLibrary() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.ANDROID_LIB_QUERY), NOOP_CONTEXT, ImmutableSet.of())
            .parse();
    assertThat(graph.getAllSourceFiles())
        .containsExactly(
            TESTDATA_ROOT.resolve("android/TestAndroidClass.java"),
            TESTDATA_ROOT.resolve("android/BUILD"),
            TESTDATA_ROOT.resolve("android/AndroidManifest.xml"));
    assertThat(graph.getJavaSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("android/TestAndroidClass.java"));
    assertThat(graph.getAndroidSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("android/TestAndroidClass.java"));
    assertThat(graph.getTargetOwners(TESTDATA_ROOT.resolve("android/TestAndroidClass.java")))
        .containsExactly(Label.of("//" + TESTDATA_ROOT + "/android:android"));
    assertThat(graph.getFileDependencies(TESTDATA_ROOT.resolve("android/TestAndroidClass.java")))
        .isEmpty();
    assertThat(graph.projectDeps()).isEmpty();
  }

  @Test
  public void testProjectAndroidLibrariesWithAidlSource_areProjectDeps() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.ANDROID_AIDL_SOURCE_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parse();
    assertThat(graph.getAllSourceFiles())
        .containsExactly(
            TESTDATA_ROOT.resolve("aidl/TestAndroidAidlClass.java"),
            TESTDATA_ROOT.resolve("aidl/TestAidlService.aidl"),
            TESTDATA_ROOT.resolve("aidl/BUILD"));
    assertThat(graph.getJavaSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("aidl/TestAndroidAidlClass.java"));
    assertThat(graph.getAndroidSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("aidl/TestAndroidAidlClass.java"));
    assertThat(graph.projectDeps()).containsExactly(Label.of("//" + TESTDATA_ROOT + "/aidl:aidl"));
    assertThat(graph.getFileDependencies(TESTDATA_ROOT.resolve("aidl/TestAndroidAidlClass.java")))
        .containsExactly(Label.of("//" + TESTDATA_ROOT + "/aidl:aidl"));
  }

  @Test
  public void testFileGroupSource() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.FILEGROUP_QUERY), NOOP_CONTEXT, ImmutableSet.of())
            .parse();
    Path sourceFile = TESTDATA_ROOT.resolve("filegroup/TestFileGroupSource.java");
    Path subgroupSourceFile = TESTDATA_ROOT.resolve("filegroup/TestSubFileGroupSource.java");
    assertThat(graph.projectDeps()).containsExactly(Label.of("@@maven//:com.google.guava.guava"));
    assertThat(graph.getJavaSourceFiles()).containsExactly(sourceFile, subgroupSourceFile);
    assertThat(graph.getFileDependencies(sourceFile))
        .containsExactly(Label.of("@@maven//:com.google.guava.guava"));
    assertThat(graph.getFileDependencies(subgroupSourceFile))
        .containsExactly(Label.of("@@maven//:com.google.guava.guava"));
  }

  @Test
  public void testCcLibrary() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.CC_LIBRARY_QUERY), NOOP_CONTEXT, ImmutableSet.of())
            .parse();
    assertThat(graph.getAllSourceFiles())
        .containsExactly(
            TESTDATA_ROOT.resolve("cc/TestClass.cc"),
            TESTDATA_ROOT.resolve("cc/TestClass.h"),
            TESTDATA_ROOT.resolve("cc/BUILD"));
    assertThat(graph.getJavaSourceFiles()).isEmpty();
    assertThat(graph.getAndroidSourceFiles()).isEmpty();
    assertThat(graph.getTargetOwners(TESTDATA_ROOT.resolve("cc/TestClass.cc")))
        .containsExactly(Label.of("//" + TESTDATA_ROOT + "/cc:cc"));
    assertThat(graph.getFileDependencies(TESTDATA_ROOT.resolve("cc/TestClass.cc"))).isEmpty();
    assertThat(graph.targetMap().get(Label.of("//" + TESTDATA_ROOT + "/cc:cc")).languages())
        .containsExactly(QuerySyncLanguage.CC);
  }

  @Test
  public void testGetSameLanguageTargetsDependingOn_returnsTargetAndDirectDependent()
      throws Exception {
    BuildGraphData graph =
        BuildGraphs.forTestProject(TestData.JAVA_LIBRARY_TRANSITIVE_INTERNAL_DEP_QUERY);
    assertThat(
            graph.getSameLanguageTargetsDependingOn(
                ImmutableSet.of(Label.of("//" + TestData.ROOT.resolve("nodeps:nodeps")))))
        .containsExactly(
            Label.of("//" + TestData.ROOT.resolve("nodeps:nodeps")),
            Label.of("//" + TestData.ROOT.resolve("internaldep:internaldep")));

    assertThat(
            graph.getSameLanguageTargetsDependingOn(
                ImmutableSet.of(Label.of("//" + TestData.ROOT.resolve("internaldep:internaldep")))))
        .containsExactly(
            Label.of("//" + TestData.ROOT.resolve("internaldep:internaldep")),
            Label.of("//" + TestData.ROOT.resolve("transitiveinternaldep:transitiveinternaldep")));

    assertThat(
            graph.getSameLanguageTargetsDependingOn(
                ImmutableSet.of(
                    Label.of(
                        "//"
                            + TestData.ROOT.resolve(
                                "transitiveinternaldep:transitiveinternaldep")))))
        .containsExactly(
            Label.of("//" + TestData.ROOT.resolve("transitiveinternaldep:transitiveinternaldep")));
  }

  @Test
  public void testTags() throws Exception {
    BuildGraphData graph = BuildGraphs.forTestProject(TestData.TAGS_QUERY);
    ProjectTarget testTarget = graph.targetMap().get(TestData.TAGS_QUERY.getAssumedOnlyLabel());
    assertThat(testTarget.tags()).containsExactly("mytag");
  }

  @Test
  public void computeRequestedTargets_srcFile() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parse();
    Optional<RequestedTargets> targets =
        graph.computeRequestedTargets(
            graph
                .getProjectTargets(
                    NOOP_CONTEXT,
                    TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY
                        .getOnlySourcePath()
                        .resolve(Path.of("TestClassExternalDep.java")))
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets).isPresent();
    assertThat(targets.get().buildTargets)
        .containsExactly(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY.getAssumedOnlyLabel());
    String expected = "@@maven//:com.google.guava.guava";
    expected = "@@maven//:com.google.guava.guava";
    assertThat(targets.get().expectedDependencyTargets).containsExactly(Label.of(expected));
  }

  @Test
  public void computeRequestedTargets_buildFile_multiTarget() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_MULTI_TARGETS),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parse();
    Optional<RequestedTargets> targets =
        graph.computeRequestedTargets(
            graph
                .getProjectTargets(
                    NOOP_CONTEXT,
                    TestData.JAVA_LIBRARY_MULTI_TARGETS
                        .getOnlySourcePath()
                        .resolve(Path.of("BUILD")))
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets).isPresent();
    assertThat(targets.get().buildTargets)
        .containsExactly(
            TestData.JAVA_LIBRARY_MULTI_TARGETS
                .getAssumedOnlyLabel()
                .siblingWithName("externaldep"),
            TestData.JAVA_LIBRARY_MULTI_TARGETS.getAssumedOnlyLabel().siblingWithName("nodeps"));
    String expected = "@@maven//:com.google.guava.guava";
    expected = "@@maven//:com.google.guava.guava";
    assertThat(targets.get().expectedDependencyTargets).containsExactly(Label.of(expected));
  }

  @Test
  public void computeRequestedTargets_buildFile_nested() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_NESTED_PACKAGE),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parse();
    Optional<RequestedTargets> targets =
        graph.computeRequestedTargets(
            graph
                .getProjectTargets(
                    NOOP_CONTEXT,
                    TestData.JAVA_LIBRARY_NESTED_PACKAGE
                        .getOnlySourcePath()
                        .resolve(Path.of("BUILD")))
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets).isPresent();
    assertThat(targets.get().buildTargets)
        .containsExactly(TestData.JAVA_LIBRARY_NESTED_PACKAGE.getAssumedOnlyLabel());
    assertThat(targets.get().expectedDependencyTargets)
        .containsExactly(Label.of("@@maven//:com.google.guava.guava"));
  }

  @Test
  public void computeRequestedTargets_directory() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_NESTED_PACKAGE),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parse();
    Optional<RequestedTargets> targets =
        graph.computeRequestedTargets(
            graph
                .getProjectTargets(
                    NOOP_CONTEXT, TestData.JAVA_LIBRARY_NESTED_PACKAGE.getOnlySourcePath())
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets).isPresent();
    assertThat(targets.get().buildTargets)
        .containsExactly(
            TestData.JAVA_LIBRARY_NESTED_PACKAGE.getAssumedOnlyLabel(),
            TestData.JAVA_LIBRARY_NESTED_PACKAGE
                .getAssumedOnlyLabel()
                .siblingWithPathAndName("inner:inner"));
    assertThat(targets.get().expectedDependencyTargets)
        .containsExactly(
            Label.of("@@maven//:com.google.guava.guava"),
            Label.of("@@maven//:com.google.code.gson.gson"));
  }

  @Test
  public void computeRequestedTargets_cc_srcFile() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.CC_EXTERNAL_DEP_QUERY), NOOP_CONTEXT, ImmutableSet.of())
            .parse();
    Optional<RequestedTargets> targets =
        graph.computeRequestedTargets(
            graph
                .getProjectTargets(
                    NOOP_CONTEXT,
                    TestData.CC_EXTERNAL_DEP_QUERY.getOnlySourcePath().resolve("TestClass.cc"))
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets).isPresent();
    assertThat(targets.get().buildTargets)
        .containsExactly(TestData.CC_EXTERNAL_DEP_QUERY.getAssumedOnlyLabel());
    assertThat(targets.get().expectedDependencyTargets).isEmpty();
  }

  @Test
  public void reverseDeps() throws IOException {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_NO_DEPS_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parse();
    assertThat(
            graph
                .getReverseDepsForSource(
                    TestData.JAVA_LIBRARY_NO_DEPS_QUERY
                        .getOnlySourcePath()
                        .resolve("TestClassNoDeps.java"))
                .stream()
                .map(ProjectTarget::label))
        .containsExactlyElementsIn(TestData.JAVA_LIBRARY_NO_DEPS_QUERY.getAssumedLabels());
  }
}
