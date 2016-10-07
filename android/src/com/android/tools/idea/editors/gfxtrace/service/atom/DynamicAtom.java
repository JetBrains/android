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
    return myMetadata.getDisplayName();
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
  public int getExtrasIndex() {
    return myMetadata.myExtrasIndex;
  }

  @Override
  public Observations getObservations() {
    if (myMetadata.myExtrasIndex >= 0) {
      Object extras = getFieldValue(myMetadata.myExtrasIndex);
      assert (extras instanceof Object[]);
      for (Object extra : (Object[])extras) {
        if (extra instanceof Observations) {
          return (Observations)extra;
        }
      }
    }
    return null;
  }

  @Override
  public int getResultIndex() {
    return myMetadata.myResultIndex;
  }

  @Override
  public boolean isEndOfFrame() {
    return myMetadata.getEndOfFrame();
  }

  @Override
  public boolean isDrawCall() {
    return myMetadata.getDrawCall();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DynamicAtom that = (DynamicAtom)o;
    if (!myValue.equals(that.myValue)) return false;
    if (!myMetadata.equals(that.myMetadata)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = myValue.hashCode();
    result = 31 * result + myMetadata.hashCode();
    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getName());
    sb.append('(');
    boolean needComma = false;
    for (int i = 0, c = getFieldCount(); i < c; i++) {
      if (!isParameter(i)) { continue; }
      if (needComma) { sb.append(", "); }
      needComma = true;
      sb.append(getFieldInfo(i).getName());
      sb.append(": ");
      sb.append(getFieldValue(i));
    }
    sb.append(')');
    return sb.toString();
  }
}
