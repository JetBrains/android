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
package com.google.idea.blaze.qsync.project

import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.RuleKinds
import com.google.idea.blaze.common.TargetPattern
import com.google.idea.blaze.common.TargetPatternCollection
import com.google.idea.blaze.qsync.BlazeQueryParser
import com.google.idea.blaze.qsync.QuerySyncTestUtils
import com.google.idea.blaze.qsync.project.BuildGraphDataImpl.Companion.builder
import com.google.idea.blaze.qsync.project.BuildGraphDataImpl.Companion.filterRedundantTargets
import com.google.idea.blaze.qsync.testdata.BuildGraphs
import com.google.idea.blaze.qsync.testdata.TestData
import java.io.IOException
import java.nio.file.Path
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4


@RunWith(JUnit4::class)
class BuildGraphDataImplTest {
  @get:Rule
  var expect: Expect = Expect.create()

  private val emptyTargetCollection = TargetPatternCollection.create(emptyList())
  private val defaultProtoRules = BuildGraphData.ProtoRules.forTests()

  @Test
  fun pathToLabel() {
    val builder = builder()

    builder
      .addSourceFileLabel((Label.of("//:BUILD")))
      .addSourceFileLabel(Label.of("//nested:BUILD"))
      .addSourceFileLabel(Label.of("//nested:file.txt"))
      .addSourceFileLabel(Label.of("//nested/inner:BUILD"))
      .addSourceFileLabel(Label.of("//nested/inner:deep/file.txt"))

    builder
      .addSupportedTargetLabel(Label.of("//:target"))
      .addSupportedTargetLabel(Label.of("//nested:nested"))
      .addSupportedTargetLabel(Label.of("//nested/inner:inner"))

    val graph = builder.build(emptyTargetCollection, emptySet(), emptySet(), defaultProtoRules)
    expect.that(graph.pathToLabel(Path.of("abc.txt"))).isEqualTo(Label.of("//:abc.txt"))
    expect.that(graph.pathToLabel(Path.of("BUILD"))).isEqualTo(Label.of("//:BUILD"))
    expect.that(graph.pathToLabel(Path.of("nested/abc.txt"))).isEqualTo(Label.of("//nested:abc.txt"))
    expect.that(graph.pathToLabel(Path.of("nested/file.txt"))).isEqualTo(Label.of("//nested:file.txt"))
    expect.that(graph.pathToLabel(Path.of("nested/BUILD"))).isEqualTo(Label.of("//nested:BUILD"))
    expect.that(graph.pathToLabel(Path.of("nested/inner/abc.txt")))
      .isEqualTo(Label.of("//nested/inner:abc.txt"))
    expect.that(graph.pathToLabel(Path.of("nested/inner/deep/file.txt")))
      .isEqualTo(Label.of("//nested/inner:deep/file.txt"))
    expect.that(graph.pathToLabel(Path.of("nested/inner/BUILD")))
      .isEqualTo(Label.of("//nested/inner:BUILD"))
    expect.that(graph.pathToLabel(Path.of("other/abc.txt"))).isEqualTo(Label.of("//:other/abc.txt"))
    expect.that(graph.pathToLabel(Path.of("other/BUILD"))).isEqualTo(Label.of("//:other/BUILD"))
    expect.that(graph.pathToLabel(Path.of("other/inner/abc.txt")))
      .isEqualTo(Label.of("//:other/inner/abc.txt"))
    expect.that(graph.pathToLabel(Path.of("other/inner/BUILD")))
      .isEqualTo(Label.of("//:other/inner/BUILD"))
  }

  @Test
  fun valueEquality() {
    assertThat(builder().build(emptyTargetCollection, emptySet(), emptySet(), defaultProtoRules))
      .isEqualTo(builder().build(emptyTargetCollection, emptySet(), emptySet(), defaultProtoRules))
  }

  @Test
  fun valueInequality_differentProjectDefinition() {
    val projectDefinition1 = TargetPatternCollection.create(listOf(TargetPattern.parse("//:target1")))
    val projectDefinition2 = TargetPatternCollection.create(listOf(TargetPattern.parse("//:target2")))
    assertThat(builder().build(projectDefinition1, emptySet(), emptySet(), defaultProtoRules))
      .isNotEqualTo(builder().build(projectDefinition2, emptySet(), emptySet(), defaultProtoRules))
  }

