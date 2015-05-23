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

import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Type;
import com.sun.jdi.VirtualMachine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TypeImpl implements com.sun.jdi.Type {

  @NotNull protected Type myType;

  @Nullable protected Object myValue;

  public TypeImpl(@NotNull Type type, @Nullable Object value) {
    myType = type;
    myValue = value;
  }

  @Override
  public String signature() {
    switch (myType) {
      case OBJECT:
        return "L" + myType.name() + ";";
      case INT:
        return "I";
      case BOOLEAN:
        return "Z";
      case BYTE:
        return "B";
      case FLOAT:
        return "F";
      case CHAR:
        return "C";
      case LONG:
        return "J";
      case DOUBLE:
        return "D";
      case SHORT:
        return "S";
      default:
        throw new RuntimeException("Invalid Type object");
    }
  }

  @Override
  public String name() {
    if (myType == Type.OBJECT && myValue != null) {
      if (myValue instanceof ClassObj) {
        return Class.class.getName();
      }
      else if (((Instance)myValue).getClassObj() != null) {
        return ((Instance)myValue).getClassObj().getClassName();
      }
    }

    // We can't resolve the type name, so fall back to the hprof type's name.
    return myType.name();
  }

  @Override
  public VirtualMachine virtualMachine() {
    return null;
  }
}
