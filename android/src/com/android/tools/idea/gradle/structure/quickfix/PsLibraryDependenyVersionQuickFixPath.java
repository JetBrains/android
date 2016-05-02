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

import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency;
import com.android.tools.idea.gradle.structure.model.PsPath;
import com.google.common.base.Joiner;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.structure.quickfix.QuickFixes.QUICK_FIX_PATH_SEPARATOR;
import static com.android.tools.idea.gradle.structure.quickfix.QuickFixes.SET_LIBRARY_DEPENDENCY_QUICK_FIX;

public class PsLibraryDependenyVersionQuickFixPath extends PsPath {
  @NotNull private final String myModuleName;
  @NotNull private final String myDependency;
  @NotNull private final String myVersion;

  public PsLibraryDependenyVersionQuickFixPath(@NotNull PsLibraryDependency dependency) {
    myModuleName = dependency.getParent().getName();
    PsArtifactDependencySpec spec = dependency.getDeclaredSpec();
    if (spec == null) {
      spec = dependency.getResolvedSpec();
    }
    myDependency = spec.compactNotation();
    String version = dependency.getResolvedSpec().version;
    assert version != null;
    myVersion = version;
  }

  @Override
  @NotNull
  public String toText(@NotNull TexType type) {
    if (type == TexType.HTML) {
      return getHtmlText();
    }
    return myDependency;
  }

  @NotNull
  private String getHtmlText() {
    String path = Joiner.on(QUICK_FIX_PATH_SEPARATOR).join(SET_LIBRARY_DEPENDENCY_QUICK_FIX, myModuleName, myDependency, myVersion);
    String href = QUICK_FIX_PATH_TYPE + path;
    return String.format("<a href='%1$s'>[Fix]</a>", href);
  }
}
