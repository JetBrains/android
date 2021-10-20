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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.tools.idea.avdmanager.CreateAvdAction;
import com.intellij.testFramework.fixtures.BareTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDevicePanelTest {
  private VirtualDevicePanel myVirtualDevicePanel;
  private BareTestFixture myFixture;

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
  public void createDeviceButton() {
    CreateAvdAction createAvdAction = Mockito.mock(CreateAvdAction.class);
    myVirtualDevicePanel = new VirtualDevicePanel(null, myFixture.getTestRootDisposable(), avdInfoProvider -> createAvdAction);

    myVirtualDevicePanel.getCreateButton().doClick();

    Mockito.verify(createAvdAction).actionPerformed(Mockito.any());
  }
}
