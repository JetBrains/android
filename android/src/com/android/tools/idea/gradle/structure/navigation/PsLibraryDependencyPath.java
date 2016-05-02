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
package com.android.tools.idea.gradle.structure.navigation;

import com.android.tools.idea.gradle.structure.configurables.DependenciesPerspectiveConfigurable;
import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency;
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable;
import com.google.common.base.Objects;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.gradle.structure.navigation.Places.serialize;
import static com.android.tools.idea.structure.dialog.ProjectStructureConfigurable.putPath;

public class PsLibraryDependencyPath extends PsNavigationPath {
  @NotNull private final PsContext myContext;
  @NotNull private final String myModuleName;
  @NotNull private final String myDependency;

  public PsLibraryDependencyPath(@NotNull PsContext context, @NotNull PsLibraryDependency dependency) {
    myContext = context;
    myModuleName = dependency.getParent().getName();
    PsArtifactDependencySpec spec = dependency.getDeclaredSpec();
    if (spec == null) {
      spec = dependency.getResolvedSpec();
    }
    myDependency = spec.name + GRADLE_PATH_SEPARATOR + spec.version;
  }

  @Override
  @NotNull
  public String getPlainText() {
    return myDependency;
  }

  @Override
  @NotNull
  public String getHtml() {
    Place place = new Place();

    ProjectStructureConfigurable mainConfigurable = myContext.getMainConfigurable();
    DependenciesPerspectiveConfigurable target = mainConfigurable.findConfigurable(DependenciesPerspectiveConfigurable.class);
    assert target != null;

    putPath(place, target);
    target.putPath(place, myModuleName, myDependency);

    String href = GO_TO_PATH_TYPE + serialize(place);
    return String.format("<a href='%1$s'>%2$s</a> (<b>'%3$s'</b>)", href, myDependency, myModuleName);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PsLibraryDependencyPath that = (PsLibraryDependencyPath)o;
    return Objects.equal(myModuleName, that.myModuleName) && Objects.equal(myDependency, that.myDependency);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myModuleName, myDependency);
  }

  @Override
  public String toString() {
    return getPlainText();
  }
}
