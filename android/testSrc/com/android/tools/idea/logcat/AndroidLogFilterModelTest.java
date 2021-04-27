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

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.testFramework.UsefulTestCase.assertThrows;

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.intellij.diagnostic.logging.LogFilterModel;
import java.time.Instant;
import java.time.ZoneId;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Test;

public class AndroidLogFilterModelTest {
  private AndroidLogFilterModel myFilterModel;
  private AndroidLogcatPreferences myPreferences;

  @Before
  public void setUp() throws Exception {
    myPreferences = new AndroidLogcatPreferences();
    myFilterModel = new AndroidLogFilterModel(new AndroidLogcatFormatter(ZoneId.of("America/Los_Angeles"), myPreferences), myPreferences);
    myFilterModel.processingStarted();
  }

  @Test
  public void processLine_validLine_accepted() {
    LogFilterModel.MyProcessingResult result = myFilterModel.processLine(new LogLineBuilder().build());

    assertThat(result.isApplicable()).isTrue();
  }

  @Test
  public void processLine_invalidLine_throws() {
    assertThrows(RuntimeException.class, () ->myFilterModel.processLine("--- INVALID LINE ---"));
  }

  @Test
  public void processLine_bellowLogLevel_rejected() {
    myPreferences.TOOL_WINDOW_LOG_LEVEL = LogLevel.ERROR.getStringValue();

    assertThat(myFilterModel.processLine(new LogLineBuilder().setLogLevel(LogLevel.ERROR).build()).isApplicable()).isTrue();
    assertThat(myFilterModel.processLine(new LogLineBuilder().setLogLevel(LogLevel.WARN).build()).isApplicable()).isFalse();
  }

  @Test
  public void processLine_oldMessage_rejected() {
    String dateOld = new LogLineBuilder().setTimestamp("2018-01-22T12:34:56.79Z").build();
    String dateNow = new LogLineBuilder().setTimestamp("2018-01-23T12:34:56.79Z").build();
    String dateNew = new LogLineBuilder().setTimestamp("2018-01-24T12:34:56.79Z").build();

    myFilterModel.processLine(dateNow);
    myFilterModel.beginRejectingOldMessages();

    LogFilterModel.MyProcessingResult result = myFilterModel.processLine(dateOld);
    assertThat(result.isApplicable()).isFalse();

    result = myFilterModel.processLine(dateNew);
    assertThat(result.isApplicable()).isTrue();
  }

  @Test
  public void processLine_doesNotMatchFilter_rejected() {
    PersistentAndroidLogFilters.FilterData filterData = new PersistentAndroidLogFilters.FilterData();
    filterData.setLogMessagePattern("Some Message");
    filterData.setLogTagPattern("SomeTag");
    myFilterModel.updateLogcatFilter(DefaultAndroidLogcatFilter.compile(filterData, "(Unused Name)"));
    LogLineBuilder builder = new LogLineBuilder();

    LogFilterModel.MyProcessingResult result = myFilterModel.processLine(builder.setTag("SomeTag").setMessage("Some Message").build());
    assertThat(result.isApplicable()).isTrue();

    result = myFilterModel.processLine(builder.setTag("SomeTag").setMessage("Invalid Message").build());
    assertThat(result.isApplicable()).isFalse();

    result = myFilterModel.processLine(builder.setTag("InvalidTag").setMessage("Some Message").build());
    assertThat(result.isApplicable()).isFalse();
  }

  @Test
  public void processLine_doesNotMatchCustomPattern_rejected() {
    myFilterModel.updateCustomPattern(Pattern.compile("^.+/SomeTag: Some Message$"));
    LogLineBuilder builder = new LogLineBuilder();

    LogFilterModel.MyProcessingResult result = myFilterModel.processLine(builder.setTag("SomeTag").setMessage("Some Message").build());
    assertThat(result.isApplicable()).isTrue();

    result = myFilterModel.processLine(builder.setTag("SomeTag").setMessage("Invalid Message").build());
    assertThat(result.isApplicable()).isFalse();

    result = myFilterModel.processLine(builder.setTag("InvalidTag").setMessage("Some Message").build());
    assertThat(result.isApplicable()).isFalse();
  }

  @Test
  public void processLine_filterCanMatchAgainstAnyLineInAMultiLineLog() {
    PersistentAndroidLogFilters.FilterData filterData = new PersistentAndroidLogFilters.FilterData();
    String line = new LogLineBuilder().setMessage("line 1\n+ line 2\n+ line 3").build();

    // Test multiline log against first line
    filterData.setLogMessagePattern("line 1");
    myFilterModel.updateLogcatFilter(DefaultAndroidLogcatFilter.compile(filterData, "(Unused Name)"));

    assertThat(myFilterModel.processLine(line).isApplicable()).isTrue();

    // Test multiline log against second line
    filterData.setLogMessagePattern("line 2");
    myFilterModel.updateLogcatFilter(DefaultAndroidLogcatFilter.compile(filterData, "(Unused Name)"));

    assertThat(myFilterModel.processLine(line).isApplicable()).isTrue();

    // Test multiline log against third line
    filterData.setLogMessagePattern("line 3");
    myFilterModel.updateLogcatFilter(DefaultAndroidLogcatFilter.compile(filterData, "(Unused Name)"));

    assertThat(myFilterModel.processLine(line).isApplicable()).isTrue();

    // Test multiline log against non-existent line
    filterData.setLogMessagePattern("line x");
    myFilterModel.updateLogcatFilter(DefaultAndroidLogcatFilter.compile(filterData, "(Unused Name)"));

    assertThat(myFilterModel.processLine(line).isApplicable()).isFalse();
  }

  private static class LogLineBuilder {
    private LogLevel myLogLevel = LogLevel.INFO;
    private String myTag = "Tag";
    private String myMessage = "";
    private Instant myTimestamp = Instant.ofEpochSecond(1534635551);

    LogLineBuilder setLogLevel(LogLevel logLevel) {
      myLogLevel = logLevel;
      return this;
    }

    LogLineBuilder setTag(String tag) {
      myTag = tag;
      return this;
    }

    LogLineBuilder setMessage(String message) {
      myMessage = message;
      return this;
    }

    LogLineBuilder setTimestamp(String timestamp) {
      myTimestamp = Instant.parse(timestamp);
      return this;
    }

    String build() {
      return LogcatJson.toJson(
        new LogCatMessage(new LogCatHeader(myLogLevel, 99, 99, "?", myTag, myTimestamp), myMessage));
    }
  }
}
