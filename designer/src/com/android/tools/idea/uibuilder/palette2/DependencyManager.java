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
package com.android.tools.idea.uibuilder.palette2;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dependencies are checked by finding the dependencies for all the components
 * on the palette that are currently missing from the project. That allows us
 * to quickly check if a certain palette item has a missing dependency with
 * {@link #needsLibraryLoad}.
 * After each Gradle sync we need to recompute the missing dependencies.
 */
public class DependencyManager {
  private final Project myProject;
  private final Set<String> myMissingLibraries;
  private Module myModule;
  private Palette myPalette;

  public DependencyManager(@NotNull Project project) {
    myProject = project;
    myMissingLibraries = new HashSet<>();
    myPalette = Palette.EMPTY;
  }

  public void setPalette(@NotNull Palette palette, @NotNull Module module) {
    myPalette = palette;
    myModule = module;
    checkForNewMissingDependencies();
  }

  public boolean needsLibraryLoad(@NotNull Palette.Item item) {
    return myMissingLibraries.contains(item.getGradleCoordinateId());
  }

  private boolean checkForNewMissingDependencies() {
    Set<String> missing = Collections.emptySet();
    if (myModule != null && !myModule.isDisposed()) {
      GradleDependencyManager manager = GradleDependencyManager.getInstance(myProject);
      List<GradleCoordinate> coordinates = toGradleCoordinatesFromIds(myPalette.getGradleCoordinateIds());
      missing = fromGradleCoordinatesToIds(manager.findMissingDependencies(myModule, coordinates));
      if (myMissingLibraries.equals(missing)) {
        return false;
      }
    }
    myMissingLibraries.clear();
    myMissingLibraries.addAll(missing);
    return true;
  }

  public void registerDependencyUpdates(@NotNull JComponent paletteUI, @NotNull Disposable parentDisposable) {
    GradleSyncState.subscribe(myProject, new GradleSyncListener.Adapter() {
      @Override
      public void syncSucceeded(@NotNull Project project) {
        if (checkForNewMissingDependencies()) {
          paletteUI.repaint();
        }
      }
    }, parentDisposable);
  }

  public void ensureLibraryIsIncluded(@NotNull Palette.Item item) {
    if (needsLibraryLoad(item)) {
      List<GradleCoordinate> dependencies =
        Collections.singletonList(GradleCoordinate.parseCoordinateString(item.getGradleCoordinateId() + ":+"));
      if (GradleDependencyManager.userWantToAddDependencies(myModule, dependencies)) {
        GradleDependencyManager manager = GradleDependencyManager.getInstance(myProject);
        manager.addDependencies(myModule, dependencies, null);
      }
    }
  }

  @NotNull
  private static Set<String> fromGradleCoordinatesToIds(@NotNull Collection<GradleCoordinate> coordinates) {
    return coordinates.stream()
      .map(GradleCoordinate::getId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  @NotNull
  private static List<GradleCoordinate> toGradleCoordinatesFromIds(@NotNull Collection<String> dependencies) {
    return dependencies.stream()
      .map(dependency -> GradleCoordinate.parseCoordinateString(dependency + ":+"))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }
}
