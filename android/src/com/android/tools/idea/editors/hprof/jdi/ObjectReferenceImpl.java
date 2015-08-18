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
import com.android.tools.perflib.heap.Instance;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class ObjectReferenceImpl extends ValueImpl implements ObjectReference {

  public ObjectReferenceImpl(@NotNull Field field, @Nullable Instance instance) {
    super(field, instance);
  }

  @Override
  public ReferenceType referenceType() {
    return null;
  }

  @Override
  public Value getValue(com.sun.jdi.Field sig) {
    return null;
  }

  @Override
  public Map<com.sun.jdi.Field, Value> getValues(List<? extends com.sun.jdi.Field> fields) {
    return null;
  }

  @Override
  public void setValue(com.sun.jdi.Field field, Value value) throws InvalidTypeException, ClassNotLoadedException {

  }

  @Override
  public Value invokeMethod(ThreadReference thread, Method method, List<? extends Value> arguments, int options)
    throws InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException, InvocationException {
    return null;
  }

  @Override
  public void disableCollection() {

  }

  @Override
  public void enableCollection() {

  }

  @Override
  public boolean isCollected() {
    return false;
  }

  @Override
  public long uniqueID() {
    return myValue == null ? 0 : ((Instance)myValue).getUniqueId();
  }

  @Override
  public List<ThreadReference> waitingThreads() throws IncompatibleThreadStateException {
    return null;
  }

  @Override
  public ThreadReference owningThread() throws IncompatibleThreadStateException {
    return null;
  }

  @Override
  public int entryCount() throws IncompatibleThreadStateException {
    return 0;
  }

  @Override
  public List<ObjectReference> referringObjects(long maxReferrers) {
    return null;
  }

  @Override
  public Type type() {
    return new ReferenceTypeImpl(myField.getType(), myValue);
  }

  @NotNull
  public Field getField() {
    return myField;
  }

  @Nullable
  public Instance getInstance() {
    return (Instance)myValue;
  }
}
