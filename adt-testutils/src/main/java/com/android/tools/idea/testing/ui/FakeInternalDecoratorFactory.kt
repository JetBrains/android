/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.testing.ui;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.intellij.toolWindow.InternalDecoratorImpl;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import java.awt.Container;
import java.util.ArrayList;
import javax.swing.JPanel;
import org.mockito.Answers;

public class FakeInternalDecoratorFactory implements ToolWindowHeadlessManagerImpl.InternalDecoratorFactory {
  private final Object treeLock = new JPanel().getTreeLock();

  @SuppressWarnings("UnstableApiUsage")
  public InternalDecoratorImpl createInternalDecorator(ContentManager contentManager) {
    InternalDecoratorImpl mockDecorator = mock(InternalDecoratorImpl.class, Answers.CALLS_REAL_METHODS);
    try {
      var field = Container.class.getDeclaredField("component");
      field.setAccessible(true);
      field.set(mockDecorator, new ArrayList<>());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    doAnswer(invocation -> treeLock).when(mockDecorator).getTreeLock();

    doAnswer(invocation -> {
      ToolWindowHeadlessManagerImpl.split(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
      return null;
    }).when(mockDecorator).splitWithContent(any(Content.class), anyInt(), anyInt());

    doAnswer(invocation -> {
      Object argument = invocation.getArgument(0);
      ToolWindowHeadlessManagerImpl.unsplit(contentManager, invocation.getArgument(0));
      return null;
    }).when(mockDecorator).unsplit(any(Content.class));

    doAnswer(invocation -> false).when(mockDecorator).isSplitUnsplitInProgress();
    doAnswer(invocation -> "").when(mockDecorator).toString(); // To avoid NPE while debugging.
    return mockDecorator;
  }
}
