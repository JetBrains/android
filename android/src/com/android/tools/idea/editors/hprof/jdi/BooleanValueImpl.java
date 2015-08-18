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
import com.sun.jdi.BooleanValue;
import org.jetbrains.annotations.NotNull;

public class BooleanValueImpl extends PrimitiveValueImpl implements BooleanValue {

  public BooleanValueImpl(@NotNull Field field, @NotNull Object value) {
    super(field, value);
  }

  @Override
  public boolean value() {
    assert (myValue != null);
    return (Boolean)myValue;
  }

  @Override
  public boolean booleanValue() {
    return value();
  }

  @Override
  public byte byteValue() {
    return value() ? (byte)1 : (byte)0;
  }

  @Override
  public char charValue() {
    return (char)byteValue();
  }

  @Override
  public short shortValue() {
    return byteValue();
  }

  @Override
  public int intValue() {
    return byteValue();
  }

  @Override
  public long longValue() {
    return byteValue();
  }

  @Override
  public float floatValue() {
    return (float)byteValue();
  }

  @Override
  public double doubleValue() {
    return (double)byteValue();
  }
}
