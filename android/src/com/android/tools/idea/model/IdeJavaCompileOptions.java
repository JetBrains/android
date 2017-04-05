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

import com.android.builder.model.JavaCompileOptions;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Objects;

/**
 * Creates a deep copy of {@link JavaCompileOptions}.
 *
 * @see IdeAndroidProject
 */
public class IdeJavaCompileOptions implements JavaCompileOptions, Serializable {
  @NotNull private final String myEncoding;
  @NotNull private final String mySourceCompatibility;
  @NotNull private final String myTargetCompatibility;

  public IdeJavaCompileOptions(@NotNull JavaCompileOptions options) {
    myEncoding = options.getEncoding();
    mySourceCompatibility = options.getSourceCompatibility();
    myTargetCompatibility = options.getTargetCompatibility();
  }

  @Override
  @NotNull
  public String getEncoding() {
    return myEncoding;
  }

  @Override
  @NotNull
  public String getSourceCompatibility() {
    return mySourceCompatibility;
  }

  @Override
  @NotNull
  public String getTargetCompatibility() {
    return myTargetCompatibility;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JavaCompileOptions)) return false;
    JavaCompileOptions options = (JavaCompileOptions)o;
    return Objects.equals(getEncoding(), options.getEncoding()) &&
           Objects.equals(getSourceCompatibility(), options.getSourceCompatibility()) &&
           Objects.equals(getTargetCompatibility(), options.getTargetCompatibility());
  }

}
