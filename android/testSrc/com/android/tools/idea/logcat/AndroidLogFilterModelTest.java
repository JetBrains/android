/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.idea.logcat;

import com.android.ddmlib.Log.LogLevel;
import com.intellij.diagnostic.logging.LogFilterModel;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;

public class AndroidLogFilterModelTest {

  private TestFilterModel myFilterModel;

  @Before
  public void setUp() throws Exception {
    myFilterModel = new TestFilterModel();
    myFilterModel.processingStarted();
  }

  @Test
  public void filterAcceptsCorrectLogcatLine() throws Exception {
    LogFilterModel.MyProcessingResult result = myFilterModel.processLine("01-23 12:34:56.789 1234-5678/? I/DummyTag: Dummy Message");
    assertThat(result.isApplicable()).isTrue();
  }

  @Test
  public void filterRejectsIncorrectLogcatLine() throws Exception {
    LogFilterModel.MyProcessingResult result = myFilterModel.processLine("--- INVALID LINE ---");
    assertThat(result.isApplicable()).isFalse();
  }

  @Test
  public void filterRejectsBelowMinimumLogLevelLine() throws Exception {
    myFilterModel.setMinimumLevel(LogLevel.ERROR);
    LogFilterModel.MyProcessingResult result = myFilterModel.processLine("01-23 12:34:56.789 1234-5678/? I/DummyTag: Dummy Message");
    assertThat(result.isApplicable()).isFalse();
  }

  @Test
  public void filterRejectsOldMessages() throws Exception {
    String dateOld = "01-22 12:34:56.789 1234-5678/? I/DummyTag: Dummy Message";
    String dateNow = "01-23 12:34:56.789 1234-5678/? I/DummyTag: Dummy Message";
    String dateNew = "01-24 12:34:56.789 1234-5678/? I/DummyTag: Dummy Message";

    myFilterModel.processLine(dateNow);
    myFilterModel.beginRejectingOldMessages();

    LogFilterModel.MyProcessingResult result = myFilterModel.processLine(dateOld);
    assertThat(result.isApplicable()).isFalse();

    result = myFilterModel.processLine(dateNew);
    assertThat(result.isApplicable()).isTrue();
  }

  @Test
  public void configuredFilterRejectsLinesThatDontMatch() throws Exception {
    PersistentAndroidLogFilters.FilterData filterData = new PersistentAndroidLogFilters.FilterData();
    filterData.setLogMessagePattern("Dummy Message");
    filterData.setLogTagPattern("DummyTag");

    myFilterModel.updateLogcatFilter(DefaultAndroidLogcatFilter.compile(filterData, "(Unused Name)"));

    LogFilterModel.MyProcessingResult result = myFilterModel.processLine("01-23 12:34:56.789 1234-5678/? I/DummyTag: Dummy Message");
    assertThat(result.isApplicable()).isTrue();

    result = myFilterModel.processLine("01-23 12:34:56.789 1234-5678/? I/DummyTag: Invalid Message");
    assertThat(result.isApplicable()).isFalse();

    result = myFilterModel.processLine("01-23 12:34:56.789 1234-5678/? I/InvalidTag: Dummy Message");
    assertThat(result.isApplicable()).isFalse();
  }

  @Test
  public void customPatternRejectsLinesThatDontMatch() throws Exception {
    myFilterModel.updateCustomPattern(Pattern.compile("^.+/DummyTag: Dummy Message$"));

    LogFilterModel.MyProcessingResult result = myFilterModel.processLine("01-23 12:34:56.789 1234-5678/? I/DummyTag: Dummy Message");
    assertThat(result.isApplicable()).isTrue();

    result = myFilterModel.processLine("01-23 12:34:56.789 1234-5678/? I/DummyTag: Invalid Message");
    assertThat(result.isApplicable()).isFalse();

    result = myFilterModel.processLine("01-23 12:34:56.789 1234-5678/? I/InvalidTag: Dummy Message");
    assertThat(result.isApplicable()).isFalse();
  }

