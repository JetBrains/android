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

import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.Scope;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.Value;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.XSourcePositionWrapper;
import java.util.ArrayList;
import java.util.List;

/** A {@link XSourcePosition} with additional context used for completion suggestions. */
class SkylarkSourcePosition extends XSourcePositionWrapper {

  final SkylarkStackFrame frame;
  private volatile List<Value> allBindings = null;

  SkylarkSourcePosition(XSourcePosition base, SkylarkStackFrame frame) {
    super(base);
    this.frame = frame;
  }

  List<Value> getBindings() {
    if (allBindings == null) {
      List<Value> bindings = new ArrayList<>();
      for (Scope scope : frame.frame.getScopeList()) {
        bindings.addAll(scope.getBindingList());
      }
      allBindings = bindings;
    }
    return allBindings;
  }
}
