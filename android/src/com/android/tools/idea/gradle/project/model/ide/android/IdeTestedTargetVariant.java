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

import com.android.builder.model.TestedTargetVariant;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Creates a deep copy of a {@link TestedTargetVariant}.
 */
public final class IdeTestedTargetVariant extends IdeModel implements TestedTargetVariant {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final String myTargetProjectPath;
  @NotNull private final String myTargetVariant;
  private final int myHashCode;

  public IdeTestedTargetVariant(@NotNull TestedTargetVariant variant, @NotNull ModelCache modelCache) {
    super(variant, modelCache);
    myTargetProjectPath = variant.getTargetProjectPath();
    myTargetVariant = variant.getTargetVariant();

    myHashCode = calculateHashCode();
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
    if (!(o instanceof IdeTestedTargetVariant)) {
      return false;
    }
    IdeTestedTargetVariant variants = (IdeTestedTargetVariant)o;
    return Objects.equals(myTargetProjectPath, variants.myTargetProjectPath) &&
           Objects.equals(myTargetVariant, variants.myTargetVariant);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myTargetProjectPath, myTargetVariant);
  }

  @Override
  public String toString() {
    return "IdeTestedTargetVariants{" +
           "myTargetProjectPath='" + myTargetProjectPath + '\'' +
           ", myTargetVariant='" + myTargetVariant + '\'' +
           "}";
  }
}
