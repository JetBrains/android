/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.skylark.debugger.impl;

import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;

// SkylarkLineBreakpointType extends from XLineBreakpointTypeBase which uses raw
// XBreakpointProperties. The raw use of XBreakpointProperties needs to propagate to all affected
// classes. Check XLineBreakpointTypeBase again after #api212.
@SuppressWarnings("rawtypes")
class SkylarkLineBreakpointHandler
    extends XBreakpointHandler<XLineBreakpoint<XBreakpointProperties>> {

  private final SkylarkDebugProcess debugProcess;

  SkylarkLineBreakpointHandler(SkylarkDebugProcess debugProcess) {
    super(SkylarkLineBreakpointType.class);
    this.debugProcess = debugProcess;
  }

  @Override
  public void registerBreakpoint(XLineBreakpoint<XBreakpointProperties> breakpoint) {
    debugProcess.addBreakpoint(breakpoint);
  }

  @Override
  public void unregisterBreakpoint(
      XLineBreakpoint<XBreakpointProperties> breakpoint, boolean temporary) {
    debugProcess.removeBreakpoint(breakpoint);
  }
}
