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
package com.android.tools.idea.devicemanager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.annotations.Nullable;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceManagerWelcomeScreenActionTest {
  private @Nullable DeviceManagerWelcomeScreenAction myAction;

  @Before
  public void setUpFixture() throws Exception {
    IdeaTestFixtureFactory.getFixtureFactory().createBareFixture().setUp();
  }

  @Test
  public void deviceManagerEnabled() {
    myAction = new DeviceManagerWelcomeScreenAction(() -> true, () -> false, () -> true);

    TestActionEvent event = new TestActionEvent();
    myAction.update(event);

    assertTrue(event.getPresentation().isVisible());
    assertTrue(event.getPresentation().isEnabled());
  }

  @Test
  public void deviceManagerDisabled() {
    myAction = new DeviceManagerWelcomeScreenAction(() -> false, () -> false, () -> true);

    TestActionEvent event = new TestActionEvent();
    myAction.update(event);

    assertFalse(event.getPresentation().isVisible());
    assertTrue(event.getPresentation().isEnabled());
  }

  @Test
  public void isChromeOsAndNotHWAccelerated() {
    myAction = new DeviceManagerWelcomeScreenAction(() -> true, () -> true, () -> true);

    TestActionEvent event = new TestActionEvent();
    myAction.update(event);

    assertFalse(event.getPresentation().isVisible());
  }

  @Test
  public void androidSdkNotAvailable() {
    myAction = new DeviceManagerWelcomeScreenAction(() -> true, () -> false, () -> false);

    TestActionEvent event = new TestActionEvent();
    myAction.update(event);

    assertFalse(event.getPresentation().isEnabled());
  }
}
