/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.fd;

import com.intellij.debugger.jdi.StackFrameProxyProvider;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.sun.jdi.StackFrame;
import org.jetbrains.annotations.NotNull;

public class InstantRunStackFrameProxyProvider implements StackFrameProxyProvider {

  @Override
  public StackFrameProxyImpl createProxy(@NotNull ThreadReferenceProxyImpl threadProxy, @NotNull StackFrame frame, int fromBottomIndex) {
    return new InstantRunStackFrame(threadProxy, frame, fromBottomIndex);
  }
}
