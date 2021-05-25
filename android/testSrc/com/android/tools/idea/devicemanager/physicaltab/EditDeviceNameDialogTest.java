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

import static org.junit.Assert.assertEquals;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BareTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class EditDeviceNameDialogTest {
  private BareTestFixture myFixture;
  private EditDeviceNameDialog myDialog;

  @Before
  public void setUpFixture() throws Exception {
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createBareFixture();
    myFixture.setUp();
  }

  @After
  public void tearDownFixture() throws Exception {
    myFixture.tearDown();
  }

  @Test
  public void clickLink() {
    // Arrange
    ApplicationManager.getApplication().invokeAndWait(() -> myDialog = new EditDeviceNameDialog(null, "", "Google Pixel 3"));
    Disposer.register(myFixture.getTestRootDisposable(), myDialog.getDisposable());

    // Act
    myDialog.getTextField().setText("Google Pixel 5");
    myDialog.getLink().doClick();

    // Assert
    assertEquals("", myDialog.getNameOverride());
  }
}
