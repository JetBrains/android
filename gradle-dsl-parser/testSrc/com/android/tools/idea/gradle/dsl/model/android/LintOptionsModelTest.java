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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.LintOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;

import java.io.File;

/**
 * Tests for {@link LintOptionsModel}.
 */
public class LintOptionsModelTest extends GradleFileModelTestCase {
  private static final String LINT_OPTIONS_TEXT = "android {\n" +
                                                  "  lintOptions {\n" +
                                                  "    abortOnError true\n" +
                                                  "    absolutePaths false\n" +
                                                  "    check 'check-id-1', 'check-id-2'\n" +
                                                  "    checkAllWarnings true\n" +
                                                  "    checkReleaseBuilds false\n" +
                                                  "    disable 'disable-id-1', 'disable-id-2'\n" +
                                                  "    enable 'enable-id-1', 'enable-id-2'\n" +
                                                  "    error 'error-id-1', 'error-id-2'\n" +
                                                  "    explainIssues true\n" +
                                                  "    fatal 'fatal-id-1', 'fatal-id-2'\n" +
                                                  "    htmlOutput file('html.output')\n" +
                                                  "    htmlReport false\n" +
                                                  "    ignore 'ignore-id-1', 'ignore-id-2'\n" +
                                                  "    ignoreWarnings true\n" +
                                                  "    lintConfig file('lint.config')\n" +
                                                  "    noLines false\n" +
                                                  "    quiet true\n" +
                                                  "    showAll false\n" +
                                                  "    textOutput file('text.output')\n" +
                                                  "    textReport true\n" +
                                                  "    warning 'warning-id-1', 'warning-id-2'\n" +
                                                  "    warningsAsErrors false\n" +
                                                  "    xmlOutput file('xml.output')\n" +
                                                  "    xmlReport true\n" +
                                                  "  }\n" +
                                                  "}";

  public void testParseElements() throws Exception {
    writeToBuildFile(LINT_OPTIONS_TEXT);
    verifyLintOptions();
  }

  public void testEditElements() throws Exception {
    writeToBuildFile(LINT_OPTIONS_TEXT);
    verifyLintOptions();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    lintOptions.setAbortOnError(false);
    lintOptions.setAbsolutePaths(true);
    lintOptions.replaceCheck("check-id-2", "check-id-3");
    lintOptions.setCheckAllWarnings(false);
    lintOptions.setCheckReleaseBuilds(true);
    lintOptions.replaceDisable("disable-id-2", "disable-id-3");
    lintOptions.replaceEnable("enable-id-2", "enable-id-3");
    lintOptions.replaceError("error-id-2", "error-id-3");
    lintOptions.setExplainIssues(false);
    lintOptions.replaceFatal("fatal-id-2", "fatal-id-3");
    lintOptions.setHtmlOutput(new File("other-html.output"));
    lintOptions.setHtmlReport(true);
    lintOptions.replaceIgnore("ignore-id-2", "ignore-id-3");
    lintOptions.setIgnoreWarnings(false);
    lintOptions.setLintConfig(new File("other-lint.config"));
    lintOptions.setNoLines(true);
    lintOptions.setQuiet(false);
    lintOptions.setShowAll(true);
    lintOptions.setTextOutput(new File("other-text.output"));
    lintOptions.setTextReport(false);
    lintOptions.replaceWarning("warning-id-2", "warning-id-3");
    lintOptions.setWarningsAsErrors(true);
    lintOptions.setXmlOutput(new File("other-xml.output"));
    lintOptions.setXmlReport(false);

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    lintOptions = android.lintOptions();
    assertEquals("abortOnError", Boolean.FALSE, lintOptions.abortOnError());
    assertEquals("absolutePaths", Boolean.TRUE, lintOptions.absolutePaths());
    assertEquals("check", ImmutableList.of("check-id-1", "check-id-3"), lintOptions.check());
    assertEquals("checkAllWarnings", Boolean.FALSE, lintOptions.checkAllWarnings());
    assertEquals("checkReleaseBuilds", Boolean.TRUE, lintOptions.checkReleaseBuilds());
    assertEquals("disable", ImmutableList.of("disable-id-1", "disable-id-3"), lintOptions.disable());
    assertEquals("enable", ImmutableList.of("enable-id-1", "enable-id-3"), lintOptions.enable());
    assertEquals("error", ImmutableList.of("error-id-1", "error-id-3"), lintOptions.error());
    assertEquals("explainIssues", Boolean.FALSE, lintOptions.explainIssues());
    assertEquals("fatal", ImmutableList.of("fatal-id-1", "fatal-id-3"), lintOptions.fatal());
    assertEquals("htmlOutput", new File("other-html.output"), lintOptions.htmlOutput());
    assertEquals("htmlReport", Boolean.TRUE, lintOptions.htmlReport());
    assertEquals("ignore", ImmutableList.of("ignore-id-1", "ignore-id-3"), lintOptions.ignore());
    assertEquals("ignoreWarnings", Boolean.FALSE, lintOptions.ignoreWarnings());
    assertEquals("lintConfig", new File("other-lint.config"), lintOptions.lintConfig());
    assertEquals("noLines", Boolean.TRUE, lintOptions.noLines());
    assertEquals("quiet", Boolean.FALSE, lintOptions.quiet());
    assertEquals("showAll", Boolean.TRUE, lintOptions.showAll());
    assertEquals("textOutput", new File("other-text.output"), lintOptions.textOutput());
    assertEquals("textReport", Boolean.FALSE, lintOptions.textReport());
    assertEquals("warning", ImmutableList.of("warning-id-1", "warning-id-3"), lintOptions.warning());
    assertEquals("warningsAsErrors", Boolean.TRUE, lintOptions.warningsAsErrors());
    assertEquals("xmlOutput", new File("other-xml.output"), lintOptions.xmlOutput());
    assertEquals("xmlReport", Boolean.FALSE, lintOptions.xmlReport());
  }

