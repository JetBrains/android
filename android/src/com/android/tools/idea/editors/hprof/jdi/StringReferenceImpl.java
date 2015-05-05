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
import com.android.tools.perflib.heap.ClassInstance;
import com.android.tools.perflib.heap.Field;
import com.android.tools.perflib.heap.Instance;
import com.sun.jdi.StringReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class StringReferenceImpl extends ObjectReferenceImpl implements StringReference {

  public StringReferenceImpl(@NotNull Field field, @Nullable Instance instance) {
    super(field, instance);
  }

  @Override
  public String value() {
    if (myValue == null) {
      return null;
    }

    ArrayInstance charBufferArray = null;
    assert (myValue instanceof ClassInstance);
    ClassInstance classInstance = (ClassInstance)myValue;
    for (Map.Entry<Field, Object> entry : classInstance.getValues().entrySet()) {
      if ("value".equals(entry.getKey().getName())) {
        charBufferArray = (ArrayInstance)entry.getValue();
      }
    }
    assert (charBufferArray != null);
    StringBuilder builder = new StringBuilder(charBufferArray.getValues().length + 3);
    builder.append(" \"");
    for (Object o : charBufferArray.getValues()) {
      assert (o instanceof Character);
      builder.append(o);
    }
    builder.append("\"");
    return builder.toString();
  }
}