  @Test
  fun valueInequality_differentAlwaysBuildRules() {
    val alwaysBuildRules1 = setOf("rule1")
    val alwaysBuildRules2 = setOf("rule2")
    assertThat(builder().build(emptyTargetCollection, alwaysBuildRules1, emptySet(), defaultProtoRules))
      .isNotEqualTo(builder().build(emptyTargetCollection, alwaysBuildRules2, emptySet(), defaultProtoRules))
  }

  @Test
  fun valueInequality_differentSupportedBuildRules() {
    val supportedBuildRules1 = setOf("rule1")
    val supportedBuildRules2 = setOf("rule2")
    assertThat(builder().build(emptyTargetCollection, emptySet(), supportedBuildRules1, defaultProtoRules))
      .isNotEqualTo(builder().build(emptyTargetCollection, emptySet(), supportedBuildRules2, defaultProtoRules))
  }

  @Test
  fun valueInequality_differentSupportedTargets() {
    val builder1 = builder().addSupportedTargetLabel(Label.of("//:target1"))
    val builder2 = builder().addSupportedTargetLabel(Label.of("//:target2"))
    assertThat(builder1.build(emptyTargetCollection, emptySet(), emptySet(), defaultProtoRules))
      .isNotEqualTo(builder2.build(emptyTargetCollection, emptySet(), emptySet(), defaultProtoRules))
  }

  @Test
  fun sourceFileToLabel() {
    val builder = builder()
    builder
      .addSourceFileLabel(Label.of("//:BUILD"))
      .addSourceFileLabel(Label.of("//nested:BUILD"))
      .addSourceFileLabel(Label.of("//nested:file.txt"))
      .addSourceFileLabel(Label.of("//nested/inner:BUILD"))
      .addSourceFileLabel(Label.of("//nested/inner:deep/file.txt"))

    builder
      .addSupportedTargetLabel(Label.of("//:target"))
      .addSupportedTargetLabel(Label.of("//nested:nested"))
      .addSupportedTargetLabel(Label.of("//nested/inner:inner"))

    val graph = builder.build(emptyTargetCollection, emptySet(), emptySet(), defaultProtoRules)
    expect.that(graph.sourceFileToLabel(Path.of("abc.txt"))).isNull()
    expect.that(graph.sourceFileToLabel(Path.of("BUILD"))).isEqualTo(Label.of("//:BUILD"))
    expect.that(graph.sourceFileToLabel(Path.of("nested/abc.txt"))).isNull()
    expect.that(graph.sourceFileToLabel(Path.of("nested/file.txt")))
      .isEqualTo(Label.of("//nested:file.txt"))
    expect.that(graph.sourceFileToLabel(Path.of("nested/BUILD"))).isEqualTo(Label.of("//nested:BUILD"))
    expect.that(graph.sourceFileToLabel(Path.of("nested/inner/abc.txt"))).isNull()
    expect.that(graph.sourceFileToLabel(Path.of("nested/inner/deep/file.txt")))
      .isEqualTo(Label.of("//nested/inner:deep/file.txt"))
    expect.that(graph.sourceFileToLabel(Path.of("nested/inner/BUILD")))
      .isEqualTo(Label.of("//nested/inner:BUILD"))
    expect.that(graph.sourceFileToLabel(Path.of("other/abc.txt"))).isNull()
    expect.that(graph.sourceFileToLabel(Path.of("other/BUILD"))).isNull()
    expect.that(graph.sourceFileToLabel(Path.of("other/inner/abc.txt"))).isNull()
    expect.that(graph.sourceFileToLabel(Path.of("other/inner/BUILD"))).isNull()
  }