  public void testAddElements() throws Exception {
    String text = "android {\n" +
                  "  lintOptions {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);
    verifyNullLintOptions();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    lintOptions.setAbortOnError(true);
    lintOptions.setAbsolutePaths(false);
    lintOptions.addCheck("check-id-1");
    lintOptions.setCheckAllWarnings(true);
    lintOptions.setCheckReleaseBuilds(false);
    lintOptions.addDisable("disable-id-1");
    lintOptions.addEnable("enable-id-1");
    lintOptions.addError("error-id-1");
    lintOptions.setExplainIssues(true);
    lintOptions.addFatal("fatal-id-1");
    lintOptions.setHtmlOutput(new File("html.output"));
    lintOptions.setHtmlReport(false);
    lintOptions.addIgnore("ignore-id-1");
    lintOptions.setIgnoreWarnings(true);
    lintOptions.setLintConfig(new File("lint.config"));
    lintOptions.setNoLines(false);
    lintOptions.setQuiet(true);
    lintOptions.setShowAll(false);
    lintOptions.setTextOutput(new File("text.output"));
    lintOptions.setTextReport(true);
    lintOptions.addWarning("warning-id-1");
    lintOptions.setWarningsAsErrors(false);
    lintOptions.setXmlOutput(new File("xml.output"));
    lintOptions.setXmlReport(true);

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    lintOptions = android.lintOptions();

    assertEquals("abortOnError", Boolean.TRUE, lintOptions.abortOnError());
    assertEquals("absolutePaths", Boolean.FALSE, lintOptions.absolutePaths());
    assertEquals("check", ImmutableList.of("check-id-1"), lintOptions.check());
    assertEquals("checkAllWarnings", Boolean.TRUE, lintOptions.checkAllWarnings());
    assertEquals("checkReleaseBuilds", Boolean.FALSE, lintOptions.checkReleaseBuilds());
    assertEquals("disable", ImmutableList.of("disable-id-1"), lintOptions.disable());
    assertEquals("enable", ImmutableList.of("enable-id-1"), lintOptions.enable());
    assertEquals("error", ImmutableList.of("error-id-1"), lintOptions.error());
    assertEquals("explainIssues", Boolean.TRUE, lintOptions.explainIssues());
    assertEquals("fatal", ImmutableList.of("fatal-id-1"), lintOptions.fatal());
    assertEquals("htmlOutput", new File("html.output"), lintOptions.htmlOutput());
    assertEquals("htmlReport", Boolean.FALSE, lintOptions.htmlReport());
    assertEquals("ignore", ImmutableList.of("ignore-id-1"), lintOptions.ignore());
    assertEquals("ignoreWarnings", Boolean.TRUE, lintOptions.ignoreWarnings());
    assertEquals("lintConfig", new File("lint.config"), lintOptions.lintConfig());
    assertEquals("noLines", Boolean.FALSE, lintOptions.noLines());
    assertEquals("quiet", Boolean.TRUE, lintOptions.quiet());
    assertEquals("showAll", Boolean.FALSE, lintOptions.showAll());
    assertEquals("textOutput", new File("text.output"), lintOptions.textOutput());
    assertEquals("textReport", Boolean.TRUE, lintOptions.textReport());
    assertEquals("warning", ImmutableList.of("warning-id-1"), lintOptions.warning());
    assertEquals("warningsAsErrors", Boolean.FALSE, lintOptions.warningsAsErrors());
    assertEquals("xmlOutput", new File("xml.output"), lintOptions.xmlOutput());
    assertEquals("xmlReport", Boolean.TRUE, lintOptions.xmlReport());
  }

