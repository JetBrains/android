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
import com.android.support.AndroidxNameUtils;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;

/**
 * Keeps track of which of the dependencies for all the components on a palette are currently
 * missing from a project. DependencyManager does this by caching dependency information available
 * through the instance of {@link com.android.tools.idea.projectsystem.AndroidModuleSystem} associated
 * with the module of the palette. This allows callers to quickly check if a particular palette item has
 * a missing dependency via the {@link #needsLibraryLoad(Palette.Item)} method.
 *
 * The set of missing dependencies is recomputed each time the project is synced (in case new dependencies have
 * been added to the palette's module) and each time the associated palette changes (see {@link #setPalette(Palette, Module)}).
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
      missing = myPalette.getGradleCoordinateIds().stream()
        .map(id -> GradleCoordinate.parseCoordinateString(id + ":+"))
        .filter(Objects::nonNull)
        .map(GoogleMavenArtifactId::forCoordinate)
        .filter(artifactId -> artifactId != null && !DependencyManagementUtil.dependsOn(myModule, artifactId))
        .map(GoogleMavenArtifactId::toString)
        .collect(Collectors.toSet());

      if (myMissingLibraries.equals(missing)) {
        return false;
      }
    }

    myMissingLibraries.clear();
    myMissingLibraries.addAll(missing);
    return true;
  }

  public void registerDependencyUpdates(@NotNull JComponent paletteUI, @NotNull Disposable parentDisposable) {
    myProject.getMessageBus().connect(parentDisposable).subscribe(PROJECT_SYSTEM_SYNC_TOPIC, result -> {
      if (result == ProjectSystemSyncManager.SyncResult.SUCCESS && checkForNewMissingDependencies()) {
        paletteUI.repaint();
      }
    });
  }

  public void ensureLibraryIsIncluded(@NotNull Palette.Item item) {
    if (needsLibraryLoad(item)) {
      GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(item.getGradleCoordinateId() + ":+");

      if (coordinate != null) {
        GoogleMavenArtifactId artifactId = GoogleMavenArtifactId.forCoordinate(coordinate);

        if (artifactId != null) {
          DependencyManagementUtil.addDependencies(myModule, Collections.singletonList(artifactId), true);
        }
      }
    }
  }

  public boolean useAndroidxDependencies() {
    return !DependencyManagementUtil.dependsOnOldSupportLib(myModule) && StudioFlags.NELE_USE_ANDROIDX_DEFAULT.get();
  }
}