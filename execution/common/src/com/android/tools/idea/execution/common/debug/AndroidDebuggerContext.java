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

import com.google.common.collect.ImmutableSortedMap;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import java.util.List;
import java.util.Map;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidDebuggerContext implements JDOMExternalizable {
  public String DEBUGGER_TYPE;
  private final Map<String, AndroidDebuggerState> myAndroidDebuggerStates;
  private final String myDefaultDebuggerType;

  public AndroidDebuggerContext(@NotNull String defaultDebuggerType) {
    myDefaultDebuggerType = defaultDebuggerType;
    DEBUGGER_TYPE = getDefaultAndroidDebuggerType();

    // ImmutableSortedMap.naturalOrder is used to make sure that state entries are persisted in the same order.
    ImmutableSortedMap.Builder<String, AndroidDebuggerState> builder = ImmutableSortedMap.naturalOrder();
    for (AndroidDebugger androidDebugger : getAndroidDebuggers()) {
      builder.put(androidDebugger.getId(), androidDebugger.createState());
    }
    myAndroidDebuggerStates = builder.build();
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);

    for (Map.Entry<String, AndroidDebuggerState> entry : myAndroidDebuggerStates.entrySet()) {
      Element optionElement = element.getChild(entry.getKey());
      if (optionElement != null) {
        entry.getValue().readExternal(optionElement);
      }
    }

    // check DEBUGGER_TYPE consistency
    if (getAndroidDebugger() == null) {
      DEBUGGER_TYPE = getDefaultAndroidDebuggerType();
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);

    for (Map.Entry<String, AndroidDebuggerState> entry : myAndroidDebuggerStates.entrySet()) {
      Element optionElement = new Element(entry.getKey());
      element.addContent(optionElement);
      entry.getValue().writeExternal(optionElement);
    }
  }

  @NotNull
  public String getDebuggerType() {
    return DEBUGGER_TYPE;
  }

  public void setDebuggerType(@NotNull String debuggerType) {
    DEBUGGER_TYPE = debuggerType;
  }

  @NotNull
  public List<AndroidDebugger> getAndroidDebuggers() {
    return AndroidDebugger.EP_NAME.getExtensionList();
  }

  @Nullable
  public AndroidDebugger getAndroidDebugger() {
    for (AndroidDebugger androidDebugger : getAndroidDebuggers()) {
      if (androidDebugger.getId().equals(DEBUGGER_TYPE)) {
        return androidDebugger;
      }
    }
    return null;
  }

  @Nullable
  public <T extends AndroidDebuggerState> T getAndroidDebuggerState(@NotNull String androidDebuggerId) {
    AndroidDebuggerState state = myAndroidDebuggerStates.get(androidDebuggerId);
    return (state != null) ? (T)state : null;
  }

  @Nullable
  public <T extends AndroidDebuggerState> T getAndroidDebuggerState() {
    return getAndroidDebuggerState(DEBUGGER_TYPE);
  }

  @NotNull
  protected String getDefaultAndroidDebuggerType() {
    for (AndroidDebugger androidDebugger : getAndroidDebuggers()) {
      if (androidDebugger.shouldBeDefault()) {
        return androidDebugger.getId();
      }
    }
    return myDefaultDebuggerType;
  }
}
