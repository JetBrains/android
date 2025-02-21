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

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.qsync.query.QuerySummaryTestUtil.createProtoForPackages;

import com.google.common.base.Preconditions;
import com.google.common.truth.Truth8;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class QuerySummaryTest {

  private String targetName(Label buildTarget) {
    return buildTarget.name();
  }

  @Test
  public void testCreate_javaLibrary_noDeps() throws IOException {
    QuerySummary qs =
        QuerySummaryImpl.create(QuerySpec.QueryStrategy.PLAIN, TestData.JAVA_LIBRARY_NO_DEPS_QUERY.getQueryOutputPath().toFile());
    Label nodeps = Label.of(TestData.ROOT_PACKAGE + "/nodeps:nodeps");
    assertThat(qs.getRulesMap().keySet()).containsExactly(nodeps);
    QueryData.Rule rule = qs.getRulesMap().get(nodeps);
    assertThat(rule.ruleClass()).isEqualTo("java_library");
    assertThat(rule.sources()).hasSize(1);
    assertThat(targetName(rule.sources().get(0))).isEqualTo("TestClassNoDeps.java");
    assertThat(rule.deps()).hasSize(0);
    assertThat(rule.idlSources()).hasSize(0);
    assertThat(qs.getSourceFilesMap().keySet())
        .containsExactly(
            Label.of(TestData.ROOT_PACKAGE + "/nodeps:TestClassNoDeps.java"),
            Label.of(TestData.ROOT_PACKAGE + "/nodeps:BUILD"));
  }

  @Test
  public void testCreate_ccLibrary_noDeps() throws Exception {
    QuerySummary qs = QuerySummaryImpl.create(QuerySpec.QueryStrategy.PLAIN, TestData.CC_LIBRARY_QUERY.getQueryOutputPath().toFile());
    Label cc = Label.of(TestData.ROOT_PACKAGE + "/cc:cc");
    assertThat(qs.getRulesMap().keySet()).containsExactly(cc);
    QueryData.Rule rule = Preconditions.checkNotNull(qs.getRulesMap().get(cc));
    assertThat(rule.ruleClass()).isEqualTo("cc_library");
    assertThat(rule.sources()).hasSize(1);
    assertThat(targetName(rule.sources().get(0))).isEqualTo("TestClass.cc");
    assertThat(rule.hdrs()).hasSize(1);
    assertThat(targetName(rule.hdrs().get(0))).isEqualTo("TestClass.h");
    assertThat(rule.deps()).hasSize(0);
    assertThat(qs.getSourceFilesMap().keySet())
        .containsExactly(
            Label.of(TestData.ROOT_PACKAGE + "/cc:TestClass.cc"),
            Label.of(TestData.ROOT_PACKAGE + "/cc:TestClass.h"),
            Label.of(TestData.ROOT_PACKAGE + "/cc:BUILD"));
    assertThat(rule.copts()).containsExactly("-w");
  }

  @Test
  public void testCreate_androidLibrary_manifest() throws IOException {
    QuerySummary qs = QuerySummaryImpl.create(QuerySpec.QueryStrategy.PLAIN, TestData.ANDROID_LIB_QUERY.getQueryOutputPath().toFile());
    Label android = Label.of(TestData.ROOT_PACKAGE + "/android:android");
    assertThat(qs.getRulesMap().keySet()).contains(android);
    QueryData.Rule rule = qs.getRulesMap().get(android);
    assertThat(rule.manifest())
        .isEqualTo(Optional.of(android.siblingWithName("AndroidManifest.xml")));
  }

  @Test
  public void testGetPackages_singleRule() {
    QuerySummary summary = QuerySummaryImpl.create(createProtoForPackages("//my/build/package:rule"));
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
  public void testGetParentPackage_noparent() {
    QuerySummary summary = QuerySummaryImpl.create(createProtoForPackages("//my/build/package:rule"));
    Truth8.assertThat(summary.getParentPackage(Path.of("my/build/package"))).isEmpty();
  }

  @Test
  public void testGetParentPackage_directParent() {
    QuerySummary summary =
        QuerySummaryImpl.create(
            createProtoForPackages(
                "//my/build/package:rule", "//my/build/package/subpackage:rule"));
    Truth8.assertThat(summary.getParentPackage(Path.of("my/build/package/subpackage")))
        .hasValue(Path.of("my/build/package"));
  }

  @Test
  public void testGetParentPackage_indirectParent() {
    QuerySummary summary =
        QuerySummaryImpl.create(
            createProtoForPackages("//my/build/package:rule", "//my/build/package/sub1/sub2:rule"));
    Truth8.assertThat(summary.getParentPackage(Path.of("my/build/package/sub1/sub2")))
        .hasValue(Path.of("my/build/package"));
  }

  @Test
  public void testBuildIncludes() throws IOException {
    QuerySummary qs =
        QuerySummaryImpl.create(QuerySpec.QueryStrategy.PLAIN, TestData.BUILDINCLUDES_QUERY.getQueryOutputPath().toFile());
    Label buildLabel = Label.of(TestData.ROOT_PACKAGE + "/buildincludes:BUILD");
    assertThat(qs.getSourceFilesMap()).containsKey(buildLabel);
    QueryData.SourceFile buildSrc = qs.getSourceFilesMap().get(buildLabel);
    assertThat(buildSrc.subincliudes())
        .containsExactly(Label.of(TestData.ROOT_PACKAGE + "/buildincludes:includes.bzl"));
    assertThat(qs.getReverseSubincludeMap())
        .containsExactly(
            TestData.ROOT.resolve("buildincludes/includes.bzl"),
            TestData.ROOT.resolve("buildincludes/BUILD"));
  }

  @Test
  public void getPackages_withEmptyPackage_containsEmptyPackage() throws IOException {
    QuerySummary qs = QuerySummaryImpl.create(QuerySpec.QueryStrategy.PLAIN, TestData.EMPTY_QUERY.getQueryOutputPath().toFile());
    assertThat(qs.getRulesMap()).isEmpty();
    assertThat(qs.getSourceFilesMap().keySet())
        .containsExactly(Label.of(TestData.ROOT_PACKAGE + "/empty:BUILD"));
    assertThat(qs.getPackages().size()).isEqualTo(1);
    assertThat(qs.getPackages().asPathSet()).containsExactly(TestData.ROOT.resolve("empty"));
  }

  @Test
  public void testCreate_proto() throws IOException {
    QuerySummary qs =
      QuerySummaryImpl.create(QuerySpec.QueryStrategy.PLAIN, TestData.JAVA_LIBRARY_NO_DEPS_QUERY.getQueryOutputPath().toFile());

    assertThat(qs.protoForSerializationOnly().getQueryStrategy()).isEqualTo(Query.Summary.QueryStrategy.QUERY_STRATEGY_PLAIN);
  }
}