  @Test
  public void filterCanMatchAgainstAnyLineInAMultiLineLog() throws Exception {
    PersistentAndroidLogFilters.FilterData filterData = new PersistentAndroidLogFilters.FilterData();

    String[] lines = "01-23 12:34:56.789 1234-5678/? I/DummyTag: line 1\n+ line 2\n+ line 3".split("\n");
    LogFilterModel.MyProcessingResult result;

    // Test multiline log against first line
    filterData.setLogMessagePattern("line 1");
    myFilterModel.updateLogcatFilter(DefaultAndroidLogcatFilter.compile(filterData, "(Unused Name)"));

    result = myFilterModel.processLine(lines[0]);
    assertThat(result.isApplicable()).isTrue();
    assert(result.getMessagePrefix() != null);
    assertThat(result.getMessagePrefix()).isEmpty();

    result = myFilterModel.processLine(lines[1]);
    assertThat(result.isApplicable()).isTrue();
    assert(result.getMessagePrefix() != null);
    assertThat(result.getMessagePrefix()).isEmpty();

    result = myFilterModel.processLine(lines[2]);
    assertThat(result.isApplicable()).isTrue();
    assert(result.getMessagePrefix() != null);
    assertThat(result.getMessagePrefix()).isEmpty();

    // Test multiline log against second line
    filterData.setLogMessagePattern("line 2");
    myFilterModel.updateLogcatFilter(DefaultAndroidLogcatFilter.compile(filterData, "(Unused Name)"));

    result = myFilterModel.processLine(lines[0]);
    assertThat(result.isApplicable()).isFalse();

    result = myFilterModel.processLine(lines[1]);
    assertThat(result.isApplicable()).isTrue();
    assert(result.getMessagePrefix() != null);
    assertThat(result.getMessagePrefix()).isEqualTo("01-23 12:34:56.789 1234-5678/? I/DummyTag: line 1\n");

    result = myFilterModel.processLine(lines[2]);
    assertThat(result.isApplicable()).isTrue();
    assert(result.getMessagePrefix() != null);
    assertThat(result.getMessagePrefix().isEmpty());

    // Test multiline log against third line
    filterData.setLogMessagePattern("line 3");
    myFilterModel.updateLogcatFilter(DefaultAndroidLogcatFilter.compile(filterData, "(Unused Name)"));

    result = myFilterModel.processLine(lines[0]);
    assertThat(result.isApplicable()).isFalse();

    result = myFilterModel.processLine(lines[1]);
    assertThat(result.isApplicable()).isFalse();

    result = myFilterModel.processLine(lines[2]);
    assertThat(result.isApplicable()).isTrue();
    assert(result.getMessagePrefix() != null);
    assertThat(result.getMessagePrefix()).isEqualTo("01-23 12:34:56.789 1234-5678/? I/DummyTag: line 1\n+ line 2\n");

    // Test multiline log against non-existent line
    filterData.setLogMessagePattern("line x");
    myFilterModel.updateLogcatFilter(DefaultAndroidLogcatFilter.compile(filterData, "(Unused Name)"));

    result = myFilterModel.processLine(lines[0]);
    assertThat(result.isApplicable()).isFalse();

    result = myFilterModel.processLine(lines[1]);
    assertThat(result.isApplicable()).isFalse();

    result = myFilterModel.processLine(lines[2]);
    assertThat(result.isApplicable()).isFalse();
  }

  private static class TestFilterModel extends AndroidLogFilterModel {

    @NotNull private LogLevel myMinimumLevel = LogLevel.VERBOSE; // Allow all messages by default

    public void setMinimumLevel(@NotNull LogLevel logLevel) {
      myMinimumLevel = logLevel;
    }

    @Override
    protected void saveConfiguredFilterName(String filterName) {
      // No need to save during unit tests
    }

    @Override
    protected void saveLogLevel(String logLevelName) {
      // No need to save during unit tests
    }

    @Override
    public String getSelectedLogLevelName() {
      return myMinimumLevel.getStringValue();
    }

  }
}
