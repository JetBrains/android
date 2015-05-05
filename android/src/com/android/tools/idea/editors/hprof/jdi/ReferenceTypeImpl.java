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

import java.util.List;
import java.util.Map;

public class ReferenceTypeImpl extends TypeImpl implements ReferenceType {

  public ReferenceTypeImpl(@NotNull Type type, @Nullable Object value) {
    super(type, value);
  }

  @Override
  public String genericSignature() {
    return "";
  }

  @Override
  public ClassLoaderReference classLoader() {
    return null;
  }

  @Override
  public String sourceName() {
    return null;
  }

  @Override
  public List<String> sourceNames(String stratum) {
    return null;
  }

  @Override
  public List<String> sourcePaths(String stratum) {
    return null;
  }

  @Override
  public String sourceDebugExtension() {
    return "";
  }

  @Override
  public boolean isStatic() {
    return false;
  }

  @Override
  public boolean isAbstract() {
    return false;
  }

  @Override
  public boolean isFinal() {
    return false;
  }

  @Override
  public boolean isPrepared() {
    return false;
  }

  @Override
  public boolean isVerified() {
    return false;
  }

  @Override
  public boolean isInitialized() {
    return true;
  }

  @Override
  public boolean failedToInitialize() {
    return false;
  }

  @Override
  public List<Field> fields() {
    return null;
  }

  @Override
  public List<Field> visibleFields() {
    return null;
  }

  @Override
  public List<Field> allFields() {
    return null;
  }

  @Override
  public Field fieldByName(String fieldName) {
    return null;
  }

  @Override
  public List<Method> methods() {
    return null;
  }

  @Override
  public List<Method> visibleMethods() {
    return null;
  }

  @Override
  public List<Method> allMethods() {
    return null;
  }

  @Override
  public List<Method> methodsByName(String name) {
    return null;
  }

  @Override
  public List<Method> methodsByName(String name, String signature) {
    return null;
  }

  @Override
  public List<ReferenceType> nestedTypes() {
    return null;
  }

  @Override
  public Value getValue(Field field) {
    return null;
  }

  @Override
  public Map<Field, Value> getValues(List<? extends Field> fields) {
    return null;
  }

  @Override
  public ClassObjectReference classObject() {
    return null;
  }

  @Override
  public List<Location> allLineLocations() {
    return null;
  }

  @Override
  public List<Location> allLineLocations(String stratum, String sourceName) {
    return null;
  }

  @Override
  public List<Location> locationsOfLine(int lineNumber) {
    return null;
  }

  @Override
  public List<Location> locationsOfLine(String stratum, String sourceName, int lineNumber) {
    return null;
  }

  @Override
  public List<String> availableStrata() {
    return null;
  }

  @Override
  public String defaultStratum() {
    return null;
  }

  @Override
  public List<ObjectReference> instances(long maxInstances) {
    return null;
  }

  @Override
  public int majorVersion() {
    return 0;
  }

  @Override
  public int minorVersion() {
    return 0;
  }

  @Override
  public int constantPoolCount() {
    return 0;
  }

  @Override
  public byte[] constantPool() {
    return new byte[0];
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
    return false;
  }

  @Override
  public int compareTo(ReferenceType o) {
    return 0;
  }
}
