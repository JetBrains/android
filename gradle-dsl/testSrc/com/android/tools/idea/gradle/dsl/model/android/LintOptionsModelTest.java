/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.TestFileName;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.LintOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion;
import com.google.common.collect.ImmutableList;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemDependent;
import org.junit.Test;

/**
 * Tests for {@link LintOptionsModel}.
 */
public class LintOptionsModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseElements() throws Exception {
    writeToBuildFile(TestFile.TEXT);
    verifyLintOptions();
  }

  @Test
  public void testEditElements() throws Exception {
    writeToBuildFile(TestFile.TEXT);
    verifyLintOptions();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    lintOptions.abortOnError().setValue(false);
    lintOptions.absolutePaths().setValue(true);
    lintOptions.baseline().setValue("other-baseline.xml");
    lintOptions.check().getListValue("check-id-2").setValue("check-id-3");
    lintOptions.checkAllWarnings().setValue(false);
    lintOptions.checkDependencies().setValue(true);
    lintOptions.checkGeneratedSources().setValue(false);
    lintOptions.checkReleaseBuilds().setValue(true);
    lintOptions.checkTestSources().setValue(false);
    lintOptions.disable().getListValue("disable-id-2").setValue("disable-id-3");
    lintOptions.enable().getListValue("enable-id-2").setValue("enable-id-3");
    lintOptions.error().getListValue("error-id-1").setValue("error-id-3");
    lintOptions.explainIssues().setValue(false);
    lintOptions.fatal().getListValue("fatal-id-2").setValue("fatal-id-3");
    lintOptions.htmlOutput().setValue("other-html.output");
    lintOptions.htmlReport().setValue(false);
    lintOptions.ignore().getListValue("ignore-id-2").setValue("ignore-id-3");
    lintOptions.ignoreTestSources().setValue(true);
    lintOptions.ignoreWarnings().setValue(false);
    lintOptions.informational().getListValue("informational-id-1").setValue("informational-id-3");
    lintOptions.lintConfig().setValue("other-lint.config");
    lintOptions.noLines().setValue(true);
    lintOptions.quiet().setValue(false);
    lintOptions.sarifOutput().setValue("other-sarif.output");
    lintOptions.sarifReport().setValue(false);
    lintOptions.showAll().setValue(true);
    lintOptions.textOutput().setValue("other-text.output");
    lintOptions.textReport().setValue(false);
    lintOptions.warning().getListValue("warning-id-2").setValue("warning-id-3");
    lintOptions.warningsAsErrors().setValue(true);
    lintOptions.xmlOutput().setValue("other-xml.output");
    lintOptions.xmlReport().setValue(false);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.EDIT_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    lintOptions = android.lintOptions();
    assertEquals("abortOnError", Boolean.FALSE, lintOptions.abortOnError());
    assertEquals("absolutePaths", Boolean.TRUE, lintOptions.absolutePaths());
    assertEquals("baseline", "other-baseline.xml", lintOptions.baseline());
    assertEquals("check", ImmutableList.of("check-id-1", "check-id-3"), lintOptions.check());
    assertEquals("checkAllWarnings", Boolean.FALSE, lintOptions.checkAllWarnings());
    assertEquals("checkDependencies", Boolean.TRUE, lintOptions.checkDependencies());
    assertEquals("checkGeneratedSources", Boolean.FALSE, lintOptions.checkGeneratedSources());
    assertEquals("checkReleaseBuilds", Boolean.TRUE, lintOptions.checkReleaseBuilds());
    assertEquals("checkTestSources", Boolean.FALSE, lintOptions.checkTestSources());
    assertEquals("disable", ImmutableList.of("disable-id-1", "disable-id-3"), lintOptions.disable());
    assertEquals("enable", ImmutableList.of("enable-id-1", "enable-id-3"), lintOptions.enable());
    assertEquals("error", ImmutableList.of("error-id-3", "error-id-2"), lintOptions.error());
    assertEquals("explainIssues", Boolean.FALSE, lintOptions.explainIssues());
    assertEquals("fatal", ImmutableList.of("fatal-id-1", "fatal-id-3"), lintOptions.fatal());
    assertEquals("htmlOutput", "other-html.output", lintOptions.htmlOutput());
    assertEquals("htmlReport", Boolean.FALSE, lintOptions.htmlReport());
    assertEquals("ignore", ImmutableList.of("ignore-id-1", "ignore-id-3"), lintOptions.ignore());
    assertEquals("ignoreTestSources", Boolean.TRUE, lintOptions.ignoreTestSources());
    assertEquals("ignoreWarnings", Boolean.FALSE, lintOptions.ignoreWarnings());
    assertEquals("informational", ImmutableList.of("informational-id-3", "informational-id-2"), lintOptions.informational());
    assertEquals("lintConfig", "other-lint.config", lintOptions.lintConfig());
    assertEquals("noLines", Boolean.TRUE, lintOptions.noLines());
    assertEquals("quiet", Boolean.FALSE, lintOptions.quiet());
    assertEquals("sarifOutput", "other-sarif.output", lintOptions.sarifOutput());
    assertEquals("sarifReport", Boolean.FALSE, lintOptions.sarifReport());
    assertEquals("showAll", Boolean.TRUE, lintOptions.showAll());
    assertEquals("textOutput", "other-text.output", lintOptions.textOutput());
    assertEquals("textReport", Boolean.FALSE, lintOptions.textReport());
    assertEquals("warning", ImmutableList.of("warning-id-1", "warning-id-3"), lintOptions.warning());
    assertEquals("warningsAsErrors", Boolean.TRUE, lintOptions.warningsAsErrors());
    assertEquals("xmlOutput", "other-xml.output", lintOptions.xmlOutput());
    assertEquals("xmlReport", Boolean.FALSE, lintOptions.xmlReport());
  }

  @Test
  public void testAddElements() throws Exception {
    writeToBuildFile(TestFile.ADD_ELEMENTS);
    verifyNullLintOptions();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    lintOptions.abortOnError().setValue(true);
    lintOptions.absolutePaths().setValue(false);
    lintOptions.baseline().setValue("baseline.xml");
    lintOptions.check().addListValue().setValue("check-id-1");
    lintOptions.checkAllWarnings().setValue(true);
    lintOptions.checkDependencies().setValue(false);
    lintOptions.checkGeneratedSources().setValue(true);
    lintOptions.checkReleaseBuilds().setValue(false);
    lintOptions.checkTestSources().setValue(true);
    lintOptions.disable().addListValue().setValue("disable-id-1");
    lintOptions.enable().addListValue().setValue("enable-id-1");
    lintOptions.error().addListValue().setValue("error-id-1");
    lintOptions.explainIssues().setValue(true);
    lintOptions.fatal().addListValue().setValue("fatal-id-1");
    lintOptions.htmlOutput().setValue("html.output");
    lintOptions.htmlReport().setValue(false);
    lintOptions.ignore().addListValue().setValue("ignore-id-1");
    lintOptions.ignoreTestSources().setValue(false);
    lintOptions.ignoreWarnings().setValue(true);
    lintOptions.informational().addListValue().setValue("informational-id-1");
    lintOptions.lintConfig().setValue("lint.config");
    lintOptions.noLines().setValue(false);
    lintOptions.quiet().setValue(true);
    lintOptions.sarifOutput().setValue("sarif.output");
    lintOptions.sarifReport().setValue(true);
    lintOptions.showAll().setValue(false);
    lintOptions.textOutput().setValue("text.output");
    lintOptions.textReport().setValue(true);
    lintOptions.warning().addListValue().setValue("warning-id-1");
    lintOptions.warningsAsErrors().setValue(false);
    lintOptions.xmlOutput().setValue("xml.output");
    lintOptions.xmlReport().setValue(true);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    lintOptions = android.lintOptions();

    assertEquals("abortOnError", Boolean.TRUE, lintOptions.abortOnError());
    assertEquals("absolutePaths", Boolean.FALSE, lintOptions.absolutePaths());
    assertEquals("baseline", "baseline.xml", lintOptions.baseline());
    assertEquals("check", ImmutableList.of("check-id-1"), lintOptions.check());
    assertEquals("checkAllWarnings", Boolean.TRUE, lintOptions.checkAllWarnings());
    assertEquals("checkDependencies", Boolean.FALSE, lintOptions.checkDependencies());
    assertEquals("checkGeneratedSources", Boolean.TRUE, lintOptions.checkGeneratedSources());
    assertEquals("checkReleaseBuilds", Boolean.FALSE, lintOptions.checkReleaseBuilds());
    assertEquals("checkTestSources", Boolean.TRUE, lintOptions.checkTestSources());
    assertEquals("disable", ImmutableList.of("disable-id-1"), lintOptions.disable());
    assertEquals("enable", ImmutableList.of("enable-id-1"), lintOptions.enable());
    assertEquals("error", ImmutableList.of("error-id-1"), lintOptions.error());
    assertEquals("explainIssues", Boolean.TRUE, lintOptions.explainIssues());
    assertEquals("fatal", ImmutableList.of("fatal-id-1"), lintOptions.fatal());
    assertEquals("htmlOutput", "html.output", lintOptions.htmlOutput());
    assertEquals("htmlReport", Boolean.FALSE, lintOptions.htmlReport());
    assertEquals("ignore", ImmutableList.of("ignore-id-1"), lintOptions.ignore());
    assertEquals("ignoreTestSources", Boolean.FALSE, lintOptions.ignoreTestSources());
    assertEquals("ignoreWarnings", Boolean.TRUE, lintOptions.ignoreWarnings());
    assertEquals("informational", ImmutableList.of("informational-id-1"), lintOptions.informational());
    assertEquals("lintConfig", "lint.config", lintOptions.lintConfig());
    assertEquals("noLines", Boolean.FALSE, lintOptions.noLines());
    assertEquals("quiet", Boolean.TRUE, lintOptions.quiet());
    assertEquals("sarifOutput", "sarif.output", lintOptions.sarifOutput());
    assertEquals("sarifReport", Boolean.TRUE, lintOptions.sarifReport());
    assertEquals("showAll", Boolean.FALSE, lintOptions.showAll());
    assertEquals("textOutput", "text.output", lintOptions.textOutput());
    assertEquals("textReport", Boolean.TRUE, lintOptions.textReport());
    assertEquals("warning", ImmutableList.of("warning-id-1"), lintOptions.warning());
    assertEquals("warningsAsErrors", Boolean.FALSE, lintOptions.warningsAsErrors());
    assertEquals("xmlOutput", "xml.output", lintOptions.xmlOutput());
    assertEquals("xmlReport", Boolean.TRUE, lintOptions.xmlReport());
  }

  @Test
  public void testRemoveElements() throws Exception {
    writeToBuildFile(TestFile.TEXT);
    verifyLintOptions();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    checkForValidPsiElement(lintOptions, LintOptionsModelImpl.class);
    lintOptions.abortOnError().delete();
    lintOptions.absolutePaths().delete();
    lintOptions.baseline().delete();
    lintOptions.check().delete();
    lintOptions.checkAllWarnings().delete();
    lintOptions.checkDependencies().delete();
    lintOptions.checkGeneratedSources().delete();
    lintOptions.checkReleaseBuilds().delete();
    lintOptions.checkTestSources().delete();
    lintOptions.disable().delete();
    lintOptions.enable().delete();
    lintOptions.error().delete();
    lintOptions.explainIssues().delete();
    lintOptions.fatal().delete();
    lintOptions.htmlOutput().delete();
    lintOptions.htmlReport().delete();
    lintOptions.ignore().delete();
    lintOptions.ignoreTestSources().delete();
    lintOptions.ignoreWarnings().delete();
    lintOptions.informational().delete();
    lintOptions.lintConfig().delete();
    lintOptions.noLines().delete();
    lintOptions.quiet().delete();
    lintOptions.sarifOutput().delete();
    lintOptions.sarifReport().delete();
    lintOptions.showAll().delete();
    lintOptions.textOutput().delete();
    lintOptions.textReport().delete();
    lintOptions.warning().delete();
    lintOptions.warningsAsErrors().delete();
    lintOptions.xmlOutput().delete();
    lintOptions.xmlReport().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    lintOptions = android.lintOptions();
    checkForInvalidPsiElement(lintOptions, LintOptionsModelImpl.class);
    verifyNullLintOptions();
  }

  private void verifyLintOptions() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    assertEquals("abortOnError", Boolean.TRUE, lintOptions.abortOnError());
    assertEquals("absolutePaths", Boolean.FALSE, lintOptions.absolutePaths());
    assertEquals("baseline", "baseline.xml", lintOptions.baseline());
    assertEquals("check", ImmutableList.of("check-id-1", "check-id-2"), lintOptions.check());
    assertEquals("checkAllWarnings", Boolean.TRUE, lintOptions.checkAllWarnings());
    assertEquals("checkDependencies", Boolean.FALSE, lintOptions.checkDependencies());
    assertEquals("checkGeneratedSources", Boolean.TRUE, lintOptions.checkGeneratedSources());
    assertEquals("checkReleaseBuilds", Boolean.FALSE, lintOptions.checkReleaseBuilds());
    assertEquals("checkTestSources", Boolean.TRUE, lintOptions.checkTestSources());
    assertEquals("disable", ImmutableList.of("disable-id-1", "disable-id-2"), lintOptions.disable());
    assertEquals("enable", ImmutableList.of("enable-id-1", "enable-id-2"), lintOptions.enable());
    assertEquals("error", ImmutableList.of("error-id-1", "error-id-2"), lintOptions.error());
    assertEquals("explainIssues", Boolean.TRUE, lintOptions.explainIssues());
    assertEquals("fatal", ImmutableList.of("fatal-id-1", "fatal-id-2"), lintOptions.fatal());
    assertEquals("htmlOutput", "html.output", lintOptions.htmlOutput());
    assertEquals("htmlReport", Boolean.FALSE, lintOptions.htmlReport());
    assertEquals("ignore", ImmutableList.of("ignore-id-1", "ignore-id-2"), lintOptions.ignore());
    assertEquals("ignoreTestSources", Boolean.FALSE, lintOptions.ignoreTestSources());
    assertEquals("ignoreWarnings", Boolean.TRUE, lintOptions.ignoreWarnings());
    assertEquals("informational", ImmutableList.of("informational-id-1", "informational-id-2"), lintOptions.informational());
    assertEquals("lintConfig", "lint.config", lintOptions.lintConfig());
    assertEquals("noLines", Boolean.FALSE, lintOptions.noLines());
    assertEquals("quiet", Boolean.TRUE, lintOptions.quiet());
    assertEquals("sarifOutput", "sarif.output", lintOptions.sarifOutput());
    assertEquals("sarifReport", Boolean.TRUE, lintOptions.sarifReport());
    assertEquals("showAll", Boolean.FALSE, lintOptions.showAll());
    assertEquals("textOutput", "text.output", lintOptions.textOutput());
    assertEquals("textReport", Boolean.TRUE, lintOptions.textReport());
    assertEquals("warning", ImmutableList.of("warning-id-1", "warning-id-2"), lintOptions.warning());
    assertEquals("warningsAsErrors", Boolean.FALSE, lintOptions.warningsAsErrors());
    assertEquals("xmlOutput", "xml.output", lintOptions.xmlOutput());
    assertEquals("xmlReport", Boolean.TRUE, lintOptions.xmlReport());
  }

  private void verifyNullLintOptions() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    assertMissingProperty("abortOnError", lintOptions.abortOnError());
    assertMissingProperty("absolutePaths", lintOptions.absolutePaths());
    assertMissingProperty("baseline", lintOptions.baseline());
    assertMissingProperty("check", lintOptions.check());
    assertMissingProperty("checkAllWarnings", lintOptions.checkAllWarnings());
    assertMissingProperty("checkReleaseBuilds", lintOptions.checkReleaseBuilds());
    assertMissingProperty("disable", lintOptions.disable());
    assertMissingProperty("enable", lintOptions.enable());
    assertMissingProperty("error", lintOptions.error());
    assertMissingProperty("explainIssues", lintOptions.explainIssues());
    assertMissingProperty("fatal", lintOptions.fatal());
    assertMissingProperty("htmlOutput", lintOptions.htmlOutput());
    assertMissingProperty("htmlReport", lintOptions.htmlReport());
    assertMissingProperty("ignore", lintOptions.ignore());
    assertMissingProperty("ignoreWarnings", lintOptions.ignoreWarnings());
    assertMissingProperty("informational", lintOptions.informational());
    assertMissingProperty("lintConfig", lintOptions.lintConfig());
    assertMissingProperty("noLines", lintOptions.noLines());
    assertMissingProperty("quiet", lintOptions.quiet());
    assertMissingProperty("sarifOutput", lintOptions.sarifOutput());
    assertMissingProperty("sarifReport", lintOptions.sarifReport());
    assertMissingProperty("showAll", lintOptions.showAll());
    assertMissingProperty("textOutput", lintOptions.textOutput());
    assertMissingProperty("textReport", lintOptions.textReport());
    assertMissingProperty("warning", lintOptions.warning());
    assertMissingProperty("warningsAsErrors", lintOptions.warningsAsErrors());
    assertMissingProperty("xmlOutput", lintOptions.xmlOutput());
    assertMissingProperty("xmlReport", lintOptions.xmlReport());
  }

  @Test
  public void testRemoveOneOfElementsInTheList() throws Exception {
    writeToBuildFile(TestFile.REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    assertEquals("check", ImmutableList.of("check-id-1", "check-id-2"), lintOptions.check());
    assertEquals("disable", ImmutableList.of("disable-id-1", "disable-id-2"), lintOptions.disable());
    assertEquals("enable", ImmutableList.of("enable-id-1", "enable-id-2"), lintOptions.enable());
    assertEquals("error", ImmutableList.of("error-id-1", "error-id-2"), lintOptions.error());
    assertEquals("fatal", ImmutableList.of("fatal-id-1", "fatal-id-2"), lintOptions.fatal());
    assertEquals("ignore", ImmutableList.of("ignore-id-1", "ignore-id-2"), lintOptions.ignore());
    assertEquals("informational", ImmutableList.of("informational-id-1", "informational-id-2"), lintOptions.informational());
    assertEquals("warning", ImmutableList.of("warning-id-1", "warning-id-2"), lintOptions.warning());

    buildModel.getContext().setAgpVersion(AndroidGradlePluginVersion.Companion.parse("4.0.0"));

    lintOptions.check().getListValue("check-id-1").delete();
    lintOptions.disable().getListValue("disable-id-2").delete();
    lintOptions.enable().getListValue("enable-id-1").delete();
    lintOptions.error().getListValue("error-id-2").delete();
    lintOptions.fatal().getListValue("fatal-id-1").delete();
    lintOptions.ignore().getListValue("ignore-id-2").delete();
    lintOptions.informational().getListValue("informational-id-1").delete();
    lintOptions.warning().getListValue("warning-id-1").delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    lintOptions = android.lintOptions();
    assertEquals("check", ImmutableList.of("check-id-2"), lintOptions.check());
    assertEquals("disable", ImmutableList.of("disable-id-1"), lintOptions.disable());
    assertEquals("enable", ImmutableList.of("enable-id-2"), lintOptions.enable());
    assertEquals("error", ImmutableList.of("error-id-1"), lintOptions.error());
    assertEquals("fatal", ImmutableList.of("fatal-id-2"), lintOptions.fatal());
    assertEquals("ignore", ImmutableList.of("ignore-id-1"), lintOptions.ignore());
    assertEquals("informational", ImmutableList.of("informational-id-2"), lintOptions.informational());
    assertEquals("warning", ImmutableList.of("warning-id-2"), lintOptions.warning());
  }

  @Test
  public void testRemoveOnlyElementsInTheList() throws Exception {
    writeToBuildFile(TestFile.REMOVE_ONLY_ELEMENTS_IN_THE_LIST);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    checkForValidPsiElement(lintOptions, LintOptionsModelImpl.class);
    assertEquals("check", ImmutableList.of("check-id"), lintOptions.check());
    assertEquals("disable", ImmutableList.of("disable-id"), lintOptions.disable());
    assertEquals("enable", ImmutableList.of("enable-id"), lintOptions.enable());
    assertEquals("error", ImmutableList.of("error-id"), lintOptions.error());
    assertEquals("fatal", ImmutableList.of("fatal-id"), lintOptions.fatal());
    assertEquals("ignore", ImmutableList.of("ignore-id"), lintOptions.ignore());
    assertEquals("informational", ImmutableList.of("informational-id"), lintOptions.informational());
    assertEquals("warning", ImmutableList.of("warning-id"), lintOptions.warning());

    lintOptions.check().getListValue("check-id").delete();
    lintOptions.disable().getListValue("disable-id").delete();
    lintOptions.enable().getListValue("enable-id").delete();
    lintOptions.error().getListValue("error-id").delete();
    lintOptions.fatal().getListValue("fatal-id").delete();
    lintOptions.ignore().getListValue("ignore-id").delete();
    lintOptions.informational().getListValue("informational-id").delete();
    lintOptions.warning().getListValue("warning-id").delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    lintOptions = android.lintOptions();
    checkForInvalidPsiElement(lintOptions, LintOptionsModelImpl.class);
    assertMissingProperty("check", lintOptions.check());
    assertMissingProperty("disable", lintOptions.disable());
    assertMissingProperty("enable", lintOptions.enable());
    assertMissingProperty("error", lintOptions.error());
    assertMissingProperty("fatal", lintOptions.fatal());
    assertMissingProperty("ignore", lintOptions.ignore());
    assertMissingProperty("informational", lintOptions.informational());
    assertMissingProperty("warning", lintOptions.warning());
  }

  enum TestFile implements TestFileName {
    TEXT("lintOptionsText"),
    ADD_ELEMENTS("addElements"),
    ADD_ELEMENTS_EXPECTED("addElementsExpected"),
    EDIT_ELEMENTS_EXPECTED("editElementsExpected"),
    REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST("removeOneOfElementsInTheList"),
    REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST_EXPECTED("removeOneOfElementsInTheListExpected"),
    REMOVE_ONLY_ELEMENTS_IN_THE_LIST("removeOnlyElementsInTheList"),
    ;
    @NotNull private @SystemDependent String path;
    TestFile(@NotNull @SystemDependent String path) {
      this.path = path;
    }

    @NotNull
    @Override
    public File toFile(@NotNull @SystemDependent String basePath, @NotNull String extension) {
      return TestFileName.super.toFile(basePath + "/lintOptionsModel/" + path, extension);
    }
  }
}
