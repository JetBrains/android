/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.memory.adapters.classifiers;

import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

/**
 * Classifies {@link InstanceObject}s based on its package name. Primitive arrays are classified as leaf nodes directly under the root.
 */
public class PackageSet extends ClassifierSet {
  @NotNull private final CaptureObject myCaptureObject;
  private final int myPackageNameIndex;

  @NotNull
  public static Classifier createDefaultClassifier(@NotNull CaptureObject captureObject) {
    return packageClassifier(captureObject, 0);
  }

  public PackageSet(@NotNull CaptureObject captureObject, @NotNull String packageElementName, int packageNameIndex) {
    super(packageElementName);
    myCaptureObject = captureObject;
    myPackageNameIndex = packageNameIndex;
  }

  @NotNull
  @Override
  public Classifier createSubClassifier() {
    return packageClassifier(myCaptureObject, myPackageNameIndex + 1);
  }

  private static Classifier packageClassifier(CaptureObject captureObject, int packageNameIndex) {
    return new Classifier.Join<>(packageElementAt(packageNameIndex), elem -> new PackageSet(captureObject, elem, packageNameIndex),
                                 Classifier.of(InstanceObject::getClassEntry, ClassSet::new));
  }

  private static Function1<InstanceObject, String> packageElementAt(int packageNameIndex) {
    return inst -> packageNameIndex < inst.getClassEntry().getSplitPackageName().length
                   ? inst.getClassEntry().getSplitPackageName()[packageNameIndex]
                   : null;
  }
}