  public void testRemoveElements() throws Exception {
    writeToBuildFile(LINT_OPTIONS_TEXT);
    verifyLintOptions();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    checkForValidPsiElement(lintOptions, LintOptionsModelImpl.class);
    lintOptions.removeAbortOnError();
    lintOptions.removeAbsolutePaths();
    lintOptions.removeAllCheck();
    lintOptions.removeCheckAllWarnings();
    lintOptions.removeCheckReleaseBuilds();
    lintOptions.removeAllDisable();
    lintOptions.removeAllEnable();
    lintOptions.removeAllError();
    lintOptions.removeExplainIssues();
    lintOptions.removeAllFatal();
    lintOptions.removeHtmlOutput();
    lintOptions.removeHtmlReport();
    lintOptions.removeAllIgnore();
    lintOptions.removeIgnoreWarnings();
    lintOptions.removeLintConfig();
    lintOptions.removeNoLines();
    lintOptions.removeQuiet();
    lintOptions.removeShowAll();
    lintOptions.removeTextOutput();
    lintOptions.removeTextReport();
    lintOptions.removeAllWarning();
    lintOptions.removeWarningsAsErrors();
    lintOptions.removeXmlOutput();
    lintOptions.removeXmlReport();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    lintOptions = android.lintOptions();
    checkForInValidPsiElement(lintOptions, LintOptionsModelImpl.class);
    verifyNullLintOptions();
  }

