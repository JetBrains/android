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
package com.android.tools.idea.gradle.service.notification.hyperlink;

import com.intellij.notification.Notification;
import com.intellij.openapi.project.Project;
import junit.framework.TestCase;

import javax.swing.event.HyperlinkEvent;

import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link com.android.tools.idea.gradle.service.notification.hyperlink.CustomNotificationListener}.
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
    myHyperlink1 = createMock(NotificationHyperlink.class);
    myHyperlink2 = createMock(NotificationHyperlink.class);
    myHyperlink3 = createMock(NotificationHyperlink.class);
    myNotification = createMock(Notification.class);
    myHyperlinkEvent = createMock(HyperlinkEvent.class);
    myProject = createMock(Project.class);
  }

  public void testHyperlinkActivatedWithOneHyperlink() {
    myListener = new CustomNotificationListener(myProject, myHyperlink1);

    // if there is only one hyperlink, just execute it.
    expect(myHyperlink1.executeIfClicked(myProject, myHyperlinkEvent)).andReturn(true);
    replay(myHyperlink1, myHyperlink2, myHyperlink3);

    myListener.hyperlinkActivated(myNotification, myHyperlinkEvent);

    verify(myHyperlink1, myHyperlink2, myHyperlink3);
  }

  public void testHyperlinkActivatedWithMoreThanOneHyperlink() {
    myListener = new CustomNotificationListener(myProject, myHyperlink1, myHyperlink2, myHyperlink3);

    // should not try to execute myHyperlink3, because execution of myHyperlink2 was successful.
    expect(myHyperlink1.executeIfClicked(myProject, myHyperlinkEvent)).andReturn(false);
    expect(myHyperlink2.executeIfClicked(myProject, myHyperlinkEvent)).andReturn(true);
    replay(myHyperlink1, myHyperlink2, myHyperlink3);

    myListener.hyperlinkActivated(myNotification, myHyperlinkEvent);

    verify(myHyperlink1, myHyperlink2, myHyperlink3);
  }
}
