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
package com.android.tools.idea.apk.viewer.dex;

import com.google.common.collect.Iterables;
import org.jetbrains.annotations.NotNull;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;

import java.util.Set;

public class DexFileStats {
  public final int classCount;
  public final int definedMethodCount;
  public final int referencedMethodCount;

  private DexFileStats(int classCount, int definedMethodCount, int referencedMethodCount) {
    this.classCount = classCount;
    this.definedMethodCount = definedMethodCount;
    this.referencedMethodCount = referencedMethodCount;
  }

  @NotNull
  public static DexFileStats create(@NotNull DexBackedDexFile dexFile) {
    int definedMethodCount = 0;
    Set<? extends DexBackedClassDef> classes = dexFile.getClasses();
    for (DexBackedClassDef dexBackedClassDef : classes) {
      definedMethodCount += Iterables.size(dexBackedClassDef.getMethods());
    }

    return new DexFileStats(classes.size(), definedMethodCount, dexFile.getMethodCount());
  }
}
