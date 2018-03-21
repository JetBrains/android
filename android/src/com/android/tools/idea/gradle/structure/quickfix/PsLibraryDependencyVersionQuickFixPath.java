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
import com.android.tools.idea.gradle.structure.model.PsPath;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.structure.configurables.issues.QuickFixLinkHandler.QUICK_FIX_PATH_TYPE;
import static com.android.tools.idea.gradle.structure.quickfix.QuickFixes.QUICK_FIX_PATH_SEPARATOR;
import static com.android.tools.idea.gradle.structure.quickfix.QuickFixes.SET_LIBRARY_DEPENDENCY_QUICK_FIX;

public final class PsLibraryDependencyVersionQuickFixPath extends PsPath {
  public static final String DEFAULT_QUICK_FIX_TEXT = "[Fix]";

  @NotNull private final String myModuleName;
  @NotNull private final String myDependency;
  @NotNull private final String myVersion;
  @NotNull private final String myQuickFixText;

  public PsLibraryDependencyVersionQuickFixPath(@NotNull PsLibraryDependency dependency, @NotNull String quickFixText) {
    super(null);
    myModuleName = dependency.getParent().getName();
    myDependency = getCompactNotation(dependency);
    String version = dependency.getResolvedSpec().getVersion();
    assert version != null;
    myVersion = version;
    myQuickFixText = quickFixText;
  }

  public PsLibraryDependencyVersionQuickFixPath(@NotNull PsLibraryDependency dependency,
                                                @NotNull String version,
                                                @NotNull String quickFixText) {
    super(null);
    myModuleName = dependency.getParent().getName();
    myDependency = getCompactNotation(dependency);
    myVersion = version;
    myQuickFixText = quickFixText;
  }

  @NotNull
  private static String getCompactNotation(@NotNull PsLibraryDependency dependency) {
    PsArtifactDependencySpec spec = dependency.getDeclaredSpec();
    if (spec == null) {
      spec = dependency.getResolvedSpec();
    }
    return spec.compactNotation();
  }

  @Override
  @NotNull
  public String toText(@NotNull TexType type) {
    return myDependency;
  }

  @Override
  @NotNull
  public String getHyperlinkDestination(@NotNull PsContext context) {
    String path = Joiner.on(QUICK_FIX_PATH_SEPARATOR).join(SET_LIBRARY_DEPENDENCY_QUICK_FIX, myModuleName, myDependency, myVersion);
    return QUICK_FIX_PATH_TYPE + path;
  }

  @NotNull
  @Override
  public String getHtml(@NotNull PsContext context) {
    return String.format("<a href=\"%s\">%s</a>", getHyperlinkDestination(context), myQuickFixText);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    PsLibraryDependencyVersionQuickFixPath path = (PsLibraryDependencyVersionQuickFixPath)o;
    return Objects.equal(myModuleName, path.myModuleName) &&
           Objects.equal(myDependency, path.myDependency) &&
           Objects.equal(myVersion, path.myVersion) &&
           Objects.equal(myQuickFixText, path.myQuickFixText);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), myModuleName, myDependency, myVersion, myQuickFixText);
  }
}
