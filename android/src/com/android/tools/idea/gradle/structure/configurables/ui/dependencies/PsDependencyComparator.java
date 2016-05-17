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
package com.android.tools.idea.gradle.structure.configurables.ui.dependencies;

import com.android.tools.idea.gradle.structure.model.PsDependency;
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency;
import com.android.tools.idea.gradle.structure.model.PsModuleDependency;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

import static com.android.tools.idea.gradle.structure.model.PsDependency.TextType.PLAIN_TEXT;

public class PsDependencyComparator implements Comparator<PsDependency> {
  @NotNull public static final PsDependencyComparator INSTANCE = new PsDependencyComparator();

  private PsDependencyComparator() {
  }

  @Override
  public int compare(PsDependency d1, PsDependency d2) {
    if (d1 instanceof PsLibraryDependency) {
      if (d2 instanceof PsLibraryDependency) {
        String s1 = ((PsLibraryDependency)d1).getResolvedSpec().getDisplayText();
        String s2 = ((PsLibraryDependency)d2).getResolvedSpec().getDisplayText();
        return s1.compareTo(s2);
      }
    }
    else if (d1 instanceof PsModuleDependency) {
      if (d2 instanceof PsModuleDependency) {
        return d1.toText(PLAIN_TEXT).compareTo(d2.toText(PLAIN_TEXT));
      }
      else if (d2 instanceof PsLibraryDependency) {
        return 1;
      }
    }
    return -1;
  }
}
