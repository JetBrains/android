/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant;

import static org.junit.Assert.assertTrue;

import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public class WhatsNewSidePanelActionTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private Presentation myPresentation;
  private AnActionEvent myEvent;
  private Runnable myBrowseToWhatsNewUrl;

  @Before
  public void mockEvent() {
    myPresentation = new Presentation();

    myEvent = Mockito.mock(AnActionEvent.class);
    Mockito.when(myEvent.getPresentation()).thenReturn(myPresentation);
  }

  @Before
  public void mockBrowseToWhatsNewUrl() {
    myBrowseToWhatsNewUrl = Mockito.mock(Runnable.class);
  }

  @Test
  public void updateProjectIsNull() {
    WhatsNewSidePanelAction action = new WhatsNewSidePanelAction(myBrowseToWhatsNewUrl);

    action.update(myEvent);
    assertTrue(myPresentation.isEnabled());

    action.actionPerformed(myEvent);
    Mockito.verify(myBrowseToWhatsNewUrl).run();
  }

  @Test
  public void updateProjectIsNotNull() {
    WhatsNewSidePanelAction action = new WhatsNewSidePanelAction(myBrowseToWhatsNewUrl);
    Mockito.when(myEvent.getProject()).thenReturn(myRule.getProject());

    action.update(myEvent);
    assertTrue(myPresentation.isEnabled());

    action.actionPerformed(myEvent);
    Mockito.verify(myBrowseToWhatsNewUrl, Mockito.never()).run();
  }
}
