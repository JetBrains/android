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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview;

import com.android.tools.idea.gradle.structure.model.android.PsAndroidArtifact;
import com.android.tools.idea.gradle.structure.model.android.PsVariant;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;

public class ArtifactComparator implements Comparator<PsAndroidArtifact> {
  @NotNull public static final ArtifactComparator INSTANCE = new ArtifactComparator();

  @NotNull private static ArtifactNameComparator ourByName = new ArtifactNameComparator();

  @Override
  public int compare(PsAndroidArtifact a1, PsAndroidArtifact a2) {
    PsVariant v1 = a1.getParent();
    PsVariant v2 = a2.getParent();
    int compareVariantName = v1.getName().compareTo(v2.getName());
    if (compareVariantName == 0) {
      return byName().compare(a1.getName(), a2.getName());
    }
    return compareVariantName;
  }

  @NotNull
  public static ArtifactNameComparator byName() {
    return ourByName;
  }

  public static class ArtifactNameComparator implements Comparator<String> {
    private ArtifactNameComparator() {
    }

    @Override
    public int compare(String s1, String s2) {
      if (s1.equals(ARTIFACT_MAIN)) {
        return -1; // always first.
      }
      else if (s2.equals(ARTIFACT_MAIN)) {
        return 1;
      }
      return s1.compareTo(s2);
    }
  }
}
