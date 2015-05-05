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

import com.android.tools.perflib.heap.Type;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ClassNotLoadedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ArrayTypeImpl extends ReferenceTypeImpl implements ArrayType {

  public ArrayTypeImpl(@NotNull Type type, @Nullable Object[] value) {
    super(type, value);
  }

  @Override
  public ArrayReference newInstance(int length) {
    return null;
  }

  @Override
  public String componentSignature() {
    return signature();
  }

  @Override
  public String componentTypeName() {
    return myType.name();
  }

  @Override
  public com.sun.jdi.Type componentType() throws ClassNotLoadedException {
    if (myType == Type.OBJECT) {
      return new ReferenceTypeImpl(myType, myValue);
    }
    else {
      return new PrimitiveTypeImpl(myType, null);
    }
  }
}
