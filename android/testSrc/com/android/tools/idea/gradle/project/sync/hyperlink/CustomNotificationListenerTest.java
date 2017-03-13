/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.notification.Notification;
import com.intellij.openapi.project.Project;
import junit.framework.TestCase;

import javax.swing.event.HyperlinkEvent;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link CustomNotificationListener}.
 */
public class CustomNotificationListenerTest extends TestCase {
  private NotificationHyperlink myHyperlink1;
  private NotificationHyperlink myHyperlink2;
  private NotificationHyperlink myHyperlink3;
  private Notification myNotification;
  private HyperlinkEvent myHyperlinkEvent;
  private CustomNotificationListener myListener;
  private Project myProject;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myHyperlink1 = mock(NotificationHyperlink.class);
    myHyperlink2 = mock(NotificationHyperlink.class);
    myHyperlink3 = mock(NotificationHyperlink.class);
    myNotification = mock(Notification.class);
    myHyperlinkEvent = mock(HyperlinkEvent.class);
    myProject = mock(Project.class);
  }

  public void testHyperlinkActivatedWithOneHyperlink() {
    myListener = new CustomNotificationListener(myProject, myHyperlink1);

    myListener.hyperlinkActivated(myNotification, myHyperlinkEvent);

    // if there is only one hyperlink, just execute it.
    verify(myHyperlink1).executeIfClicked(myProject, myHyperlinkEvent);
    verify(myHyperlink2, never()).executeIfClicked(myProject, myHyperlinkEvent);
    verify(myHyperlink3, never()).executeIfClicked(myProject, myHyperlinkEvent);
  }

  public void testHyperlinkActivatedWithMoreThanOneHyperlink() {
    myListener = new CustomNotificationListener(myProject, myHyperlink1, myHyperlink2, myHyperlink3);

    myListener.hyperlinkActivated(myNotification, myHyperlinkEvent);

    verify(myHyperlink1).executeIfClicked(myProject, myHyperlinkEvent);
    verify(myHyperlink2).executeIfClicked(myProject, myHyperlinkEvent);
    verify(myHyperlink3).executeIfClicked(myProject, myHyperlinkEvent);
  }

  public void testHyperlinkCloseOnClick() {
    myListener = new CustomNotificationListener(myProject, myHyperlink1);

    when(myHyperlink1.executeIfClicked(myProject, myHyperlinkEvent)).thenReturn(true);
    when(myHyperlink1.isCloseOnClick()).thenReturn(true);
    myListener.hyperlinkActivated(myNotification, myHyperlinkEvent);

    verify(myNotification).expire();
  }
}
