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

import com.google.common.truth.Truth8;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.query.Query.SourceFile;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class QuerySummaryTest {

  private String targetName(String buildTarget) {
    return buildTarget.substring(buildTarget.indexOf(':') + 1);
  }

  @Test
  public void testCreate_javaLibrary_noDeps() throws IOException {
    QuerySummary qs =
        QuerySummary.create(TestData.JAVA_LIBRARY_NO_DEPS_QUERY.getQueryOutputPath().toFile());
    Label nodeps = Label.of(TestData.ROOT_PACKAGE + "/nodeps:nodeps");
    assertThat(qs.getRulesMap().keySet()).containsExactly(nodeps);
    Query.Rule rule = qs.getRulesMap().get(nodeps);
    assertThat(rule.getRuleClass()).isEqualTo("java_library");
    assertThat(rule.getSourcesCount()).isEqualTo(1);
    assertThat(targetName(rule.getSources(0))).isEqualTo("TestClassNoDeps.java");
    assertThat(rule.getDepsCount()).isEqualTo(0);
    assertThat(rule.getIdlSourcesCount()).isEqualTo(0);
    assertThat(qs.getSourceFilesMap().keySet())
        .containsExactly(
            Label.of(TestData.ROOT_PACKAGE + "/nodeps:TestClassNoDeps.java"),
            Label.of(TestData.ROOT_PACKAGE + "/nodeps:BUILD"));
  }

  @Test
  public void testCreate_ccLibrary_noDeps() throws Exception {
    QuerySummary qs = QuerySummary.create(TestData.CC_LIBRARY_QUERY.getQueryOutputPath().toFile());
    Label cc = Label.of(TestData.ROOT_PACKAGE + "/cc:cc");
    assertThat(qs.getRulesMap().keySet()).containsExactly(cc);
    Query.Rule rule = qs.getRulesMap().get(cc);
    assertThat(rule.getRuleClass()).isEqualTo("cc_library");
    assertThat(rule.getSourcesCount()).isEqualTo(1);
    assertThat(targetName(rule.getSources(0))).isEqualTo("TestClass.cc");
    assertThat(rule.getHdrsCount()).isEqualTo(1);
    assertThat(targetName(rule.getHdrs(0))).isEqualTo("TestClass.h");
    assertThat(rule.getDepsCount()).isEqualTo(0);
    assertThat(qs.getSourceFilesMap().keySet())
        .containsExactly(
            Label.of(TestData.ROOT_PACKAGE + "/cc:TestClass.cc"),
            Label.of(TestData.ROOT_PACKAGE + "/cc:TestClass.h"),
            Label.of(TestData.ROOT_PACKAGE + "/cc:BUILD"));
    assertThat(rule.getCoptsList()).containsExactly("-w");
  }

  @Test
  public void testCreate_androidLibrary_manifest() throws IOException {
    QuerySummary qs = QuerySummary.create(TestData.ANDROID_LIB_QUERY.getQueryOutputPath().toFile());
    Label android = Label.of(TestData.ROOT_PACKAGE + "/android:android");
    assertThat(qs.getRulesMap().keySet()).contains(android);
    Query.Rule rule = qs.getRulesMap().get(android);
    assertThat(rule.getManifest())
        .isEqualTo(android.siblingWithName("AndroidManifest.xml").toString());
  }

  @Test
  public void testGetPackages_singleRule() {
    QuerySummary summary = QuerySummary.create(createProtoForPackages("//my/build/package:rule"));
    assertThat(summary.getPackages().asPathSet()).containsExactly(Path.of("my/build/package"));
  }

  @Test
  public void testGetPackages_multiRule_onePackage() {
    QuerySummary summary =
        QuerySummary.create(
            createProtoForPackages("//my/build/package:rule1", "//my/build/package:rule2"));
    assertThat(summary.getPackages().asPathSet()).containsExactly(Path.of("my/build/package"));
  }

  @Test
  public void testGetPackages_multiRule_multiPackage() {
    QuerySummary summary =
        QuerySummary.create(
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
    QuerySummary summary = QuerySummary.create(createProtoForPackages("//my/build/package:rule"));
    Truth8.assertThat(summary.getParentPackage(Path.of("my/build/package"))).isEmpty();
  }

  @Test
  public void testGetParentPackage_directParent() {
    QuerySummary summary =
        QuerySummary.create(
            createProtoForPackages(
                "//my/build/package:rule", "//my/build/package/subpackage:rule"));
    Truth8.assertThat(summary.getParentPackage(Path.of("my/build/package/subpackage")))
        .hasValue(Path.of("my/build/package"));
  }

  @Test
  public void testGetParentPackage_indirectParent() {
    QuerySummary summary =
        QuerySummary.create(
            createProtoForPackages("//my/build/package:rule", "//my/build/package/sub1/sub2:rule"));
    Truth8.assertThat(summary.getParentPackage(Path.of("my/build/package/sub1/sub2")))
        .hasValue(Path.of("my/build/package"));
  }

  @Test
  public void testBuildIncludes() throws IOException {
    QuerySummary qs =
        QuerySummary.create(TestData.BUILDINCLUDES_QUERY.getQueryOutputPath().toFile());
    Label buildLabel = Label.of(TestData.ROOT_PACKAGE + "/buildincludes:BUILD");
    assertThat(qs.getSourceFilesMap()).containsKey(buildLabel);
    SourceFile buildSrc = qs.getSourceFilesMap().get(buildLabel);
    assertThat(buildSrc.getSubincludeList())
        .containsExactly(TestData.ROOT_PACKAGE + "/buildincludes:includes.bzl");
    assertThat(qs.getReverseSubincludeMap())
        .containsExactly(
            TestData.ROOT.resolve("buildincludes/includes.bzl"),
            TestData.ROOT.resolve("buildincludes/BUILD"));
  }

  @Test
  public void getPackages_withEmptyPackage_containsEmptyPackage() throws IOException {
    QuerySummary qs = QuerySummary.create(TestData.EMPTY_QUERY.getQueryOutputPath().toFile());
    assertThat(qs.getRulesMap()).isEmpty();
    assertThat(qs.getSourceFilesMap().keySet())
        .containsExactly(Label.of(TestData.ROOT_PACKAGE + "/empty:BUILD"));
    assertThat(qs.getPackages().size()).isEqualTo(1);
    assertThat(qs.getPackages().asPathSet()).containsExactly(TestData.ROOT.resolve("empty"));
  }
}
