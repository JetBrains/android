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
package com.android.tools.idea.gradle.structure.quickfix;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.google.common.base.Splitter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class QuickFixes {
  public static final char QUICK_FIX_PATH_SEPARATOR = '/';
  @NonNls public static final String SET_LIBRARY_DEPENDENCY_QUICK_FIX = "setLibraryDependency";

  private QuickFixes() {
  }

  public static void executeQuickFix(@NotNull String quickFix, @NonNls PsContext context) {
    List<String> segments = Splitter.on(QUICK_FIX_PATH_SEPARATOR).splitToList(quickFix);
    assert !segments.isEmpty();

    String action = segments.get(0);
    if (SET_LIBRARY_DEPENDENCY_QUICK_FIX.equals(action)) {
      assert segments.size() == 4;
      String moduleName = segments.get(1);
      String dependency = segments.get(2);
      String version = segments.get(3);
      setLibraryDependencyVersion(context, moduleName, dependency, version);
    }
  }

  private static void setLibraryDependencyVersion(@NonNls PsContext context,
                                                  @NotNull String moduleName,
                                                  @NotNull String dependency,
                                                  @NotNull String version) {
    PsModule module = context.getProject().findModuleByName(moduleName);
    if (module instanceof PsAndroidModule) {
      PsAndroidModule androidModule = (PsAndroidModule)module;
      androidModule.forEachDeclaredDependency(declaredDependency -> {
        if (declaredDependency instanceof PsLibraryDependency) {
          PsLibraryDependency libraryDependency = (PsLibraryDependency)declaredDependency;
          PsArtifactDependencySpec declaredSpec = libraryDependency.getDeclaredSpec();
          if (declaredSpec != null && dependency.equals(declaredSpec.compactNotation())) {
            libraryDependency.setVersion(version);
          }
        }
      });
    }
  }
}
