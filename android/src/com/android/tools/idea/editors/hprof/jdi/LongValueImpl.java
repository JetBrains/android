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
import com.sun.jdi.LongValue;
import org.jetbrains.annotations.NotNull;

public class LongValueImpl extends PrimitiveValueImpl implements LongValue {

  public LongValueImpl(@NotNull Field field, @NotNull Object value) {
    super(field, value);
  }

  @Override
  public long value() {
    assert (myValue != null);
    return (Long)myValue;
  }

  @Override
  public int compareTo(LongValue o) {
    return (int)((value() - o.value()) >> 32);
  }

  @Override
  public boolean booleanValue() {
    return value() != 0;
  }

  @Override
  public byte byteValue() {
    return (byte)value();
  }

  @Override
  public char charValue() {
    return (char)value();
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
    return value();
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
