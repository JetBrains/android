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
import com.android.tools.idea.gradle.dsl.api.android.LintModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion;
import com.google.common.collect.ImmutableList;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemDependent;
import org.junit.Test;

/**
 * Tests for {@link LintModel}.
 */
public class LintModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseElements() throws Exception {
    writeToBuildFile(TestFile.PARSE_TEXT);
    verifyLintOptions();
  }

  @Test
  public void testEditElements() throws Exception {
    writeToBuildFile(TestFile.PARSE_TEXT);
    verifyLintOptions();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintModel lint = android.lint();
    lint.abortOnError().setValue(false);
    lint.absolutePaths().setValue(true);
    lint.baseline().setValue("other-baseline.xml");
    lint.checkOnly().getListValue("checkOnly-id-2").setValue("checkOnly-id-3");
    lint.checkAllWarnings().setValue(false);
    lint.checkDependencies().setValue(true);
    lint.checkGeneratedSources().setValue(false);
    lint.checkReleaseBuilds().setValue(true);
    lint.checkTestSources().setValue(false);
    lint.disable().getListValue("disable-id-2").setValue("disable-id-3");
    lint.enable().getListValue("enable-id-2").setValue("enable-id-3");
    lint.error().getListValue("error-id-1").setValue("error-id-3");
    lint.explainIssues().setValue(false);
    lint.fatal().getListValue("fatal-id-2").setValue("fatal-id-3");
    lint.htmlOutput().setValue("other-html.output");
    lint.htmlReport().setValue(false);
    lint.ignore().getListValue("ignore-id-2").setValue("ignore-id-3");
    lint.ignoreTestSources().setValue(true);
    lint.ignoreWarnings().setValue(false);
    lint.informational().getListValue("informational-id-1").setValue("informational-id-3");
    lint.lintConfig().setValue("other-lint.config");
    lint.noLines().setValue(true);
    lint.quiet().setValue(false);
    lint.showAll().setValue(true);
    lint.textOutput().setValue("other-text.output");
    lint.textReport().setValue(false);
    lint.warning().getListValue("warning-id-2").setValue("warning-id-3");
    lint.warningsAsErrors().setValue(true);
    lint.xmlOutput().setValue("other-xml.output");
    lint.xmlReport().setValue(false);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.EDIT_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    lint = android.lint();
    assertEquals("abortOnError", Boolean.FALSE, lint.abortOnError());
    assertEquals("absolutePaths", Boolean.TRUE, lint.absolutePaths());
    assertEquals("baseline", "other-baseline.xml", lint.baseline());
    assertEquals("checkOnly", ImmutableList.of("checkOnly-id-1", "checkOnly-id-3"), lint.checkOnly());
    assertEquals("checkAllWarnings", Boolean.FALSE, lint.checkAllWarnings());
    assertEquals("checkDependencies", Boolean.TRUE, lint.checkDependencies());
    assertEquals("checkGeneratedSources", Boolean.FALSE, lint.checkGeneratedSources());
    assertEquals("checkReleaseBuilds", Boolean.TRUE, lint.checkReleaseBuilds());
    assertEquals("checkTestSources", Boolean.FALSE, lint.checkTestSources());
    assertEquals("disable", ImmutableList.of("disable-id-1", "disable-id-3"), lint.disable());
    assertEquals("enable", ImmutableList.of("enable-id-1", "enable-id-3"), lint.enable());
    assertEquals("error", ImmutableList.of("error-id-3", "error-id-2"), lint.error());
    assertEquals("explainIssues", Boolean.FALSE, lint.explainIssues());
    assertEquals("fatal", ImmutableList.of("fatal-id-1", "fatal-id-3"), lint.fatal());
    assertEquals("htmlOutput", "other-html.output", lint.htmlOutput());
    assertEquals("htmlReport", Boolean.FALSE, lint.htmlReport());
    assertEquals("ignore", ImmutableList.of("ignore-id-1", "ignore-id-3"), lint.ignore());
    assertEquals("ignoreTestSources", Boolean.TRUE, lint.ignoreTestSources());
    assertEquals("ignoreWarnings", Boolean.FALSE, lint.ignoreWarnings());
    assertEquals("informational", ImmutableList.of("informational-id-3", "informational-id-2"), lint.informational());
    assertEquals("lintConfig", "other-lint.config", lint.lintConfig());
    assertEquals("noLines", Boolean.TRUE, lint.noLines());
    assertEquals("quiet", Boolean.FALSE, lint.quiet());
    assertEquals("showAll", Boolean.TRUE, lint.showAll());
    assertEquals("textOutput", "other-text.output", lint.textOutput());
    assertEquals("textReport", Boolean.FALSE, lint.textReport());
    assertEquals("warning", ImmutableList.of("warning-id-1", "warning-id-3"), lint.warning());
    assertEquals("warningsAsErrors", Boolean.TRUE, lint.warningsAsErrors());
    assertEquals("xmlOutput", "other-xml.output", lint.xmlOutput());
    assertEquals("xmlReport", Boolean.FALSE, lint.xmlReport());
  }

  @Test
  public void testAddElements() throws Exception {
    writeToBuildFile(TestFile.ADD_ELEMENTS);
    verifyNullLintOptions();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintModel lint = android.lint();
    lint.abortOnError().setValue(true);
    lint.absolutePaths().setValue(false);
    lint.baseline().setValue("baseline.xml");
    lint.checkOnly().addListValue().setValue("checkOnly-id-1");
    lint.checkAllWarnings().setValue(true);
    lint.checkDependencies().setValue(false);
    lint.checkGeneratedSources().setValue(true);
    lint.checkReleaseBuilds().setValue(false);
    lint.checkTestSources().setValue(true);
    lint.disable().addListValue().setValue("disable-id-1");
    lint.enable().addListValue().setValue("enable-id-1");
    lint.error().addListValue().setValue("error-id-1");
    lint.explainIssues().setValue(true);
    lint.fatal().addListValue().setValue("fatal-id-1");
    lint.htmlOutput().setValue("html.output");
    lint.htmlReport().setValue(false);
    lint.ignore().addListValue().setValue("ignore-id-1");
    lint.ignoreTestSources().setValue(false);
    lint.ignoreWarnings().setValue(true);
    lint.informational().addListValue().setValue("informational-id-1");
    lint.lintConfig().setValue("lint.config");
    lint.noLines().setValue(false);
    lint.quiet().setValue(true);
    lint.showAll().setValue(false);
    lint.textOutput().setValue("text.output");
    lint.textReport().setValue(true);
    lint.warning().addListValue().setValue("warning-id-1");
    lint.warningsAsErrors().setValue(false);
    lint.xmlOutput().setValue("xml.output");
    lint.xmlReport().setValue(true);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    lint = android.lint();

    assertEquals("abortOnError", Boolean.TRUE, lint.abortOnError());
    assertEquals("absolutePaths", Boolean.FALSE, lint.absolutePaths());
    assertEquals("baseline", "baseline.xml", lint.baseline());
    assertEquals("checkOnly", ImmutableList.of("checkOnly-id-1"), lint.checkOnly());
    assertEquals("checkAllWarnings", Boolean.TRUE, lint.checkAllWarnings());
    assertEquals("checkDependencies", Boolean.FALSE, lint.checkDependencies());
    assertEquals("checkGeneratedSources", Boolean.TRUE, lint.checkGeneratedSources());
    assertEquals("checkReleaseBuilds", Boolean.FALSE, lint.checkReleaseBuilds());
    assertEquals("checkTestSources", Boolean.TRUE, lint.checkTestSources());
    assertEquals("disable", ImmutableList.of("disable-id-1"), lint.disable());
    assertEquals("enable", ImmutableList.of("enable-id-1"), lint.enable());
    assertEquals("error", ImmutableList.of("error-id-1"), lint.error());
    assertEquals("explainIssues", Boolean.TRUE, lint.explainIssues());
    assertEquals("fatal", ImmutableList.of("fatal-id-1"), lint.fatal());
    assertEquals("htmlOutput", "html.output", lint.htmlOutput());
    assertEquals("htmlReport", Boolean.FALSE, lint.htmlReport());
    assertEquals("ignore", ImmutableList.of("ignore-id-1"), lint.ignore());
    assertEquals("ignoreTestSources", Boolean.FALSE, lint.ignoreTestSources());
    assertEquals("ignoreWarnings", Boolean.TRUE, lint.ignoreWarnings());
    assertEquals("informational", ImmutableList.of("informational-id-1"), lint.informational());
    assertEquals("lintConfig", "lint.config", lint.lintConfig());
    assertEquals("noLines", Boolean.FALSE, lint.noLines());
    assertEquals("quiet", Boolean.TRUE, lint.quiet());
    assertEquals("showAll", Boolean.FALSE, lint.showAll());
    assertEquals("textOutput", "text.output", lint.textOutput());
    assertEquals("textReport", Boolean.TRUE, lint.textReport());
    assertEquals("warning", ImmutableList.of("warning-id-1"), lint.warning());
    assertEquals("warningsAsErrors", Boolean.FALSE, lint.warningsAsErrors());
    assertEquals("xmlOutput", "xml.output", lint.xmlOutput());
    assertEquals("xmlReport", Boolean.TRUE, lint.xmlReport());
  }

  @Test
  public void testRemoveElements() throws Exception {
    writeToBuildFile(TestFile.PARSE_TEXT);
    verifyLintOptions();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintModel lint = android.lint();
    checkForValidPsiElement(lint, LintModelImpl.class);
    lint.abortOnError().delete();
    lint.absolutePaths().delete();
    lint.baseline().delete();
    lint.checkOnly().delete();
    lint.checkAllWarnings().delete();
    lint.checkDependencies().delete();
    lint.checkGeneratedSources().delete();
    lint.checkReleaseBuilds().delete();
    lint.checkTestSources().delete();
    lint.disable().delete();
    lint.enable().delete();
    lint.error().delete();
    lint.explainIssues().delete();
    lint.fatal().delete();
    lint.htmlOutput().delete();
    lint.htmlReport().delete();
    lint.ignore().delete();
    lint.ignoreTestSources().delete();
    lint.ignoreWarnings().delete();
    lint.informational().delete();
    lint.lintConfig().delete();
    lint.noLines().delete();
    lint.quiet().delete();
    lint.showAll().delete();
    lint.textOutput().delete();
    lint.textReport().delete();
    lint.warning().delete();
    lint.warningsAsErrors().delete();
    lint.xmlOutput().delete();
    lint.xmlReport().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    lint = android.lint();
    checkForInvalidPsiElement(lint, LintModelImpl.class);
    verifyNullLintOptions();
  }

  private void verifyLintOptions() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    LintModel lint = android.lint();
    assertEquals("abortOnError", Boolean.TRUE, lint.abortOnError());
    assertEquals("absolutePaths", Boolean.FALSE, lint.absolutePaths());
    assertEquals("baseline", "baseline.xml", lint.baseline());
    assertEquals("checkOnly", ImmutableList.of("checkOnly-id-1", "checkOnly-id-2"), lint.checkOnly());
    assertEquals("checkAllWarnings", Boolean.TRUE, lint.checkAllWarnings());
    assertEquals("checkDependencies", Boolean.FALSE, lint.checkDependencies());
    assertEquals("checkGeneratedSources", Boolean.TRUE, lint.checkGeneratedSources());
    assertEquals("checkReleaseBuilds", Boolean.FALSE, lint.checkReleaseBuilds());
    assertEquals("checkTestSources", Boolean.TRUE, lint.checkTestSources());
    assertEquals("disable", ImmutableList.of("disable-id-1", "disable-id-2"), lint.disable());
    assertEquals("enable", ImmutableList.of("enable-id-1", "enable-id-2"), lint.enable());
    assertEquals("error", ImmutableList.of("error-id-1", "error-id-2"), lint.error());
    assertEquals("explainIssues", Boolean.TRUE, lint.explainIssues());
    assertEquals("fatal", ImmutableList.of("fatal-id-1", "fatal-id-2"), lint.fatal());
    assertEquals("htmlOutput", "html.output", lint.htmlOutput());
    assertEquals("htmlReport", Boolean.FALSE, lint.htmlReport());
    assertEquals("ignore", ImmutableList.of("ignore-id-1", "ignore-id-2"), lint.ignore());
    assertEquals("ignoreTestSources", Boolean.FALSE, lint.ignoreTestSources());
    assertEquals("ignoreWarnings", Boolean.TRUE, lint.ignoreWarnings());
    assertEquals("informational", ImmutableList.of("informational-id-1", "informational-id-2"), lint.informational());
    assertEquals("lintConfig", "lint.config", lint.lintConfig());
    assertEquals("noLines", Boolean.FALSE, lint.noLines());
    assertEquals("quiet", Boolean.TRUE, lint.quiet());
    assertEquals("showAll", Boolean.FALSE, lint.showAll());
    assertEquals("textOutput", "text.output", lint.textOutput());
    assertEquals("textReport", Boolean.TRUE, lint.textReport());
    assertEquals("warning", ImmutableList.of("warning-id-1", "warning-id-2"), lint.warning());
    assertEquals("warningsAsErrors", Boolean.FALSE, lint.warningsAsErrors());
    assertEquals("xmlOutput", "xml.output", lint.xmlOutput());
    assertEquals("xmlReport", Boolean.TRUE, lint.xmlReport());
  }

  private void verifyNullLintOptions() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    LintModel lint = android.lint();
    assertMissingProperty("abortOnError", lint.abortOnError());
    assertMissingProperty("absolutePaths", lint.absolutePaths());
    assertMissingProperty("baseline", lint.baseline());
    assertMissingProperty("checkOnly", lint.checkOnly());
    assertMissingProperty("checkAllWarnings", lint.checkAllWarnings());
    assertMissingProperty("checkReleaseBuilds", lint.checkReleaseBuilds());
    assertMissingProperty("disable", lint.disable());
    assertMissingProperty("enable", lint.enable());
    assertMissingProperty("error", lint.error());
    assertMissingProperty("explainIssues", lint.explainIssues());
    assertMissingProperty("fatal", lint.fatal());
    assertMissingProperty("htmlOutput", lint.htmlOutput());
    assertMissingProperty("htmlReport", lint.htmlReport());
    assertMissingProperty("ignore", lint.ignore());
    assertMissingProperty("ignoreWarnings", lint.ignoreWarnings());
    assertMissingProperty("informational", lint.informational());
    assertMissingProperty("lintConfig", lint.lintConfig());
    assertMissingProperty("noLines", lint.noLines());
    assertMissingProperty("quiet", lint.quiet());
    assertMissingProperty("showAll", lint.showAll());
    assertMissingProperty("textOutput", lint.textOutput());
    assertMissingProperty("textReport", lint.textReport());
    assertMissingProperty("warning", lint.warning());
    assertMissingProperty("warningsAsErrors", lint.warningsAsErrors());
    assertMissingProperty("xmlOutput", lint.xmlOutput());
    assertMissingProperty("xmlReport", lint.xmlReport());
  }

  @Test
  public void testRemoveOneOfElementsInTheList() throws Exception {
    writeToBuildFile(TestFile.REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintModel lint = android.lint();
    assertEquals("checkOnly", ImmutableList.of("checkOnly-id-1", "checkOnly-id-2"), lint.checkOnly());
    assertEquals("disable", ImmutableList.of("disable-id-1", "disable-id-2"), lint.disable());
    assertEquals("enable", ImmutableList.of("enable-id-1", "enable-id-2"), lint.enable());
    assertEquals("error", ImmutableList.of("error-id-1", "error-id-2"), lint.error());
    assertEquals("fatal", ImmutableList.of("fatal-id-1", "fatal-id-2"), lint.fatal());
    assertEquals("ignore", ImmutableList.of("ignore-id-1", "ignore-id-2"), lint.ignore());
    assertEquals("informational", ImmutableList.of("informational-id-1", "informational-id-2"), lint.informational());
    assertEquals("warning", ImmutableList.of("warning-id-1", "warning-id-2"), lint.warning());

    buildModel.getContext().setAgpVersion(AndroidGradlePluginVersion.Companion.parse("4.0.0"));

    lint.checkOnly().getListValue("checkOnly-id-1").delete();
    lint.disable().getListValue("disable-id-2").delete();
    lint.enable().getListValue("enable-id-1").delete();
    lint.error().getListValue("error-id-2").delete();
    lint.fatal().getListValue("fatal-id-1").delete();
    lint.ignore().getListValue("ignore-id-2").delete();
    lint.informational().getListValue("informational-id-1").delete();
    lint.warning().getListValue("warning-id-1").delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    lint = android.lint();
    assertEquals("checkOnly", ImmutableList.of("checkOnly-id-2"), lint.checkOnly());
    assertEquals("disable", ImmutableList.of("disable-id-1"), lint.disable());
    assertEquals("enable", ImmutableList.of("enable-id-2"), lint.enable());
    assertEquals("error", ImmutableList.of("error-id-1"), lint.error());
    assertEquals("fatal", ImmutableList.of("fatal-id-2"), lint.fatal());
    assertEquals("ignore", ImmutableList.of("ignore-id-1"), lint.ignore());
    assertEquals("informational", ImmutableList.of("informational-id-2"), lint.informational());
    assertEquals("warning", ImmutableList.of("warning-id-2"), lint.warning());
  }

  @Test
  public void testRemoveOnlyElementsInTheList() throws Exception {
    writeToBuildFile(TestFile.REMOVE_ONLY_ELEMENTS_IN_THE_LIST);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintModel lint = android.lint();
    checkForValidPsiElement(lint, LintModelImpl.class);
    assertEquals("checkOnly", ImmutableList.of("checkOnly-id"), lint.checkOnly());
    assertEquals("disable", ImmutableList.of("disable-id"), lint.disable());
    assertEquals("enable", ImmutableList.of("enable-id"), lint.enable());
    assertEquals("error", ImmutableList.of("error-id"), lint.error());
    assertEquals("fatal", ImmutableList.of("fatal-id"), lint.fatal());
    assertEquals("ignore", ImmutableList.of("ignore-id"), lint.ignore());
    assertEquals("informational", ImmutableList.of("informational-id"), lint.informational());
    assertEquals("warning", ImmutableList.of("warning-id"), lint.warning());

    lint.checkOnly().getListValue("checkOnly-id").delete();
    lint.disable().getListValue("disable-id").delete();
    lint.enable().getListValue("enable-id").delete();
    lint.error().getListValue("error-id").delete();
    lint.fatal().getListValue("fatal-id").delete();
    lint.ignore().getListValue("ignore-id").delete();
    lint.informational().getListValue("informational-id").delete();
    lint.warning().getListValue("warning-id").delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    lint = android.lint();
    checkForInvalidPsiElement(lint, LintModelImpl.class);
    assertMissingProperty("checkOnly", lint.checkOnly());
    assertMissingProperty("disable", lint.disable());
    assertMissingProperty("enable", lint.enable());
    assertMissingProperty("error", lint.error());
    assertMissingProperty("fatal", lint.fatal());
    assertMissingProperty("ignore", lint.ignore());
    assertMissingProperty("informational", lint.informational());
    assertMissingProperty("warning", lint.warning());
  }

  enum TestFile implements TestFileName {
    PARSE_TEXT("parseText"),
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
      return TestFileName.super.toFile(basePath + "/lintModel/" + path, extension);
    }
  }
}
