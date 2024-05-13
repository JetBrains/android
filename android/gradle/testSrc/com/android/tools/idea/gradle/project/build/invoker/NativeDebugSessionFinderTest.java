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
package com.android.tools.idea.gradle.project.build.invoker;

import com.android.tools.idea.run.AndroidNativeDebugProcess;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class NativeDebugSessionFinderTest extends HeavyPlatformTestCase {
  @Mock private XDebuggerManager myDebuggerManager;

  private XDebugSession myJavaDebugSession;
  private NativeDebugSessionFinder mySessionFinder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myJavaDebugSession = mock(XDebugSession.class);
    JavaDebugProgressStub javaDebugProcess = new JavaDebugProgressStub(myJavaDebugSession);
    when(myJavaDebugSession.getDebugProcess()).thenReturn(javaDebugProcess);

    mySessionFinder = new NativeDebugSessionFinder(myDebuggerManager);
  }

  public void testFindNativeDebugSessionWithNativeDebugSession() {
    XDebugSession nativeDebugSession = mock(XDebugSession.class);
    XDebugProcess nativeDebugProcess = new NativeDebugProgressStub(nativeDebugSession);
    when(nativeDebugSession.getDebugProcess()).thenReturn(nativeDebugProcess);

    when(myDebuggerManager.getDebugSessions()).thenReturn(new XDebugSession[]{myJavaDebugSession, nativeDebugSession});

    // Method to test:
    XDebugSession foundSession = mySessionFinder.findNativeDebugSession();

    assertSame(nativeDebugSession, foundSession);
  }

  public void testFindNativeDebugSessionWithoutNativeDebugSession() {
    when(myDebuggerManager.getDebugSessions()).thenReturn(new XDebugSession[]{myJavaDebugSession});

    // Method to test:
    XDebugSession foundSession = mySessionFinder.findNativeDebugSession();

    assertNull(foundSession);
  }

  private static class NativeDebugProgressStub extends XDebugProcess implements AndroidNativeDebugProcess {
    NativeDebugProgressStub(@NotNull XDebugSession session) {
      super(session);
    }

    @Override
    @NotNull
    public XDebuggerEditorsProvider getEditorsProvider() {
      return mock(XDebuggerEditorsProvider.class);
    }
  }

  private static class JavaDebugProgressStub extends XDebugProcess {
    JavaDebugProgressStub(@NotNull XDebugSession session) {
      super(session);
    }

    @Override
    @NotNull
    public XDebuggerEditorsProvider getEditorsProvider() {
      return mock(XDebuggerEditorsProvider.class);
    }
  }
}