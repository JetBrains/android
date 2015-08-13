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
package com.android.tools.idea.editors.hprof.jdi;

import com.android.tools.perflib.heap.Field;
import com.android.tools.perflib.heap.Type;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ValueImpl implements Value {

  @NotNull protected Field myField;

  @Nullable protected Object myValue;

  public ValueImpl(@NotNull Field field, @Nullable Object value) {
    myField = field;
    myValue = value;
  }

  @Override
  public com.sun.jdi.Type type() {
    if (myField.getType() == Type.OBJECT) {
      return new ReferenceTypeImpl(myField.getType(), myValue);
    }
    else {
      return new PrimitiveTypeImpl(myField.getType(), myValue);
    }
  }

  @Override
  public VirtualMachine virtualMachine() {
    return null;
  }
}
