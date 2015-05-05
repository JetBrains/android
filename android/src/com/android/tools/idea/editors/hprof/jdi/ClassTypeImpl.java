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
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ClassTypeImpl extends ReferenceTypeImpl implements ClassType {

  public ClassTypeImpl(@NotNull Type type, @Nullable Object value) {
    super(type, value);
  }

  @Override
  public ClassType superclass() {
    return null;
  }

  @Override
  public List<InterfaceType> interfaces() {
    return new ArrayList<InterfaceType>(0);
  }

  @Override
  public List<InterfaceType> allInterfaces() {
    return new ArrayList<InterfaceType>(0);
  }

  @Override
  public List<ClassType> subclasses() {
    return new ArrayList<ClassType>(0);
  }

  @Override
  public boolean isEnum() {
    // TODO: Figure out if this can inferred.
    return false;
  }

  @Override
  public void setValue(Field field, Value value) throws InvalidTypeException, ClassNotLoadedException {

  }

  @Override
  public Value invokeMethod(ThreadReference thread, Method method, List<? extends Value> arguments, int options)
    throws InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException, InvocationException {
    return null;
  }

  @Override
  public ObjectReference newInstance(ThreadReference thread, Method method, List<? extends Value> arguments, int options)
    throws InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException, InvocationException {
    return null;
  }

  @Override
  public Method concreteMethodByName(String name, String signature) {
    return null;
  }
}
