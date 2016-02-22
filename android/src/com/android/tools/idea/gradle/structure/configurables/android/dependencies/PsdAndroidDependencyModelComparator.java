/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdLibraryDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdModuleDependencyModel;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class PsdAndroidDependencyModelComparator implements Comparator<PsdAndroidDependencyModel> {
  @NotNull public static final PsdAndroidDependencyModelComparator INSTANCE = new PsdAndroidDependencyModelComparator();

  private PsdAndroidDependencyModelComparator() {
  }

  @Override
  public int compare(PsdAndroidDependencyModel m1, PsdAndroidDependencyModel m2) {
    if (m1 instanceof PsdLibraryDependencyModel) {
      if (m2 instanceof PsdLibraryDependencyModel) {
        String s1 = ((PsdLibraryDependencyModel)m1).getResolvedSpec().getDisplayText();
        String s2 = ((PsdLibraryDependencyModel)m2).getResolvedSpec().getDisplayText();
        return s1.compareTo(s2);
      }
    }
    else if (m1 instanceof PsdModuleDependencyModel) {
      if (m2 instanceof PsdModuleDependencyModel) {
        return m1.getValueAsText().compareTo(m2.getValueAsText());
      }
      else if (m2 instanceof PsdLibraryDependencyModel) {
        return 1;
      }
    }
    return -1;
  }
}
