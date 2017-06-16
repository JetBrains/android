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

import com.android.builder.model.TestedTargetVariant;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class TestedTargetVariantStub extends BaseStub implements TestedTargetVariant {
  @NotNull private final String myTargetProjectPath;
  @NotNull private final String myTargetVariant;

  public TestedTargetVariantStub() {
    this("targetProjectPath", "targetVariant");
  }

  public TestedTargetVariantStub(@NotNull String targetProjectPath, @NotNull String targetVariant) {
    myTargetProjectPath = targetProjectPath;
    myTargetVariant = targetVariant;
  }

  @Override
  @NotNull
  public String getTargetProjectPath() {
    return myTargetProjectPath;
  }

  @Override
  @NotNull
  public String getTargetVariant() {
    return myTargetVariant;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TestedTargetVariant)) {
      return false;
    }
    TestedTargetVariant stub = (TestedTargetVariant)o;
    return Objects.equals(getTargetProjectPath(), stub.getTargetProjectPath()) &&
           Objects.equals(getTargetVariant(), stub.getTargetVariant());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getTargetProjectPath(), getTargetVariant());
  }

  @Override
  public String toString() {
    return "TestedTargetVariantStub{" +
           "myTargetProjectPath='" + myTargetProjectPath + '\'' +
           ", myTargetVariant='" + myTargetVariant + '\'' +
           "}";
  }
}
