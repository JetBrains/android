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
package com.android.tools.idea.model;

import com.android.builder.model.ClassField;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Creates a deep copy of {@link ClassField}.
 *
 * @see IdeAndroidProject
 */
public class IdeClassField implements ClassField, Serializable {
  @NotNull private final String myType;
  @NotNull private final String myName;
  @NotNull private final String myValue;
  @NotNull private final String myDocumentation;
  @NotNull private final Set<String> myAnnotations;

  public IdeClassField(@NotNull ClassField classField) {
    myType = classField.getType();
    myName = classField.getName();
    myValue = classField.getValue();
    myDocumentation = classField.getDocumentation();
    myAnnotations = new HashSet<>(classField.getAnnotations());
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
    return myDocumentation;
  }

  @Override
  @NotNull
  public Set<String> getAnnotations() {
    return myAnnotations;
  }
}
