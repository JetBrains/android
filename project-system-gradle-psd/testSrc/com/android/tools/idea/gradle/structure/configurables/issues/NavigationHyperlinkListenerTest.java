/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.issues;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import javax.swing.event.HyperlinkEvent;

import static org.mockito.Mockito.*;

public class NavigationHyperlinkListenerTest {

  public static final String TEST_URL = "test://one";
  private PsContext myContext;
  private LinkHandler myHandler1;
  private LinkHandler myHandler2;

  private NavigationHyperlinkListener listener;

  @Before
  public void setUp() {
    myContext = mock(PsContext.class);
    myHandler1 = mock(LinkHandler.class);
    myHandler2 = mock(LinkHandler.class);
    listener = new NavigationHyperlinkListener(myContext, myHandler1, myHandler2);
  }

  @Test
  public void navigate_firstHandlerAccepts() throws Exception {
    when(myHandler1.accepts(TEST_URL)).thenReturn(true);
    when(myHandler2.accepts(TEST_URL)).thenReturn(false);
    listener.hyperlinkActivated(
      new HyperlinkEvent(this,
                         HyperlinkEvent.EventType.ACTIVATED,
                         null /* JEditorPane passes null for unknown prptocol schemes */,
                         TEST_URL));
    InOrder order = inOrder(myHandler1, myHandler2, myContext);
    order.verify(myHandler1).accepts(TEST_URL);
    order.verify(myHandler1).navigate(TEST_URL);
    order.verifyNoMoreInteractions();
  }

  @Test
  public void navigate_secondHandlerAccepts() throws Exception {
    when(myHandler1.accepts(TEST_URL)).thenReturn(false);
    when(myHandler2.accepts(TEST_URL)).thenReturn(true);
    listener.hyperlinkActivated(
      new HyperlinkEvent(this,
                         HyperlinkEvent.EventType.ACTIVATED,
                         null /* JEditorPane passes null for unknown prptocol schemes */,
                         TEST_URL));
    InOrder order = inOrder(myHandler1, myHandler2, myContext);
    order.verify(myHandler1).accepts(TEST_URL);
    order.verify(myHandler2).accepts(TEST_URL);
    order.verify(myHandler2).navigate(TEST_URL);
    order.verifyNoMoreInteractions();
  }

  @Test
  public void navigate_noHandlersAccept() throws Exception {
    when(myHandler1.accepts(TEST_URL)).thenReturn(false);
    when(myHandler2.accepts(TEST_URL)).thenReturn(false);
    listener.hyperlinkActivated(
      new HyperlinkEvent(this,
                         HyperlinkEvent.EventType.ACTIVATED,
                         null /* JEditorPane passes null for unknown prptocol schemes */,
                         TEST_URL));
    InOrder order = inOrder(myHandler1, myHandler2, myContext);
    order.verify(myHandler1).accepts(TEST_URL);
    order.verify(myHandler2).accepts(TEST_URL);
    order.verifyNoMoreInteractions();
  }
}