  private void verifyLintOptions() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    assertEquals("abortOnError", Boolean.TRUE, lintOptions.abortOnError());
    assertEquals("absolutePaths", Boolean.FALSE, lintOptions.absolutePaths());
    assertEquals("check", ImmutableList.of("check-id-1", "check-id-2"), lintOptions.check());
    assertEquals("checkAllWarnings", Boolean.TRUE, lintOptions.checkAllWarnings());
    assertEquals("checkReleaseBuilds", Boolean.FALSE, lintOptions.checkReleaseBuilds());
    assertEquals("disable", ImmutableList.of("disable-id-1", "disable-id-2"), lintOptions.disable());
    assertEquals("enable", ImmutableList.of("enable-id-1", "enable-id-2"), lintOptions.enable());
    assertEquals("error", ImmutableList.of("error-id-1", "error-id-2"), lintOptions.error());
    assertEquals("explainIssues", Boolean.TRUE, lintOptions.explainIssues());
    assertEquals("fatal", ImmutableList.of("fatal-id-1", "fatal-id-2"), lintOptions.fatal());
    assertEquals("htmlOutput", new File("html.output"), lintOptions.htmlOutput());
    assertEquals("htmlReport", Boolean.FALSE, lintOptions.htmlReport());
    assertEquals("ignore", ImmutableList.of("ignore-id-1", "ignore-id-2"), lintOptions.ignore());
    assertEquals("ignoreWarnings", Boolean.TRUE, lintOptions.ignoreWarnings());
    assertEquals("lintConfig", new File("lint.config"), lintOptions.lintConfig());
    assertEquals("noLines", Boolean.FALSE, lintOptions.noLines());
    assertEquals("quiet", Boolean.TRUE, lintOptions.quiet());
    assertEquals("showAll", Boolean.FALSE, lintOptions.showAll());
    assertEquals("textOutput", new File("text.output"), lintOptions.textOutput());
    assertEquals("textReport", Boolean.TRUE, lintOptions.textReport());
    assertEquals("warning", ImmutableList.of("warning-id-1", "warning-id-2"), lintOptions.warning());
    assertEquals("warningsAsErrors", Boolean.FALSE, lintOptions.warningsAsErrors());
    assertEquals("xmlOutput", new File("xml.output"), lintOptions.xmlOutput());
    assertEquals("xmlReport", Boolean.TRUE, lintOptions.xmlReport());
  }

  private void verifyNullLintOptions() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    assertNull("abortOnError", lintOptions.abortOnError());
    assertNull("absolutePaths", lintOptions.absolutePaths());
    assertNull("check", lintOptions.check());
    assertNull("checkAllWarnings", lintOptions.checkAllWarnings());
    assertNull("checkReleaseBuilds", lintOptions.checkReleaseBuilds());
    assertNull("disable", lintOptions.disable());
    assertNull("enable", lintOptions.enable());
    assertNull("error", lintOptions.error());
    assertNull("explainIssues", lintOptions.explainIssues());
    assertNull("fatal", lintOptions.fatal());
    assertNull("htmlOutput", lintOptions.htmlOutput());
    assertNull("htmlReport", lintOptions.htmlReport());
    assertNull("ignore", lintOptions.ignore());
    assertNull("ignoreWarnings", lintOptions.ignoreWarnings());
    assertNull("lintConfig", lintOptions.lintConfig());
    assertNull("noLines", lintOptions.noLines());
    assertNull("quiet", lintOptions.quiet());
    assertNull("showAll", lintOptions.showAll());
    assertNull("textOutput", lintOptions.textOutput());
    assertNull("textReport", lintOptions.textReport());
    assertNull("warning", lintOptions.warning());
    assertNull("warningsAsErrors", lintOptions.warningsAsErrors());
    assertNull("xmlOutput", lintOptions.xmlOutput());
    assertNull("xmlReport", lintOptions.xmlReport());
  }

  public void testRemoveOneOfElementsInTheList() throws Exception {
    String text = "android {\n" +
                  "  lintOptions {\n" +
                  "    check 'check-id-1', 'check-id-2'\n" +
                  "    disable 'disable-id-1', 'disable-id-2'\n" +
                  "    enable 'enable-id-1', 'enable-id-2'\n" +
                  "    error 'error-id-1', 'error-id-2'\n" +
                  "    fatal 'fatal-id-1', 'fatal-id-2'\n" +
                  "    ignore 'ignore-id-1', 'ignore-id-2'\n" +
                  "    warning 'warning-id-1', 'warning-id-2'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

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
    assertEquals("warning", ImmutableList.of("warning-id-1", "warning-id-2"), lintOptions.warning());

    lintOptions.removeCheck("check-id-1");
    lintOptions.removeDisable("disable-id-2");
    lintOptions.removeEnable("enable-id-1");
    lintOptions.removeError("error-id-2");
    lintOptions.removeFatal("fatal-id-1");
    lintOptions.removeIgnore("ignore-id-2");
    lintOptions.removeWarning("warning-id-1");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    lintOptions = android.lintOptions();
    assertEquals("check", ImmutableList.of("check-id-2"), lintOptions.check());
    assertEquals("disable", ImmutableList.of("disable-id-1"), lintOptions.disable());
    assertEquals("enable", ImmutableList.of("enable-id-2"), lintOptions.enable());
    assertEquals("error", ImmutableList.of("error-id-1"), lintOptions.error());
    assertEquals("fatal", ImmutableList.of("fatal-id-2"), lintOptions.fatal());
    assertEquals("ignore", ImmutableList.of("ignore-id-1"), lintOptions.ignore());
    assertEquals("warning", ImmutableList.of("warning-id-2"), lintOptions.warning());
  }

  public void testRemoveOnlyElementsInTheList() throws Exception {
    String text = "android {\n" +
                  "  lintOptions {\n" +
                  "    check 'check-id'\n" +
                  "    disable 'disable-id'\n" +
                  "    enable 'enable-id'\n" +
                  "    error 'error-id'\n" +
                  "    fatal 'fatal-id'\n" +
                  "    ignore 'ignore-id'\n" +
                  "    warning 'warning-id'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

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
    assertEquals("warning", ImmutableList.of("warning-id"), lintOptions.warning());

    lintOptions.removeCheck("check-id");
    lintOptions.removeDisable("disable-id");
    lintOptions.removeEnable("enable-id");
    lintOptions.removeError("error-id");
    lintOptions.removeFatal("fatal-id");
    lintOptions.removeIgnore("ignore-id");
    lintOptions.removeWarning("warning-id");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    lintOptions = android.lintOptions();
    checkForInValidPsiElement(lintOptions, LintOptionsModelImpl.class);
    assertNull("check", lintOptions.check());
    assertNull("disable", lintOptions.disable());
    assertNull("enable", lintOptions.enable());
    assertNull("error", lintOptions.error());
    assertNull("fatal", lintOptions.fatal());
    assertNull("ignore", lintOptions.ignore());
    assertNull("warning", lintOptions.warning());
  }
}
