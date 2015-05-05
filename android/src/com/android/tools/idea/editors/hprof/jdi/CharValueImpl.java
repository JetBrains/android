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
import com.sun.jdi.CharValue;
import org.jetbrains.annotations.NotNull;

public class CharValueImpl extends PrimitiveValueImpl implements CharValue {

  public CharValueImpl(@NotNull Field field, @NotNull Object value) {
    super(field, value);
  }

  @Override
  public char value() {
    assert myValue != null;
    return ((Character)myValue).charValue();
  }

  @Override
  public int compareTo(CharValue o) {
    return value() - o.value();
  }

  @Override
  public boolean booleanValue() {
    return value() > 0;
  }

  @Override
  public byte byteValue() {
    return (byte)value();
  }

  @Override
  public char charValue() {
    return value();
  }

  @Override
  public short shortValue() {
    return (short)value();
  }

  @Override
  public int intValue() {
    return (int)value();
  }

  @Override
  public long longValue() {
    return (long)value();
  }

  @Override
  public float floatValue() {
    return (float)value();
  }

  @Override
  public double doubleValue() {
    return (double)value();
  }
}
