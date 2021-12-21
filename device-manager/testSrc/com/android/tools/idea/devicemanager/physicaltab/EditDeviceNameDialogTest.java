/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatform4TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class EditDeviceNameDialogTest extends LightPlatform4TestCase {
  private EditDeviceNameDialog myDialog;

  @Test
  public void clickLink() {
    // Arrange
    ApplicationManager.getApplication().invokeAndWait(() -> myDialog = new EditDeviceNameDialog(null, "", "Google Pixel 3"));
    Disposer.register(getTestRootDisposable(), myDialog.getDisposable());

    // Act
    myDialog.getTextField().setText("Google Pixel 5");
    myDialog.getLink().doClick();

    // Assert
    assertEquals("", myDialog.getNameOverride());
  }
}
