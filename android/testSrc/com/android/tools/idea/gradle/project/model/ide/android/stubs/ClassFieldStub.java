/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model.ide.android.stubs;

import com.android.builder.model.ClassField;
import com.android.tools.idea.gradle.project.model.ide.android.UnusedModelMethodException;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;

public final class ClassFieldStub extends BaseStub implements ClassField {
  @NotNull private final String myName;
  @NotNull private final String myType;
  @NotNull private final String myValue;

  public ClassFieldStub() {
    this("name", "type", "value");
  }

  public ClassFieldStub(@NotNull String name, @NotNull String type, @NotNull String value) {
    myName = name;
    myType = type;
    myValue = value;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public String getType() {
    return myType;
  }

  @Override
  @NotNull
  public String getValue() {
    return myValue;
  }

  @Override
  @NotNull
  public String getDocumentation() {
    throw new UnusedModelMethodException("getDocumentation");
  }

  @Override
  @NotNull
  public Set<String> getAnnotations() {
    throw new UnusedModelMethodException("getAnnotations");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ClassField)) {
      return false;
    }
    ClassField stub = (ClassField)o;
    return Objects.equals(getName(), stub.getName()) &&
           Objects.equals(getType(), stub.getType()) &&
           Objects.equals(getValue(), stub.getValue());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getType(), getValue());
  }

  @Override
  public String toString() {
    return "ClassFieldStub{" +
           "myName='" + myName + '\'' +
           ", myType='" + myType + '\'' +
           ", myValue='" + myValue + '\'' +
           "}";
  }
}
