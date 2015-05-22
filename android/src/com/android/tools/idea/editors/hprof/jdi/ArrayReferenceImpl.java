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

import com.android.tools.perflib.heap.ArrayInstance;
import com.android.tools.perflib.heap.Field;
import com.android.tools.perflib.heap.Instance;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ArrayReferenceImpl extends ObjectReferenceImpl implements ArrayReference {
  @Nullable
  private Object[] myCachedValues;

  public ArrayReferenceImpl(@NotNull Field field, @NotNull Instance instance) {
    super(field, instance);
  }

  @Override
  public int length() {
    return getCachedValues().length;
  }

  @Override
  public Value getValue(int index) {
    return new ValueImpl(myField, getCachedValues()[index]);
  }

  @Override
  public List<Value> getValues() {
    return getValues(0, getCachedValues().length);
  }

  @Override
  public void setValues(List<? extends Value> values) throws InvalidTypeException, ClassNotLoadedException {

  }

  @Override
  public List<Value> getValues(int index, int length) {
    Object[] rawValues = getCachedValues();
    List<Value> values = new ArrayList<Value>(rawValues.length - index);
    for (int i = index; i < rawValues.length; ++i) {
      values.add(new ValueImpl(myField, rawValues[i]));
    }
    return values;
  }

  @Override
  public void setValue(int index, Value value) throws InvalidTypeException, ClassNotLoadedException {

  }

  @Override
  public void setValues(int index, List<? extends Value> values, int srcIndex, int length)
    throws InvalidTypeException, ClassNotLoadedException {

  }

  @NotNull
  public ArrayInstance getArrayInstance() {
    //noinspection ConstantConditions
    return (ArrayInstance)getInstance();
  }

  private Object[] getCachedValues() {
    if (myCachedValues == null) {
      myCachedValues = getArrayInstance().getValues();
    }
    return myCachedValues;
  }
}
