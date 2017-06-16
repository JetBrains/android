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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.ClassField;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;

/**
 * Creates a deep copy of a {@link ClassField}.
 */
public final class IdeClassField extends IdeModel implements ClassField {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final String myName;
  @NotNull private final String myType;
  @NotNull private final String myValue;
  private final int myHashCode;

  public IdeClassField(@NotNull ClassField classField, @NotNull ModelCache modelCache) {
    super(classField, modelCache);
    myName = classField.getName();
    myType = classField.getType();
    myValue = classField.getValue();

    myHashCode = calculateHashCode();
  }

  @Override
  @NotNull
  public String getType() {
    return myType;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
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
    if (!(o instanceof IdeClassField)) {
      return false;
    }
    IdeClassField field = (IdeClassField)o;
    return Objects.equals(myName, field.myName) &&
           Objects.equals(myType, field.myType) &&
           Objects.equals(myValue, field.myValue);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myName, myType, myValue);
  }

  @Override
  public String toString() {
    return "IdeClassField{" +
           "myName='" + myName + '\'' +
           ", myType='" + myType + '\'' +
           ", myValue='" + myValue + '\'' +
           '}';
  }
}
