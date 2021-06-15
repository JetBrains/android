/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.tools.idea.logcat.LogcatHeaderFormat.TimestampFormat.EPOCH;
import static com.android.tools.idea.logcat.LogcatHeaderFormat.TimestampFormat.NONE;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.logcat.LogcatHeaderFormat.TimestampFormat;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import java.time.ZoneId;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class ConfigureLogcatHeaderDialogTest {
  private final AndroidLogcatPreferences myLogcatPreferences = new AndroidLogcatPreferences();
  private Application myApplication;
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private final LogcatHeaderFormatBuilder myFormatBuilder = new LogcatHeaderFormatBuilder();
  private final ZoneId myTimeZone = ZoneId.of("America/Los_Angeles");
  private ConfigureLogcatHeaderDialog myDialog;

  @Before
  public void initDialog() {
    myApplication = ApplicationManager.getApplication();
    myApplication.invokeAndWait(() -> myDialog = new ConfigureLogcatHeaderDialog(myRule.getProject(), myLogcatPreferences, myTimeZone));
  }

  @After
  public void disposeOfDialog() {
    myApplication.invokeAndWait(() -> Disposer.dispose(myDialog.getDisposable()));
  }

  @Test
  public void configureLogcatHeaderDialog_default() {
    myApplication.invokeAndWait(() -> {
      ConfigureLogcatHeaderDialog dialog = new ConfigureLogcatHeaderDialog(myRule.getProject(), myLogcatPreferences, myTimeZone);

      assertThat(dialog.getShowDateAndTimeCheckBox().isSelected()).isTrue();
      assertThat(dialog.getShowAsSecondsSinceEpochCheckBox().isSelected()).isFalse();
      assertThat(dialog.getShowProcessAndThreadIdsCheckBox().isSelected()).isTrue();
      assertThat(dialog.getShowPackageNameCheckBox().isSelected()).isTrue();
      assertThat(dialog.getShowTagCheckBox().isSelected()).isTrue();
      assertThat(dialog.getSampleLabel().getText())
        .isEqualTo("2018-02-06 14:16:28.555 123-456/com.android.sample I/SampleTag: This is a sample message");

      Disposer.dispose(dialog.getDisposable());
    });
  }

  @Test
  public void configureLogcatHeaderDialog_doNotShowTimestamp() {
    myApplication.invokeAndWait(() -> {
      myLogcatPreferences.LOGCAT_HEADER_FORMAT = myFormatBuilder.setTimestampFormat(NONE).build();
      ConfigureLogcatHeaderDialog dialog = new ConfigureLogcatHeaderDialog(myRule.getProject(), myLogcatPreferences, myTimeZone);

      assertThat(dialog.getShowDateAndTimeCheckBox().isSelected()).isFalse();
      assertThat(dialog.getShowAsSecondsSinceEpochCheckBox().isSelected()).isFalse();
      assertThat(dialog.getShowProcessAndThreadIdsCheckBox().isSelected()).isTrue();
      assertThat(dialog.getShowPackageNameCheckBox().isSelected()).isTrue();
      assertThat(dialog.getShowTagCheckBox().isSelected()).isTrue();
      assertThat(dialog.getSampleLabel().getText())
        .isEqualTo("123-456/com.android.sample I/SampleTag: This is a sample message");

      Disposer.dispose(dialog.getDisposable());
    });
  }

  @Test
  public void configureLogcatHeaderDialog_showTimestampAsEpoch() {
    myApplication.invokeAndWait(() -> {
      myLogcatPreferences.LOGCAT_HEADER_FORMAT = myFormatBuilder.setTimestampFormat(EPOCH).build();
      ConfigureLogcatHeaderDialog dialog = new ConfigureLogcatHeaderDialog(myRule.getProject(), myLogcatPreferences, myTimeZone);

      assertThat(dialog.getShowDateAndTimeCheckBox().isSelected()).isTrue();
      assertThat(dialog.getShowAsSecondsSinceEpochCheckBox().isSelected()).isTrue();
      assertThat(dialog.getShowProcessAndThreadIdsCheckBox().isSelected()).isTrue();
      assertThat(dialog.getShowPackageNameCheckBox().isSelected()).isTrue();
      assertThat(dialog.getShowTagCheckBox().isSelected()).isTrue();
      assertThat(dialog.getSampleLabel().getText())
        .isEqualTo("1517955388.555 123-456/com.android.sample I/SampleTag: This is a sample message");

      Disposer.dispose(dialog.getDisposable());
    });
  }

  @Test
  public void configureLogcatHeaderDialog_doNotShowProcessId() {
    myApplication.invokeAndWait(() -> {
      myLogcatPreferences.LOGCAT_HEADER_FORMAT = myFormatBuilder.setShowProcessId(false).build();
      ConfigureLogcatHeaderDialog dialog = new ConfigureLogcatHeaderDialog(myRule.getProject(), myLogcatPreferences, myTimeZone);

      assertThat(dialog.getShowDateAndTimeCheckBox().isSelected()).isTrue();
      assertThat(dialog.getShowAsSecondsSinceEpochCheckBox().isSelected()).isFalse();
      assertThat(dialog.getShowProcessAndThreadIdsCheckBox().isSelected()).isFalse();
      assertThat(dialog.getShowPackageNameCheckBox().isSelected()).isTrue();
      assertThat(dialog.getShowTagCheckBox().isSelected()).isTrue();
      assertThat(dialog.getSampleLabel().getText())
        .isEqualTo("2018-02-06 14:16:28.555 com.android.sample I/SampleTag: This is a sample message");

      Disposer.dispose(dialog.getDisposable());
    });
  }

  @Test
  public void configureLogcatHeaderDialog_doNotShowPackageName() {
    myApplication.invokeAndWait(() -> {
      myLogcatPreferences.LOGCAT_HEADER_FORMAT = myFormatBuilder.setShowPackageName(false).build();
      ConfigureLogcatHeaderDialog dialog = new ConfigureLogcatHeaderDialog(myRule.getProject(), myLogcatPreferences, myTimeZone);

      assertThat(dialog.getShowDateAndTimeCheckBox().isSelected()).isTrue();
      assertThat(dialog.getShowAsSecondsSinceEpochCheckBox().isSelected()).isFalse();
      assertThat(dialog.getShowProcessAndThreadIdsCheckBox().isSelected()).isTrue();
      assertThat(dialog.getShowPackageNameCheckBox().isSelected()).isFalse();
      assertThat(dialog.getShowTagCheckBox().isSelected()).isTrue();
      assertThat(dialog.getSampleLabel().getText())
        .isEqualTo("2018-02-06 14:16:28.555 123-456 I/SampleTag: This is a sample message");

      Disposer.dispose(dialog.getDisposable());
    });
  }

  @Test
  public void configureLogcatHeaderDialog_doNotShowTag() {
    myApplication.invokeAndWait(() -> {
      myLogcatPreferences.LOGCAT_HEADER_FORMAT = myFormatBuilder.setShowTag(false).build();
      ConfigureLogcatHeaderDialog dialog = new ConfigureLogcatHeaderDialog(myRule.getProject(), myLogcatPreferences, myTimeZone);

      assertThat(dialog.getShowDateAndTimeCheckBox().isSelected()).isTrue();
      assertThat(dialog.getShowAsSecondsSinceEpochCheckBox().isSelected()).isFalse();
      assertThat(dialog.getShowProcessAndThreadIdsCheckBox().isSelected()).isTrue();
      assertThat(dialog.getShowPackageNameCheckBox().isSelected()).isTrue();
      assertThat(dialog.getShowTagCheckBox().isSelected()).isFalse();
      assertThat(dialog.getSampleLabel().getText())
        .isEqualTo("2018-02-06 14:16:28.555 123-456/com.android.sample I: This is a sample message");

      Disposer.dispose(dialog.getDisposable());
    });
  }

  @Test
  public void unselectingShowDateAndTimeCheckBox() {
    myDialog.getShowDateAndTimeCheckBox().setSelected(false);

    assertThat(myDialog.getSampleLabel().getText()).isEqualTo("123-456/com.android.sample I/SampleTag: This is a sample message");
    assertThat(myDialog.getFormat()).isEqualTo(myFormatBuilder.setTimestampFormat(NONE).build());
  }

  @Test
  public void selectingShowAsSecondsSinceEpochCheckBox() {
    myDialog.getShowAsSecondsSinceEpochCheckBox().setSelected(true);

    assertThat(myDialog.getSampleLabel().getText())
      .isEqualTo("1517955388.555 123-456/com.android.sample I/SampleTag: This is a sample message");
    assertThat(myDialog.getFormat()).isEqualTo(myFormatBuilder.setTimestampFormat(EPOCH).build());
  }

  @Test
  public void unselectingShowProcessAndThreadIdsCheckBox() {
    myDialog.getShowProcessAndThreadIdsCheckBox().setSelected(false);

    assertThat(myDialog.getSampleLabel().getText())
      .isEqualTo("2018-02-06 14:16:28.555 com.android.sample I/SampleTag: This is a sample message");
    assertThat(myDialog.getFormat()).isEqualTo(myFormatBuilder.setShowProcessId(false).build());
  }

  @Test
  public void unselectingShowPackageNameCheckBox() {
    myDialog.getShowPackageNameCheckBox().setSelected(false);

    assertThat(myDialog.getSampleLabel().getText()).isEqualTo("2018-02-06 14:16:28.555 123-456 I/SampleTag: This is a sample message");
    assertThat(myDialog.getFormat()).isEqualTo(myFormatBuilder.setShowPackageName(false).build());
  }

  @Test
  public void unselectingShowTagCheckBox() {
    myDialog.getShowTagCheckBox().setSelected(false);

    assertThat(myDialog.getSampleLabel().getText())
      .isEqualTo("2018-02-06 14:16:28.555 123-456/com.android.sample I: This is a sample message");
    assertThat(myDialog.getFormat()).isEqualTo(myFormatBuilder.setShowTag(false).build());
  }

  /**
   * Convenience builder that highlights the change made to the default values.
   */
  @SuppressWarnings("SameParameterValue")
  private static class LogcatHeaderFormatBuilder {
    private final LogcatHeaderFormat defaultValue = new LogcatHeaderFormat();

    private TimestampFormat myTimestampFormat = defaultValue.getTimestampFormat();
    private boolean myShowProcessId = defaultValue.getShowProcessId();
    private boolean myShowPackageName = defaultValue.getShowPackageName();
    private boolean myShowTag = defaultValue.getShowTag();

    LogcatHeaderFormatBuilder setTimestampFormat(TimestampFormat value) {
      myTimestampFormat = value;
      return this;
    }

    LogcatHeaderFormatBuilder setShowProcessId(boolean value) {
      myShowProcessId = value;
      return this;
    }

    LogcatHeaderFormatBuilder setShowPackageName(boolean value) {
      myShowPackageName = value;
      return this;
    }

    LogcatHeaderFormatBuilder setShowTag(boolean value) {
      myShowTag = value;
      return this;
    }

    LogcatHeaderFormat build() {
      return new LogcatHeaderFormat(myTimestampFormat, myShowProcessId, myShowPackageName, myShowTag);
    }
  }
}
