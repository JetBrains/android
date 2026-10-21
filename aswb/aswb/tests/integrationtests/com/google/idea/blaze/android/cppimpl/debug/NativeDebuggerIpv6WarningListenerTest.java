/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.cppimpl.debug;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

/** Tests for {@link NativeDebuggerIpv6WarningListener}. */
@RunWith(JUnit4.class)
public class NativeDebuggerIpv6WarningListenerTest extends BlazeIntegrationTestCase {

  private static final Supplier<Boolean> DEFAULT_DETECTOR =
      NativeDebuggerIpv6WarningListener.ipv6Detector;

  private NotificationGroupManager notificationGroupManager;
  private NotificationGroup notificationGroup;
  private NativeDebuggerIpv6WarningListener listener;

  @Before
  public void localSetUp() throws Exception {
    notificationGroupManager = mock(NotificationGroupManager.class);
    notificationGroup = mock(NotificationGroup.class);
    when(notificationGroupManager.getNotificationGroup("Native Debugger Warning"))
        .thenReturn(notificationGroup);

    registerApplicationService(NotificationGroupManager.class, notificationGroupManager);

    listener = new NativeDebuggerIpv6WarningListener();
  }

  @After
  public void restoreDetector() throws Exception {
    NativeDebuggerIpv6WarningListener.ipv6Detector = DEFAULT_DETECTOR;
  }

  @Test
  public void testWarningShownWhenIpv6Preferred() {
    NativeDebuggerIpv6WarningListener.ipv6Detector = () -> true;

    XDebugProcess debugProcess = mock(CidrDebugProcess.class);
    XDebugSession session = mock(XDebugSession.class);
    when(debugProcess.getSession()).thenReturn(session);
    when(session.getProject()).thenReturn(getProject());

    Notification mockNotification = mock(Notification.class);
    when(notificationGroup.createNotification(
            any(String.class), any(String.class), any(NotificationType.class)))
        .thenReturn(mockNotification);

    listener.processStarted(debugProcess);

    ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);

    verify(notificationGroup)
        .createNotification(titleCaptor.capture(), contentCaptor.capture(), typeCaptor.capture());

    assertThat(titleCaptor.getValue()).isEqualTo("Native Debugger IPv6 Warning");
    assertThat(contentCaptor.getValue()).contains("IDE is configured to prefer IPv6");
    assertThat(typeCaptor.getValue()).isEqualTo(NotificationType.WARNING);
    verify(mockNotification).notify(getProject());
  }

  @Test
  public void testWarningNotShownWhenIpv4Preferred() {
    NativeDebuggerIpv6WarningListener.ipv6Detector = () -> false;

    XDebugProcess debugProcess = mock(CidrDebugProcess.class);
    XDebugSession session = mock(XDebugSession.class);
    when(debugProcess.getSession()).thenReturn(session);
    when(session.getProject()).thenReturn(getProject());

    listener.processStarted(debugProcess);

    verifyNoInteractions(notificationGroup);
  }

  @Test
  public void testWarningNotShownForNonCidrProcess() {
    NativeDebuggerIpv6WarningListener.ipv6Detector = () -> true;

    XDebugProcess debugProcess = mock(XDebugProcess.class); // Not CidrDebugProcess
    XDebugSession session = mock(XDebugSession.class);
    when(debugProcess.getSession()).thenReturn(session);
    when(session.getProject()).thenReturn(getProject());

    listener.processStarted(debugProcess);

    verifyNoInteractions(notificationGroup);
  }
}
