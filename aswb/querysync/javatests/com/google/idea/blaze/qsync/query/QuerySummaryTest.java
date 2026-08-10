/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.query;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.qsync.query.QuerySummaryTestUtil.createProtoForPackages;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class QuerySummaryTest {

  private String targetName(Label buildTarget) {
    return buildTarget.getName();
  }

  @Test
  public void testCreate_javaLibrary_noDeps() throws IOException {
    QuerySummary qs =
        QuerySummaryImpl.create(
            QuerySpec.QueryStrategy.PLAIN,
            TestData.JAVA_LIBRARY_NO_DEPS_QUERY.getQueryOutputPath().toFile());
    Label nodeps = Label.of(TestData.ROOT_PACKAGE + "/nodeps:nodeps");
    assertThat(QuerySummaryKt.getRulesMapForTests(qs).keySet()).containsExactly(nodeps);
    QueryData.Rule rule = QuerySummaryKt.getRulesMapForTests(qs).get(nodeps);
    assertThat(rule.ruleClass()).isEqualTo("java_library");
    assertThat(rule.sources()).hasSize(1);
    assertThat(targetName(rule.sources().get(0))).isEqualTo("TestClassNoDeps.java");
    assertThat(rule.deps()).hasSize(0);
    assertThat(rule.idlSources()).hasSize(0);
    assertThat(QuerySummaryKt.getSourceFilesMapForTests(qs).keySet())
        .containsExactly(
            Label.of(TestData.ROOT_PACKAGE + "/nodeps:TestClassNoDeps.java"),
            Label.of(TestData.ROOT_PACKAGE + "/nodeps:BUILD"));
  }

  @Test
  public void testCreate_ccLibrary_noDeps() throws Exception {
    QuerySummary qs =
        QuerySummaryImpl.create(
            QuerySpec.QueryStrategy.PLAIN, TestData.CC_LIBRARY_QUERY.getQueryOutputPath().toFile());
    Label cc = Label.of(TestData.ROOT_PACKAGE + "/cc:cc");
    assertThat(QuerySummaryKt.getRulesMapForTests(qs).keySet()).containsExactly(cc);
    QueryData.Rule rule =
        Preconditions.checkNotNull(QuerySummaryKt.getRulesMapForTests(qs).get(cc));
    assertThat(rule.ruleClass()).isEqualTo("cc_library");
    assertThat(rule.sources()).hasSize(1);
    assertThat(targetName(rule.sources().get(0))).isEqualTo("TestClass.cc");
    assertThat(rule.hdrs()).hasSize(1);
    assertThat(targetName(rule.hdrs().get(0))).isEqualTo("TestClass.h");
    assertThat(rule.deps()).hasSize(0);
    assertThat(QuerySummaryKt.getSourceFilesMapForTests(qs).keySet())
        .containsExactly(
            Label.of(TestData.ROOT_PACKAGE + "/cc:TestClass.cc"),
            Label.of(TestData.ROOT_PACKAGE + "/cc:TestClass.h"),
            Label.of(TestData.ROOT_PACKAGE + "/cc:BUILD"));
    assertThat(rule.copts()).containsExactly("-w");
  }

  @Test
  public void testCreate_androidLibrary_manifest() throws IOException {
    QuerySummary qs =
        QuerySummaryImpl.create(
            QuerySpec.QueryStrategy.PLAIN,
            TestData.ANDROID_LIB_QUERY.getQueryOutputPath().toFile());
    Label android = Label.of(TestData.ROOT_PACKAGE + "/android:android");
    assertThat(QuerySummaryKt.getRulesMapForTests(qs).keySet()).contains(android);
    QueryData.Rule rule = QuerySummaryKt.getRulesMapForTests(qs).get(android);
    assertThat(rule.manifest()).isEqualTo(android.siblingWithName("AndroidManifest.xml"));
  }

  @Test
  public void testGetPackages_singleRule() {
    QuerySummary summary =
        QuerySummaryImpl.create(createProtoForPackages("//my/build/package:rule"));
    assertThat(summary.getPackages().asPathSet()).containsExactly(Path.of("my/build/package"));
  }

  @Test
  public void testGetPackages_multiRule_onePackage() {
    QuerySummary summary =
        QuerySummaryImpl.create(
            createProtoForPackages("//my/build/package:rule1", "//my/build/package:rule2"));
    assertThat(summary.getPackages().asPathSet()).containsExactly(Path.of("my/build/package"));
  }

  @Test
  public void testGetPackages_multiRule_multiPackage() {
    QuerySummary summary =
        QuerySummaryImpl.create(
            createProtoForPackages(
                "//my/build/package:rule1",
                "//my/build/package:rule2",
                "//my/build/package2:rule1",
                "//my/build/package2:rule2"));
    assertThat(summary.getPackages().asPathSet())
        .containsExactly(Path.of("my/build/package"), Path.of("my/build/package2"));
  }

  @Test
  public void testBuildIncludes() throws IOException {
    QuerySummary qs =
        QuerySummaryImpl.create(
            QuerySpec.QueryStrategy.PLAIN,
            TestData.BUILDINCLUDES_QUERY.getQueryOutputPath().toFile());
    Label buildLabel = Label.of(TestData.ROOT_PACKAGE + "/buildincludes:BUILD");
    assertThat(QuerySummaryKt.getSourceFilesMapForTests(qs)).containsKey(buildLabel);
    QueryData.SourceFile buildSrc = QuerySummaryKt.getSourceFilesMapForTests(qs).get(buildLabel);
    assertThat(buildSrc.subincliudes())
        .containsExactly(
            Label.of("//tools/adt/idea/aswb/build_defs:test_data_build_defs.bzl"),
            Label.of(TestData.ROOT_PACKAGE + "/buildincludes/sub/includes:includes.bzl"),
            Label.of(TestData.ROOT_PACKAGE + "/buildincludes/sub/includes:includes2.bzl"));
    assertThat(qs.getReverseSubincludeMap())
        .containsExactly(
            Path.of("tools/adt/idea/aswb/build_defs/test_data_build_defs.bzl"),
            ImmutableSet.of(
                TestData.ROOT.resolve("buildincludes/sub/includes"),
                TestData.ROOT.resolve("buildincludes/sub"),
                TestData.ROOT.resolve("buildincludes")),
            TestData.ROOT.resolve("buildincludes/sub/includes/includes.bzl"),
            ImmutableSet.of(
                TestData.ROOT.resolve("buildincludes/sub/includes"),
                TestData.ROOT.resolve("buildincludes/sub"),
                TestData.ROOT.resolve("buildincludes")),
            TestData.ROOT.resolve("buildincludes/sub/includes/includes2.bzl"),
            ImmutableSet.of(TestData.ROOT.resolve("buildincludes")));
  }

  @Test
  public void testBuildAllIncludes() throws IOException {
    QuerySummary qs =
        QuerySummaryImpl.create(
            QuerySpec.QueryStrategy.PLAIN,
            TestData.BUILDINCLUDES_QUERY.getQueryOutputPath().toFile());
    Label buildLabel = Label.of(TestData.ROOT_PACKAGE + "/buildincludes:BUILD");
    assertThat(QuerySummaryKt.getSourceFilesMapForTests(qs)).containsKey(buildLabel);
    QueryData.SourceFile buildSrc = QuerySummaryKt.getSourceFilesMapForTests(qs).get(buildLabel);
    assertThat(buildSrc.subincliudes())
        .containsExactly(
            Label.of("//tools/adt/idea/aswb/build_defs:test_data_build_defs.bzl"),
            Label.of(TestData.ROOT_PACKAGE + "/buildincludes/sub/includes:includes.bzl"),
            Label.of(TestData.ROOT_PACKAGE + "/buildincludes/sub/includes:includes2.bzl"));
    assertThat(qs.getAllBuildIncludedFiles())
        .containsExactly(
            Label.of("//tools/adt/idea/aswb/build_defs:test_data_build_defs.bzl"),
            Label.of(TestData.ROOT_PACKAGE + "/buildincludes/sub/includes:includes.bzl"),
            Label.of(TestData.ROOT_PACKAGE + "/buildincludes/sub/includes:includes2.bzl"));
  }

  @Test
  public void getPackages_withEmptyPackage_containsEmptyPackage() throws IOException {
    QuerySummary qs =
        QuerySummaryImpl.create(
            QuerySpec.QueryStrategy.PLAIN, TestData.EMPTY_QUERY.getQueryOutputPath().toFile());
    assertThat(QuerySummaryKt.getRulesMapForTests(qs)).isEmpty();
    assertThat(QuerySummaryKt.getSourceFilesMapForTests(qs).keySet())
        .containsExactly(Label.of(TestData.ROOT_PACKAGE + "/empty:BUILD"));
    assertThat(qs.getPackages().size()).isEqualTo(1);
    assertThat(qs.getPackages().asPathSet()).containsExactly(TestData.ROOT.resolve("empty"));
  }

  @Test
  public void testCreate_proto() throws IOException {
    QuerySummary qs =
        QuerySummaryImpl.create(
            QuerySpec.QueryStrategy.PLAIN,
            TestData.JAVA_LIBRARY_NO_DEPS_QUERY.getQueryOutputPath().toFile());

    assertThat(qs.protoForSerializationOnly().getQueryStrategy())
        .isEqualTo(Query.Summary.QueryStrategy.QUERY_STRATEGY_PLAIN);
  }

  @Test
  public void testDifferentAttributeTypes() throws IOException {
    QuerySummary qs =
        QuerySummaryImpl.create(
            QuerySpec.QueryStrategy.PLAIN, TestData.CUSTOMRULE_QUERY.getQueryOutputPath().toFile());

    assertThat(qs.protoForSerializationOnly().getQueryStrategy())
        .isEqualTo(Query.Summary.QueryStrategy.QUERY_STRATEGY_PLAIN);
  }

  @Test
  public void testSymlinks() throws IOException {
    QuerySummary qs =
        QuerySummaryImpl.create(
            QuerySpec.QueryStrategy.PLAIN, TestData.SYMLINKS_QUERY.getQueryOutputPath().toFile());
    Label testLabel = Label.of(TestData.ROOT_PACKAGE + "/symlinks:test");
    assertThat(QuerySummaryKt.getRulesMapForTests(qs).keySet()).containsExactly(testLabel);
    QueryData.Rule rule = QuerySummaryKt.getRulesMapForTests(qs).get(testLabel);
    assertThat(rule.ruleClass()).isEqualTo("java_library");
    assertThat(rule.sources()).hasSize(4);
    assertThat(rule.sources().stream().map(this::targetName).collect(toImmutableList()))
        .containsExactly("filelink", "dirlink", "dangling_link", "chained_link");
    assertThat(rule.deps()).hasSize(1);
    assertThat(rule.deps())
        .containsExactly(Label.of(TestData.ROOT_PACKAGE + "/symlinks/testdir:testlib"));
    assertThat(QuerySummaryKt.getSourceFilesMapForTests(qs).keySet())
        .containsExactly(
            Label.of(TestData.ROOT_PACKAGE + "/symlinks:BUILD"),
            Label.of(TestData.ROOT_PACKAGE + "/symlinks:filelink"),
            Label.of(TestData.ROOT_PACKAGE + "/symlinks:dirlink"),
            Label.of(TestData.ROOT_PACKAGE + "/symlinks:dangling_link"),
            Label.of(TestData.ROOT_PACKAGE + "/symlinks:chained_link"));
  }

  @Test
  public void testCreate_aliasRule() throws IOException {
    QuerySummary qs =
        QuerySummaryImpl.create(
            QuerySpec.QueryStrategy.PLAIN, TestData.ALIAS_QUERY.getQueryOutputPath().toFile());
    Label aliasLabel = Label.of(TestData.ROOT_PACKAGE + "/alias:alias");
    assertThat(QuerySummaryKt.getRulesMapForTests(qs).keySet()).contains(aliasLabel);
    QueryData.Rule rule = QuerySummaryKt.getRulesMapForTests(qs).get(aliasLabel);
    assertThat(rule.ruleClass()).isEqualTo("alias");
    assertThat(rule.deps()).containsExactly(Label.of(TestData.ROOT_PACKAGE + "/nodeps:nodeps"));
  }

  @Test
  public void testCreate_libraryAttributePopulated() throws Exception {
    Build.Target target =
        Build.Target.newBuilder()
            .setType(Build.Target.Discriminator.RULE)
            .setRule(
                Build.Rule.newBuilder()
                    .setName("//foo:bar")
                    .setRuleClass("android_local_test")
                    .addAttribute(
                        Build.Attribute.newBuilder()
                            .setName("library")
                            .setType(Build.Attribute.Discriminator.LABEL)
                            .setStringValue("//foo:lib")))
            .build();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    target.writeDelimitedTo(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    QuerySummary qs = QuerySummaryImpl.create(QuerySpec.QueryStrategy.PLAIN, in);

    Label barLabel = Label.of("//foo:bar");
    assertThat(QuerySummaryKt.getRulesMapForTests(qs).keySet()).contains(barLabel);
    QueryData.Rule rule = QuerySummaryKt.getRulesMapForTests(qs).get(barLabel);
    assertThat(rule.library()).isEqualTo(Label.of("//foo:lib"));
    assertThat(rule.deps()).containsExactly(Label.of("//foo:lib"));
  }

  @Test
  public void testCreate_runtimeDepsAttributePopulated() throws Exception {
    Build.Target target =
        Build.Target.newBuilder()
            .setType(Build.Target.Discriminator.RULE)
            .setRule(
                Build.Rule.newBuilder()
                    .setName("//foo:test")
                    .setRuleClass("java_test")
                    .addAttribute(
                        Build.Attribute.newBuilder()
                            .setName("runtime_deps")
                            .setType(Build.Attribute.Discriminator.LABEL_LIST)
                            .addStringListValue("//foo:lib")))
            .build();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    target.writeDelimitedTo(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    QuerySummary qs = QuerySummaryImpl.create(QuerySpec.QueryStrategy.PLAIN, in);

    Label testLabel = Label.of("//foo:test");
    assertThat(QuerySummaryKt.getRulesMapForTests(qs).keySet()).contains(testLabel);
    QueryData.Rule rule = QuerySummaryKt.getRulesMapForTests(qs).get(testLabel);
    assertThat(rule.runtimeDeps()).containsExactly(Label.of("//foo:lib"));
  }
}
