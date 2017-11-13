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
package com.android.tools.profilers.memory.adapters;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Classifies {@link InstanceObject}s based on its package name. Primitive arrays are classified as leaf nodes directly under the root.
 */
public class PackageSet extends ClassifierSet {
  @NotNull private final CaptureObject myCaptureObject;
  private final int myPackageNameIndex;

  @NotNull
  public static Classifier createDefaultClassifier(@NotNull CaptureObject captureObject) {
    return new PackageClassifier(captureObject, 0);
  }

  public PackageSet(@NotNull CaptureObject captureObject, @NotNull String packageElementName, int packageNameIndex) {
    super(packageElementName);
    myCaptureObject = captureObject;
    myPackageNameIndex = packageNameIndex;
  }

  @NotNull
  @Override
  public Classifier createSubClassifier() {
    return new PackageClassifier(myCaptureObject, myPackageNameIndex + 1);
  }

  private static final class PackageClassifier extends Classifier {
    @NotNull private final Map<String, PackageSet> myPackageElements = new LinkedHashMap<>();
    @NotNull private final Map<ClassDb.ClassEntry, ClassSet> myClassMap = new LinkedHashMap<>();
    @NotNull private final CaptureObject myCaptureObject;
    private final int myPackageNameIndex;

    private PackageClassifier(@NotNull CaptureObject captureObject, int packageNameIndex) {
      myCaptureObject = captureObject;
      myPackageNameIndex = packageNameIndex;
    }

    @NotNull
    @Override
    public ClassifierSet getOrCreateClassifierSet(@NotNull InstanceObject instance) {
      if (myPackageNameIndex >= instance.getClassEntry().getSplitPackageName().length) {
        return myClassMap.computeIfAbsent(instance.getClassEntry(), ClassSet::new);
      }
      else {
        return myPackageElements.computeIfAbsent(instance.getClassEntry().getSplitPackageName()[myPackageNameIndex],
                                                 name -> new PackageSet(myCaptureObject, name, myPackageNameIndex));
      }
    }

    @NotNull
    @Override
    public List<ClassifierSet> getFilteredClassifierSets() {
      return Stream.concat(myPackageElements.values().stream(), myClassMap.values().stream()).filter(child -> !child.getIsFiltered()).collect(Collectors.toList());
    }

    @NotNull
    @Override
    protected List<ClassifierSet> getAllClassifierSets() {
      return Stream.concat(myPackageElements.values().stream(), myClassMap.values().stream()).collect(Collectors.toList());
    }
  }
}
