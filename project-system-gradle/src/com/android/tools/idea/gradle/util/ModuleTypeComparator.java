/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import java.text.Collator;
import java.util.Comparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModuleTypeComparator implements Comparator<Module> {
  public static final ModuleTypeComparator INSTANCE = new ModuleTypeComparator();

  @Override
  public int compare(Module m1, Module m2) {
    GradleAndroidModel gm1 = GradleAndroidModel.get(m1);
    GradleAndroidModel gm2 = GradleAndroidModel.get(m2);
    return compareModules(m1, m2, gm1, gm2);
  }

  @VisibleForTesting
  static int compareModules(@NotNull Module m1, @NotNull Module m2, @Nullable GradleAndroidModel gm1, @Nullable GradleAndroidModel gm2) {
    if ((gm1 == null && gm2 == null) ||
        (gm1 != null && gm2 != null && gm1.getAndroidProject().getProjectType() == gm2.getAndroidProject().getProjectType())) {
      return Collator.getInstance().compare(m1.getName(), m2.getName());
    }
    if (gm1 != null) {
      if (gm2 != null) {
        return gm1.getAndroidProject().getProjectType().ordinal() - gm2.getAndroidProject().getProjectType().ordinal();
      }
      return -1;
    }
    return 1;
  }
}
