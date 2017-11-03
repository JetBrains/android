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

import com.android.builder.model.JavaCompileOptions;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class JavaCompileOptionsStub extends BaseStub implements JavaCompileOptions {
  @NotNull private final String myEncoding;
  @NotNull private final String mySourceCompatibility;
  @NotNull private final String myTargetCompatibility;

  public JavaCompileOptionsStub() {
    this("encoding", "sourceCompatibility", "targetCompatibility");
  }

  public JavaCompileOptionsStub(@NotNull String encoding, @NotNull String sourceCompatibility, @NotNull String targetCompatibility) {
    myEncoding = encoding;
    mySourceCompatibility = sourceCompatibility;
    myTargetCompatibility = targetCompatibility;
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
    if (this == o) {
      return true;
    }
    if (!(o instanceof JavaCompileOptions)) {
      return false;
    }
    JavaCompileOptions compileOptions = (JavaCompileOptions)o;
    return Objects.equals(getEncoding(), compileOptions.getEncoding()) &&
           Objects.equals(getSourceCompatibility(), compileOptions.getSourceCompatibility()) &&
           Objects.equals(getTargetCompatibility(), compileOptions.getTargetCompatibility());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getEncoding(), getSourceCompatibility(), getTargetCompatibility());
  }

  @Override
  public String toString() {
    return "JavaCompileOptionsStub{" +
           "myEncoding='" + myEncoding + '\'' +
           ", mySourceCompatibility='" + mySourceCompatibility + '\'' +
           ", myTargetCompatibility='" + myTargetCompatibility + '\'' +
           "}";
  }
}
