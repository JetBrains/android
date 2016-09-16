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
package com.android.tools.idea.sdk;

import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestBase;
import org.mockito.Mockito;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public final class SdkLoggerIntegrationTest extends AndroidTestBase {
  private static void verifyParsing(String message, String title, String description, int progress) {
    SdkLoggerIntegration logger = process(message);
    verify(logger).setTitle(title);
    verify(logger).setDescription(description);
    verify(logger).setProgress(progress);
    verify(logger, never()).lineAdded("");
  }

  private static SdkLoggerIntegration process(String message) {
    SdkLoggerIntegration mock = Mockito.spy(new DummySdkLoggerIntegration());
    mock.info("%s\n", message);
    return mock;
  }

  private static void verifyIgnored(String message) {
    SdkLoggerIntegration mock = process(message);
    verify(mock, never()).setTitle("");
    verify(mock, never()).setDescription("");
    verify(mock, never()).lineAdded("");
    verify(mock, never()).setProgress(0);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
      IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    super.tearDown();
  }

  public void testIgnoredStrings() {
    verifyIgnored("Refresh Sources:");
    verifyIgnored("  Fetching http://dl-ssl.google.com/android/repository/addons_list-2.xml");
    verifyIgnored("  Validate XML");
    verifyIgnored("  Parse XML");
    verifyIgnored("  Fetched Add-ons List successfully");
    verifyIgnored("-------------------------------");
    verifyIgnored("License id: android-sdk-license-cafebabe");
    verifyIgnored("Used by: \n" +
                  " - Android SDK Platform-tools, revision 21\n" +
                  "  - SDK Platform Android 5.0, API 21, revision 1");
  }

  public void testDownloadProgress() {
    SdkLoggerIntegration mock = process("  Downloading SDK Platform Android 5.0, API 21, revision 1");
    verify(mock).setTitle("Downloading SDK Platform Android 5.0, API 21, revision 1");
    verify(mock, never()).setDescription("");
    verify(mock, never()).setProgress(0);
    verify(mock, never()).lineAdded("");

    verifyParsing("     (14%, 4674 KiB/s, 12 seconds left)", "", "14%, 4674 KiB/s, 12 seconds left", 14);
  }

  public void testUnpackingProgress() {
    verifyParsing("  Unzipping SDK Platform Android 5.0, API 21, revision 1 (1%)",
                  "Unzipping SDK Platform Android 5.0, API 21, revision 1",
                  "1%", 1);
    verifyParsing("  Unzipping SDK Platform Android 5.0, API 21, revision 1 (54%)",
                  "Unzipping SDK Platform Android 5.0, API 21, revision 1", "54%", 54);
  }

  /**
   * This concrete class is needed as Mockito cannot spy on abstract classes.
   */
  private static class DummySdkLoggerIntegration extends SdkLoggerIntegration {
    @Override
    protected void setProgress(int progress) {
      // Needed for mock object
    }

    @Override
    protected void setDescription(String description) {
      // Needed for mock object
    }

    @Override
    protected void setTitle(String title) {
      // Needed for mock object
    }

    @Override
    protected void lineAdded(String string) {
      // Needed for mock object
    }
  }
}