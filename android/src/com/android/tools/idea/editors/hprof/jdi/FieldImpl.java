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
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FieldImpl implements com.sun.jdi.Field {

  @NotNull Field myField;

  @Nullable private Object myValue;

  public FieldImpl(@NotNull Field field, @Nullable Object value) {
    myField = field;
    myValue = value;
  }

  @Override
  public String typeName() {
    return myField.getType().name();
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
  public boolean isTransient() {
    return false;
  }

  @Override
  public boolean isVolatile() {
    return false;
  }

  @Override
  public boolean isEnumConstant() {
    return false;
  }

  @Override
  public String name() {
    return myField.getName();
  }

  @Override
  public String signature() {
    return type().signature();
  }

  @Override
  public String genericSignature() {
    return type().signature();
  }

  @Override
  public ReferenceType declaringType() {
    return new ReferenceTypeImpl(myField.getType(), myValue);
  }

  @Override
  public boolean isStatic() {
    return false;
  }

  @Override
  public boolean isFinal() {
    return false;
  }

  @Override
  public boolean isSynthetic() {
    return false;
  }

  @Override
  public int modifiers() {
    return 0;
  }

  @Override
  public boolean isPrivate() {
    return false;
  }

  @Override
  public boolean isPackagePrivate() {
    return false;
  }

  @Override
  public boolean isProtected() {
    return false;
  }

  @Override
  public boolean isPublic() {
    return true;
  }

  @Override
  public int compareTo(com.sun.jdi.Field o) {
    return name().compareTo(o.name());
  }

  @Override
  public VirtualMachine virtualMachine() {
    return null;
  }
}
