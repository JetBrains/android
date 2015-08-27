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
package com.android.tools.idea.editors.gfxtrace.service.atom;

import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.schema.Dynamic;
import com.android.tools.rpclib.schema.Field;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

public class DynamicAtom extends Atom {
  @NotNull private final Dynamic myValue;
  @NotNull private final AtomMetadata myMetadata;

  public DynamicAtom(Dynamic value) {
    myValue = value;
    myMetadata = AtomMetadata.find(value.type());
  }

  @NotNull
  @Override
  public BinaryObject unwrap() {
    return myValue;
  }

  @Override
  public String getName() {
    return myValue.type().getName();
  }

  @Override
  public int getFieldCount() {
    return myValue.getFieldCount();
  }

  @Override
  public Field getFieldInfo(int index) {
    return myValue.getFieldInfo(index);
  }

  @Override
  public Object getFieldValue(int index) {
    return myValue.getFieldValue(index);
  }

  @Override
  public int getObservationsIndex() {
    return myMetadata.myObservationsIndex;
  }

  @Override
  public Observations getObservations() {
    if (myMetadata.myObservationsIndex >= 0) {
      Object value = getFieldValue(myMetadata.myObservationsIndex);
      assert(value instanceof Observations);
      return (Observations)value;
    }
    return null;
  }

  @Override
  public int getResultIndex() {
    return myMetadata.myResultIndex;
  }

  @Override
  public boolean getIsEndOfFrame() {
    return myMetadata.getEndOfFrame();
  }

  @Override
  public void render(@NotNull SimpleColoredComponent component, SimpleTextAttributes defaultAttributes) {
    component.append(getName() + "(", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    int resultIndex = getResultIndex();
    int observationsIndex = getObservationsIndex();
    boolean needComma = false;
    for (int i = 0; i < getFieldCount(); ++i) {
      if (i == resultIndex || i == observationsIndex) continue;
      if (needComma) {
        component.append(", ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
      needComma = true;
      Field field = getFieldInfo(i);
      Object parameterValue = getFieldValue(i);
      field.getType().render(parameterValue, component, defaultAttributes);
    }

    component.append(")", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    if (resultIndex >= 0) {
      component.append("->", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      Field field = getFieldInfo(resultIndex);
      Object parameterValue = getFieldValue(resultIndex);
      field.getType().render(parameterValue, component, defaultAttributes);
    }
  }
}
