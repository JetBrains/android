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

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.PositionManagerImpl;
import com.sun.jdi.Field;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Value;

public class InstantRunPositionManager extends PositionManagerImpl {

  public InstantRunPositionManager(DebugProcessImpl debugProcess) {
    super(debugProcess);
  }

  @Override
  protected ReferenceType mapClass(ReferenceType type) {
    ReferenceType ret = type;
    if (FastDeployManager.isInstantRunEnabled(getDebugProcess().getProject())) {
      Field change = type.fieldByName("$change");
      if (change != null) {
        Value value = type.getValue(change);
        if (value != null && value.type() instanceof ReferenceType) {
          ret = (ReferenceType)value.type();
        }
      }
    }
    return ret;
  }
}