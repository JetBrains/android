/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * Tests for {@link com.android.tools.idea.gradle.project.PreSyncChecks}
 */
public class PreSyncChecksTest extends IdeaTestCase {
  public void testHasEmptySettingsFileWithNotExistingFile() throws Exception {
    assertFalse(PreSyncChecks.hasEmptySettingsFile(myProject));
  }

  public void testHasEmptySettingsFileWithEmptyFile() throws IOException {
    writeToGradleSettingsFile("");
    assertTrue(PreSyncChecks.hasEmptySettingsFile(myProject));
  }

  public void testHasEmptySettingsFileWithSpaces() throws IOException {
    writeToGradleSettingsFile("    \n ");
    assertTrue(PreSyncChecks.hasEmptySettingsFile(myProject));
  }

  public void testHasEmptySettingsFileWithComment() throws IOException {
    writeToGradleSettingsFile("// include: \"app\"");
    assertTrue(PreSyncChecks.hasEmptySettingsFile(myProject));
  }

  public void testHasEmptySettingsFileWithOneModule() throws IOException {
    writeToGradleSettingsFile("include \":app\"");
    assertFalse(PreSyncChecks.hasEmptySettingsFile(myProject));
  }

  public void testHasEmptySettingsFileWithOneModuleInParenthesis() throws IOException {
    writeToGradleSettingsFile("include(\":app\")");
    assertFalse(PreSyncChecks.hasEmptySettingsFile(myProject));
  }

  public void testHasEmptySettingsFileWithTwoModulesInOneInclude() throws IOException {
    writeToGradleSettingsFile("include ':app', ':myapplication'");
    assertFalse(PreSyncChecks.hasEmptySettingsFile(myProject));
  }

  private void writeToGradleSettingsFile(@NotNull String text) throws IOException {
    File settingsFilePath = getGradleSettingsFilePath();
    FileUtil.writeToFile(settingsFilePath, text);
  }

  @NotNull
  private File getGradleSettingsFilePath() {
    return new File(myProject.getBasePath(), SdkConstants.FN_SETTINGS_GRADLE);
  }
}