  @Test
  @Throws(Exception::class)
  fun testJavaLibraryNoDeps() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.JAVA_LIBRARY_NO_DEPS_QUERY),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    assertThat(
        graph.allSupportedTargets.getTargets().toList()
    )
      .containsExactly(Label.of("//$TESTDATA_ROOT/nodeps:nodeps"))
    assertThat(graph.storage.sourceFileLabels)
      .containsExactly(
        Label.of("//$TESTDATA_ROOT/nodeps:TestClassNoDeps.java"),
        Label.of("//$TESTDATA_ROOT/nodeps:BUILD")
      )
    assertThat(graph.getJavaSourceFiles())
      .containsExactly(TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java"))
    assertThat(graph.getSourceFileOwners(TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java")))
      .containsExactly(Label.of("//$TESTDATA_ROOT/nodeps:nodeps"))
    assertThat(
      getRequiredTargets(
        graph,
        listOf(Label.of("//$TESTDATA_ROOT/nodeps:nodeps"))
      )
    )
      .isEmpty()
    assertThat(
      graph.getProjectTarget(Label.of("//$TESTDATA_ROOT/nodeps:nodeps"))!!
        .languages()
    )
      .containsExactly(QuerySyncLanguage.JVM)
  }

  @Test
  @Throws(Exception::class)
  fun testJavaLibraryExternalDep() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    assertThat(
      getRequiredTargets(
        graph,
        listOf(Label.of("//" + TESTDATA_ROOT.resolve("externaldep:externaldep")))
      )
    )
      .containsExactly(TestData.JAVA_LIBRARY_NO_DEPS_QUERY.assumedOnlyLabel)
  }

  @Test
  @Throws(Exception::class)
  fun testJavaLibraryInternalDep() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.JAVA_LIBRARY_INTERNAL_DEP_QUERY),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    // Sanity check:
    assertThat(graph.storage.sourceFileLabels)
      .contains(Label.of("//$TESTDATA_ROOT/nodeps:TestClassNoDeps.java"))
    assertThat(
      getRequiredTargets(
        graph,
        listOf(Label.of("//" + TESTDATA_ROOT.resolve("internaldep:internaldep")))
      )
    )
      .isEmpty()
  }

  @Test
  @Throws(Exception::class)
  fun testJavaLibraryTransientDep() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.JAVA_LIBRARY_TRANSITIVE_DEP_QUERY),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    // Sanity check:
    assertThat(graph.storage.sourceFileLabels)
      .contains(Label.of("//$TESTDATA_ROOT/externaldep:TestClassExternalDep.java"))
    assertThat(
      getRequiredTargets(
        graph,
        listOf(Label.of("//" + TESTDATA_ROOT.resolve("transitivedep:transitivedep")))
      )
    )
      .containsExactly(TestData.JAVA_LIBRARY_NO_DEPS_QUERY.assumedOnlyLabel)
  }

  @Test
  @Throws(Exception::class)
  fun testJavaLibraryProtoDep() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.JAVA_LIBRARY_PROTO_DEP_QUERY),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    assertThat(
      getRequiredTargets(
        graph,
        listOf(Label.of("//" + TESTDATA_ROOT.resolve("protodep:protodep")))
      )
    )
      .containsExactly(
        Label.of("//$TESTDATA_ROOT/protodep:proto_java_proto")
      )
    assertThat(
      getRequiredTargets(
        graph,
        listOf(Label.of("//" + TESTDATA_ROOT.resolve("protodep:indirect_protodep")))
      )
    )
      .containsExactly(
        Label.of("//$TESTDATA_ROOT/protodep:indirect_proto_java_proto")
      )
  }

  @Test
  @Throws(Exception::class)
  fun testJavaLibraryMultiTargets() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.JAVA_LIBRARY_MULTI_TARGETS),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    assertThat(graph.allSupportedTargets.getTargets().toList())
      .containsExactly(
        Label.of("//$TESTDATA_ROOT/multitarget:nodeps"),
        Label.of("//$TESTDATA_ROOT/multitarget:externaldep")
      )
    // Sanity check:
    assertThat(graph.getJavaSourceFiles())
      .contains(TESTDATA_ROOT.resolve("multitarget/TestClassSingleTarget.java"))
    assertThat(
      graph.getSourceFileOwners(TESTDATA_ROOT.resolve("multitarget/TestClassMultiTarget.java"))
    )
      .containsExactly(
        Label.of("//$TESTDATA_ROOT/multitarget:nodeps"),
        Label.of("//$TESTDATA_ROOT/multitarget:externaldep")
      )
    assertThat(
      getRequiredTargets(
        graph,
        listOf(Label.of("//" + TESTDATA_ROOT.resolve("multitarget:externaldep")))
      )
    )
      .contains(Label.of("//tools/vendor/google/aswb/plugin_api/maven:guava"))
    assertThat(
      getRequiredTargets(
        graph,
        listOf(Label.of("//" + TESTDATA_ROOT.resolve("multitarget:nodeps")))
      )
    )
      .isEmpty()
  }

  @Test
  @Throws(Exception::class)
  fun testJavaLibraryExportingExternalTargets() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.JAVA_EXPORTED_DEP_QUERY),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    val sourceFile: Path = TESTDATA_ROOT.resolve("exports/TestClassUsingExport.java")
    assertThat(graph.getJavaSourceFiles()).containsExactly(sourceFile)
    assertThat(
      getRequiredTargets(
        graph,
        listOf(Label.of("//" + TESTDATA_ROOT.resolve("exports:exports")))
      )
    )
      .containsExactly(Label.of("//tools/vendor/google/aswb/plugin_api/maven:guava"))
  }

  @Test
  @Throws(Exception::class)
  fun testAndroidLibrary() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.ANDROID_LIB_QUERY),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    assertThat(graph.storage.sourceFileLabels)
      .containsExactly(
        Label.of("//$TESTDATA_ROOT/android:TestAndroidClass.java"),
        Label.of("//$TESTDATA_ROOT/android:BUILD"),
        Label.of("//$TESTDATA_ROOT/android:AndroidManifest.xml"),
        Label.of("//$TESTDATA_ROOT/android:res/values/strings.xml")
      )
    assertThat(graph.getJavaSourceFiles())
      .containsExactly(TESTDATA_ROOT.resolve("android/TestAndroidClass.java"))
    assertThat(graph.getSourceFileOwners(TESTDATA_ROOT.resolve("android/TestAndroidClass.java")))
      .containsExactly(Label.of("//$TESTDATA_ROOT/android:android"))
    assertThat(
      getRequiredTargets(
        graph,
        listOf(Label.of("//" + TESTDATA_ROOT.resolve("android:android")))
      )
    )
      .isEmpty()
  }

  @Test
  @Throws(Exception::class)
  fun testProjectAndroidLibrariesWithAidlSource_areProjectDeps() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.ANDROID_AIDL_SOURCE_QUERY),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    assertThat(graph.storage.sourceFileLabels)
      .containsExactly(
        Label.of("//$TESTDATA_ROOT/aidl:TestAndroidAidlClass.java"),
        Label.of("//$TESTDATA_ROOT/aidl:TestAidlService.aidl"),
        Label.of("//$TESTDATA_ROOT/aidl:BUILD")
      )
    assertThat(graph.getJavaSourceFiles())
      .containsExactly(TESTDATA_ROOT.resolve("aidl/TestAndroidAidlClass.java"))
    assertThat(
      getRequiredTargets(
        graph,
        listOf(Label.of("//" + TESTDATA_ROOT.resolve("aidl:aidl")))
      )
    )
      .containsExactly(Label.of("//$TESTDATA_ROOT/aidl:aidl"))
  }

  @Test
  @Throws(Exception::class)
  fun testProjectAndroidLibrariesWithAidlSource_aidlsAreSources() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.ANDROID_AIDL_SOURCE_QUERY),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    assertThat(graph.storage.sourceFileLabels)
      .containsExactly(
        Label.of("//$TESTDATA_ROOT/aidl:TestAndroidAidlClass.java"),
        Label.of("//$TESTDATA_ROOT/aidl:TestAidlService.aidl"),
        Label.of("//$TESTDATA_ROOT/aidl:BUILD")
      )
    assertThat(graph.getJavaSourceFiles())
      .containsExactly(TESTDATA_ROOT.resolve("aidl/TestAndroidAidlClass.java"))
    assertThat(graph.getSourceFilesByRuleKindAndType({ RuleKinds.isAndroid(it)}, ProjectTarget.SourceType.AIDL))
      .containsExactly(
        Label.of("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/aidl:aidl"),
        listOf(Path.of("$TESTDATA_ROOT/aidl/TestAidlService.aidl"))
      )
  }

  @Test
  @Throws(Exception::class)
  fun testFileGroupSource() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.FILEGROUP_QUERY),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    val sourceFile: Path = TESTDATA_ROOT.resolve("filegroup/TestFileGroupSource.java")
    val subgroupSourceFile: Path = TESTDATA_ROOT.resolve("filegroup/TestSubFileGroupSource.java")
    assertThat(graph.getJavaSourceFiles()).containsExactly(sourceFile, subgroupSourceFile)
    assertThat(graph.getSourceFileOwners(sourceFile))
      .containsExactly(Label.of("//$TESTDATA_ROOT/filegroup:filegroup"))
    assertThat(graph.getSourceFileOwners(subgroupSourceFile))
      .containsExactly(Label.of("//$TESTDATA_ROOT/filegroup:filegroup"))
    assertThat(
      getRequiredTargets(
        graph,
        listOf(Label.of("//$TESTDATA_ROOT/filegroup:filegroup"))
      )
    )
      .containsExactly(Label.of("//tools/vendor/google/aswb/plugin_api/maven:guava"))
  }

  @Test
  @Throws(Exception::class)
  fun testCcLibrary() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.CC_LIBRARY_QUERY),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    assertThat(graph.storage.sourceFileLabels)
      .containsExactly(
        Label.of("//$TESTDATA_ROOT/cc:TestClass.cc"),
        Label.of("//$TESTDATA_ROOT/cc:TestClass.h"),
        Label.of("//$TESTDATA_ROOT/cc:BUILD")
      )
    assertThat(graph.getJavaSourceFiles()).isEmpty()
    assertThat(graph.getSourceFileOwners(TESTDATA_ROOT.resolve("cc/TestClass.cc")))
      .containsExactly(Label.of("//$TESTDATA_ROOT/cc:cc"))
    assertThat(
      getRequiredTargets(
        graph,
        listOf(Label.of("//$TESTDATA_ROOT/cc:cc"))
      )
    ).isEmpty()
    assertThat(graph.getProjectTarget(Label.of("//$TESTDATA_ROOT/cc:cc"))!!.languages())
      .containsExactly(QuerySyncLanguage.CC)
  }

  @Test
  @Throws(Exception::class)
  fun testGetSameLanguageTargetsDependingOn_returnsTargetAndDirectDependent() {
    val graph =
      BuildGraphs.forTestProject(TestData.JAVA_LIBRARY_TRANSITIVE_INTERNAL_DEP_QUERY)
    assertThat(
      graph.getSameLanguageTargetsDependingOn(
        setOf(Label.of("//" + TestData.ROOT.resolve("nodeps:nodeps")))
      )
    )
      .containsExactly(
        Label.of("//" + TestData.ROOT.resolve("nodeps:nodeps")),
        Label.of("//" + TestData.ROOT.resolve("internaldep:internaldep"))
      )

    assertThat(
      graph.getSameLanguageTargetsDependingOn(
        setOf(Label.of("//" + TestData.ROOT.resolve("internaldep:internaldep")))
      )
    )
      .containsExactly(
        Label.of("//" + TestData.ROOT.resolve("internaldep:internaldep")),
        Label.of("//" + TestData.ROOT.resolve("transitiveinternaldep:transitiveinternaldep"))
      )

    assertThat(
      graph.getSameLanguageTargetsDependingOn(
        setOf(
          Label.of(
            "//"
              + TestData.ROOT.resolve(
              "transitiveinternaldep:transitiveinternaldep"
            )
          )
        )
      )
    )
      .containsExactly(
        Label.of("//" + TestData.ROOT.resolve("transitiveinternaldep:transitiveinternaldep"))
      )
  }

  @Test
  @Throws(Exception::class)
  fun testTags() {
    val graph = BuildGraphs.forTestProject(TestData.TAGS_QUERY)
    val testTarget = graph.getProjectTarget(TestData.TAGS_QUERY.assumedOnlyLabel)
    assertThat(testTarget!!.tags()).containsExactly("mytag")
  }

  @Test
  @Throws(Exception::class)
  fun computeRequestedTargets_srcFile() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    val targets =
      graph.computeRequestedTargets(
        graph
          .getProjectTargets(
            TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY
              .onlySourcePath
              .resolve(Path.of("TestClassExternalDep.java"))
          )
          .getUnambiguousTargets(),
        replaceNativeTargetsWithAndroidTransitionTriggeringTargets = false
      )
    assertThat(targets.targetsToBuild)
      .containsExactly(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY.assumedOnlyLabel)
    assertThat(targets.requiredTargets)
      .containsExactly(TestData.JAVA_LIBRARY_NO_DEPS_QUERY.assumedOnlyLabel)
  }

  @Test
  @Ignore("b/423875334 - the behavior is currently undefined as we chose either of targets") // TODO: b/423875334 - in the case of targets like (a.java), (a.java, b.java) it is safe to choose the later.it is not always possible
  // to prefer one option to another though. For example, (a, b), (b, c), (a, c) can have three different results.
  @Throws(Exception::class)
  fun computeRequestedTargets_buildFile_multiTarget() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.JAVA_LIBRARY_MULTI_TARGETS),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    val targets =
      graph.computeRequestedTargets(
        graph
          .getProjectTargets(
            TestData.JAVA_LIBRARY_MULTI_TARGETS
              .onlySourcePath
              .resolve(Path.of("BUILD"))
          )
          .getUnambiguousTargets(),
        replaceNativeTargetsWithAndroidTransitionTriggeringTargets = false
      )
    assertThat(targets.targetsToBuild)
      .containsExactly(
        TestData.JAVA_LIBRARY_MULTI_TARGETS
          .assumedOnlyLabel
          .siblingWithName("externaldep"),
        TestData.JAVA_LIBRARY_MULTI_TARGETS
          .assumedOnlyLabel
          .siblingWithName("nodeps")
      )
    val expected = "//tools/vendor/google/aswb/plugin_api/maven:guava"
    assertThat(targets.requiredTargets).containsExactly(Label.of(expected))
  }

  @Test
  @Throws(Exception::class)
  fun computeRequestedTargets_buildFile_nested() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.JAVA_LIBRARY_NESTED_PACKAGE),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    val targets =
      graph.computeRequestedTargets(
        graph
          .getProjectTargets(
            TestData.JAVA_LIBRARY_NESTED_PACKAGE
              .onlySourcePath
              .resolve(Path.of("BUILD"))
          )
          .getUnambiguousTargets(),
        replaceNativeTargetsWithAndroidTransitionTriggeringTargets = false
      )
    assertThat(targets.targetsToBuild)
      .containsExactly(TestData.JAVA_LIBRARY_NESTED_PACKAGE.assumedOnlyLabel)
    assertThat(targets.requiredTargets)
      .containsExactly(Label.of("//tools/vendor/google/aswb/plugin_api/maven:guava"))
  }

  @Test
  @Throws(Exception::class)
  fun computeRequestedTargets_directory() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.JAVA_LIBRARY_NESTED_PACKAGE),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    val targets =
      graph.computeRequestedTargets(
        graph
          .getProjectTargets(
            TestData.JAVA_LIBRARY_NESTED_PACKAGE.onlySourcePath
          )
          .getUnambiguousTargets(),
        replaceNativeTargetsWithAndroidTransitionTriggeringTargets = false
      )
    assertThat(targets.targetsToBuild)
      .containsExactly(
        TestData.JAVA_LIBRARY_NESTED_PACKAGE.assumedOnlyLabel,
        TestData.JAVA_LIBRARY_NESTED_PACKAGE
          .assumedOnlyLabel
          .siblingWithPathAndName("inner:inner")
      )
    assertThat(targets.requiredTargets)
      .containsExactly(
        Label.of("//tools/vendor/google/aswb/plugin_api/maven:guava"),
        Label.of("@@maven//:com.google.code.gson.gson")
      )
  }

  @Test
  @Throws(Exception::class)
  fun computeRequestedTargets_cc_srcFile() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.CC_EXTERNAL_DEP_QUERY),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    val targets =
      graph.computeRequestedTargets(
        graph
          .getProjectTargets(
            TestData.CC_EXTERNAL_DEP_QUERY.onlySourcePath.resolve("TestClass.cc")
          )
          .getUnambiguousTargets(),
        replaceNativeTargetsWithAndroidTransitionTriggeringTargets = false
      )
    assertThat(targets.targetsToBuild)
      .containsExactly(TestData.CC_EXTERNAL_DEP_QUERY.assumedOnlyLabel)
    assertThat(targets.requiredTargets).isEmpty()
  }

  private fun filterRedundantTargets(graph: Map<String, Set<String>>, targets: Set<String>  ): Set<String> {
    return filterRedundantTargets({ graph.getOrDefault(it, emptySet()) }, targets)
  }

  @Test
  fun testProtoModePropagation() {
    val builder = builder()
    val fullProto = Label.of("//:full_proto")
    val liteProto = Label.of("//:lite_proto")
    val depOfFull = Label.of("//:dep_of_full")
    val depOfLite = Label.of("//:dep_of_lite")
    val sharedDep = Label.of("//:shared_dep")
    val explicitOverridden = Label.of("//:explicit_overridden")
    val transitiveSharedDep = Label.of("//:transitive_shared_dep")
    val intermediateLite = Label.of("//:intermediate_lite")

    fun addTarget(label: Label, kind: String, deps: List<Label>) {
      val targetBuilder = ProjectTarget.builder()
        .label(label)
        .kind(kind)
        .tags(emptyList())
      deps.forEach { targetBuilder.depsBuilder().add(it) }
      builder.addTarget(label, targetBuilder.build())
    }

    val consumerOfFull = Label.of("//:consumer_of_full")
    val consumerOfLite = Label.of("//:consumer_of_lite")
    val consumerOfShared = Label.of("//:consumer_of_shared")

    addTarget(fullProto, "java_proto_library", listOf(depOfFull, sharedDep, explicitOverridden))
    addTarget(liteProto, "java_lite_proto_library", listOf(depOfLite, intermediateLite))
    addTarget(intermediateLite, "java_library", listOf(sharedDep))
    addTarget(depOfFull, "java_library", emptyList())
    addTarget(depOfLite, "java_library", emptyList())
    addTarget(sharedDep, "java_library", listOf(transitiveSharedDep))
    addTarget(explicitOverridden, "java_proto_library", emptyList())
    addTarget(transitiveSharedDep, "java_library", emptyList())
    addTarget(consumerOfFull, "java_library", listOf(fullProto))
    addTarget(consumerOfLite, "java_library", listOf(liteProto))
    addTarget(consumerOfShared, "java_library", listOf(sharedDep))

    val graph = builder.build(emptyTargetCollection, emptySet(), emptySet(), defaultProtoRules)

    // Downward propagation checks
    assertThat(graph.getProtoModes(fullProto)).containsExactly(BuildGraphData.ProtoMode.FULL)
    assertThat(graph.getProtoModes(liteProto)).containsExactly(BuildGraphData.ProtoMode.LITE)
    assertThat(graph.getProtoModes(depOfFull)).containsExactly(BuildGraphData.ProtoMode.FULL)
    assertThat(graph.getProtoModes(depOfLite)).containsExactly(BuildGraphData.ProtoMode.LITE)
    assertThat(graph.getProtoModes(intermediateLite)).containsExactly(BuildGraphData.ProtoMode.LITE)
    assertThat(graph.getProtoModes(sharedDep)).containsExactly(BuildGraphData.ProtoMode.FULL, BuildGraphData.ProtoMode.LITE)
    assertThat(graph.getProtoModes(explicitOverridden)).containsExactly(BuildGraphData.ProtoMode.FULL)
    assertThat(graph.getProtoModes(transitiveSharedDep)).containsExactly(BuildGraphData.ProtoMode.FULL, BuildGraphData.ProtoMode.LITE)

    // Upward propagation checks
    assertThat(graph.getProtoModes(consumerOfFull)).containsExactly(BuildGraphData.ProtoMode.FULL)
    assertThat(graph.getProtoModes(consumerOfLite)).containsExactly(BuildGraphData.ProtoMode.LITE)
    // consumerOfShared depends on sharedDep, which has FULL/LITE via downward propagation.
    // But sharedDep is not a proto library itself, so it does not propagate modes upward.
    assertThat(graph.getProtoModes(consumerOfShared)).isEmpty()
  }

  @Test
  @Throws(Exception::class)
  fun filterRedundantTargets_scenario1() {
    val graph =
      mapOf(
        "A" to setOf("B"),
        "B" to setOf("C"),
        "C" to setOf("D")
      )
    assertThat(filterRedundantTargets(graph, setOf("A", "D")) == setOf("A")).isTrue()
    assertThat(      filterRedundantTargets(graph, setOf("B", "C")) == setOf("B"      )    )      .isTrue()
  }

  @Test
  @Throws(Exception::class)
  fun filterRedundantTargets_scenario2() {
    val graph = mapOf(
        "A" to setOf("B"),
        "B" to setOf("C"),
        "Z" to setOf("D"),
      )
    assertThat(filterRedundantTargets(graph, setOf("C", "D")) == setOf("C", "D")).isTrue()
  }

  @Test
  @Throws(Exception::class)
  fun filterRedundantTargets_scenario3() {
    val graph = mapOf(
        "A" to emptySet<String>(),
        "B" to emptySet(),
        "C" to emptySet(),
      )
    assertThat(
      filterRedundantTargets(graph, setOf("A", "C"))
        == setOf("A", "C")
    )
      .isTrue()
  }

  @Test
  @Throws(Exception::class)
  fun filterRedundantTargets_scenario4() {
    val graph = mapOf(
        "A" to setOf("B", "C"),
        "B" to setOf("D"),
        "C" to setOf("D"),
        "E" to setOf("C"),
      )
    assertThat(filterRedundantTargets(graph, setOf("A", "D")) == setOf("A")).isTrue()
    assertThat(filterRedundantTargets(graph, setOf("A", "E", "D")) == setOf("A", "E")).isTrue()
  }

  @Test
  @Throws(Exception::class)
  fun filterRedundantTargets_invalid_data() {
    val graph = mapOf("A" to setOf(""))

    assertThat(filterRedundantTargets(graph, setOf("B")) == setOf("B")).isTrue()
    assertThat(filterRedundantTargets(graph, emptySet()) == emptySet<String>()).isTrue()
  }

  @Test
  @Throws(IOException::class)
  fun reverseDeps() {
    val graph =
      BlazeQueryParser(
        emptyTargetCollection,
        QuerySyncTestUtils.getQuerySummary(TestData.JAVA_LIBRARY_NO_DEPS_QUERY),
        QuerySyncTestUtils.NOOP_CONTEXT,
        emptySet(),
        defaultProtoRules
      )
        .parseForTesting()
    assertThat(
      graph
        .getReverseDepsForSource(
          TestData.JAVA_LIBRARY_NO_DEPS_QUERY
            .onlySourcePath
            .resolve("TestClassNoDeps.java")
        )
        .map { it.label() }
    )
      .containsExactlyElementsIn(TestData.JAVA_LIBRARY_NO_DEPS_QUERY.assumedLabels)
  }

  @Test
  fun traverseDag() {
    val graph = mapOf(
      "a" to setOf("b", "c"),
      "b" to setOf("c", "d"),
      "c" to setOf("x"),
      "d" to setOf("y"),
      "x" to setOf("z"),
      "y" to setOf("z"),
      "z" to setOf(),
    )
    fun Collection<String>.traverseDag() = traverseDag(valueEmitter = { it }, edgeSelector = { n, v -> graph[n].orEmpty() }).toList()

    expect.that(setOf("a", "b").traverseDag()).containsExactly("a", "b", "c", "d", "x", "y", "z").inOrder()
    expect.that(setOf("a", "b").traverseDag()).isEqualTo(setOf("a").traverseDag())
    expect.that(setOf("c").traverseDag()).containsExactly("c", "x", "z").inOrder()
  }

  private fun getRequiredTargets(
    graph: BuildGraphData,
    forTargets: Collection<Label>,
  ): Set<Label> {
    return graph
      .computeRequestedTargets(
        forTargets,
        replaceNativeTargetsWithAndroidTransitionTriggeringTargets = false
      )
      .requiredTargets
  }

  companion object {
    private val TEST_ROOT: Path =
      Path.of("tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync")

    private val TESTDATA_ROOT: Path = TEST_ROOT.resolve("testdata")
  }
}
