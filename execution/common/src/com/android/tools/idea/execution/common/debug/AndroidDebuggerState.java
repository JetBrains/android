/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.execution.common.debug;

import com.android.tools.idea.run.ValidationError;
import com.intellij.execution.Executor;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import java.util.Collections;
import java.util.List;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidDebuggerState implements JDOMExternalizable {
  public boolean ATTACH_ON_WAIT_FOR_DEBUGGER = false;

  public boolean attachOnWaitForDebugger() {
    return ATTACH_ON_WAIT_FOR_DEBUGGER;
  }

  public void setAttachOnWaitForDebugger(boolean attachOnWaitForDebugger) {
    ATTACH_ON_WAIT_FOR_DEBUGGER = attachOnWaitForDebugger;
  }

  @NotNull
  public List<ValidationError> validate(@NotNull AndroidFacet facet, @Nullable Executor executor) {
    return Collections.emptyList();
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
