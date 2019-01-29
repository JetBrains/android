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

import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.ZoneId;

import static org.junit.Assert.*;

public final class ConfigureLogcatHeaderDialogTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private ConfigureLogcatHeaderDialog myDialog;

  @Before
  public void initDialog() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      ZoneId timeZone = ZoneId.of("America/Los_Angeles");
      myDialog = new ConfigureLogcatHeaderDialog(myRule.getProject(), new AndroidLogcatPreferences(), timeZone);
    });
  }

  @After
  public void disposeOfDialog() {
    ApplicationManager.getApplication().invokeAndWait(() -> Disposer.dispose(myDialog.getDisposable()));
  }

  @Test
  public void configureLogcatHeaderDialog() {
    assertTrue(myDialog.getShowDateAndTimeCheckBox().isSelected());
    assertFalse(myDialog.getShowAsSecondsSinceEpochCheckBox().isSelected());
    assertTrue(myDialog.getShowProcessAndThreadIdsCheckBox().isSelected());
    assertTrue(myDialog.getShowPackageNameCheckBox().isSelected());
    assertTrue(myDialog.getShowTagCheckBox().isSelected());

    assertEquals("2018-02-06 14:16:28.555 123-456/com.android.sample I/SampleTag: This is a sample message",
                 myDialog.getSampleLabel().getText());

    assertEquals("", myDialog.getFormat());
  }

  @Test
  public void unselectingShowDateAndTimeCheckBox() {
    myDialog.getShowDateAndTimeCheckBox().setSelected(false);

    assertFalse(myDialog.getShowAsSecondsSinceEpochCheckBox().isVisible());
    assertTrue(myDialog.getShowProcessAndThreadIdsCheckBox().isSelected());
    assertTrue(myDialog.getShowPackageNameCheckBox().isSelected());
    assertTrue(myDialog.getShowTagCheckBox().isSelected());
    assertEquals("123-456/com.android.sample I/SampleTag: This is a sample message", myDialog.getSampleLabel().getText());

    assertEquals("%2$s/%3$s %4$c/%5$s: %6$s", myDialog.getFormat());
  }

  @Test
  public void selectingShowAsSecondsSinceEpochCheckBox() {
    myDialog.getShowAsSecondsSinceEpochCheckBox().setSelected(true);

    assertTrue(myDialog.getShowDateAndTimeCheckBox().isSelected());
    assertTrue(myDialog.getShowProcessAndThreadIdsCheckBox().isSelected());
    assertTrue(myDialog.getShowPackageNameCheckBox().isSelected());
    assertTrue(myDialog.getShowTagCheckBox().isSelected());
    assertEquals("1517955388.555 123-456/com.android.sample I/SampleTag: This is a sample message", myDialog.getSampleLabel().getText());

    assertEquals("", myDialog.getFormat());
  }

  @Test
  public void unselectingShowProcessAndThreadIdsCheckBox() {
    myDialog.getShowProcessAndThreadIdsCheckBox().setSelected(false);

    assertTrue(myDialog.getShowDateAndTimeCheckBox().isSelected());
    assertFalse(myDialog.getShowAsSecondsSinceEpochCheckBox().isSelected());
    assertTrue(myDialog.getShowPackageNameCheckBox().isSelected());
    assertTrue(myDialog.getShowTagCheckBox().isSelected());
    assertEquals("2018-02-06 14:16:28.555 com.android.sample I/SampleTag: This is a sample message", myDialog.getSampleLabel().getText());

    assertEquals("%1$s %3$s %4$c/%5$s: %6$s", myDialog.getFormat());
  }

  @Test
  public void unselectingShowPackageNameCheckBox() {
    myDialog.getShowPackageNameCheckBox().setSelected(false);

    assertTrue(myDialog.getShowDateAndTimeCheckBox().isSelected());
    assertFalse(myDialog.getShowAsSecondsSinceEpochCheckBox().isSelected());
    assertTrue(myDialog.getShowProcessAndThreadIdsCheckBox().isSelected());
    assertTrue(myDialog.getShowTagCheckBox().isSelected());
    assertEquals("2018-02-06 14:16:28.555 123-456 I/SampleTag: This is a sample message", myDialog.getSampleLabel().getText());

    assertEquals("%1$s %2$s %4$c/%5$s: %6$s", myDialog.getFormat());
  }

  @Test
  public void unselectingShowTagCheckBox() {
    myDialog.getShowTagCheckBox().setSelected(false);

    assertTrue(myDialog.getShowDateAndTimeCheckBox().isSelected());
    assertFalse(myDialog.getShowAsSecondsSinceEpochCheckBox().isSelected());
    assertTrue(myDialog.getShowProcessAndThreadIdsCheckBox().isSelected());
    assertTrue(myDialog.getShowPackageNameCheckBox().isSelected());
    assertEquals("2018-02-06 14:16:28.555 123-456/com.android.sample I: This is a sample message", myDialog.getSampleLabel().getText());

    assertEquals("%1$s %2$s/%3$s %4$c: %6$s", myDialog.getFormat());
  }
}
