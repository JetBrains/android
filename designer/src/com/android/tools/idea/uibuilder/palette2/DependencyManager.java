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

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.projectsystem.ProjectSystemService;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.android.refactoring.MigrateToAndroidxUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Keeps track of which of the dependencies for all the components on a palette are currently
 * missing from a project. DependencyManager does this by caching dependency information available
 * through the instance of {@link AndroidModuleSystem} associated with the module of the palette.
 * This allows callers to quickly check if a particular palette item has a missing dependency via
 * the {@link #needsLibraryLoad(Palette.Item)} method.
 *
 * The set of missing dependencies is recomputed each time the project is synced (in case new dependencies have
 * been added to the palette's module) and each time the associated palette changes (see {@link #setPalette}).
 */
public class DependencyManager implements Disposable {
  private final Project myProject;
  private final Set<String> myMissingLibraries;
  private final List<DependencyChangeListener> myListeners;
  private Module myModule;
  private Palette myPalette;
  private boolean myUseAndroidXDependencies;
  private boolean myRegisteredForDependencyUpdates;
  private boolean myNotifyAlways;

  public DependencyManager(@NotNull Project project) {
    myProject = project;
    myMissingLibraries = new HashSet<>();
    myListeners = new ArrayList<>(2);
    myPalette = Palette.EMPTY;
  }

  public void addDependencyChangeListener(@NotNull DependencyChangeListener listener) {
    myListeners.add(listener);
  }

  public void setPalette(@NotNull Palette palette, @NotNull Module module) {
    myPalette = palette;
    myModule = module;
    checkForRelevantDependencyChanges();
    registerDependencyUpdates();
  }

  public boolean useAndroidXDependencies() {
    return myUseAndroidXDependencies;
  }

  public boolean needsLibraryLoad(@NotNull Palette.Item item) {
    return myMissingLibraries.contains(item.getGradleCoordinateId());
  }

  public boolean dependsOn(@NotNull GoogleMavenArtifactId artifactId) {
    return DependencyManagementUtil.dependsOn(myModule, artifactId);
  }

  @TestOnly
  public void setNotifyAlways(boolean notifyAlways) {
    myNotifyAlways = notifyAlways;
  }

  @Override
  public void dispose() {
  }

  private boolean checkForRelevantDependencyChanges() {
    return checkForNewMissingDependencies() | checkForNewAndroidXDependencies();
  }

  private boolean checkForNewAndroidXDependencies() {
    boolean useAndroidX = computeUseAndroidXDependencies();
    if (useAndroidX == myUseAndroidXDependencies) {
      return false;
    }
    myUseAndroidXDependencies = useAndroidX;
    return true;
  }

  private boolean checkForNewMissingDependencies() {
    Set<String> missing = Collections.emptySet();

    if (myModule != null && !myModule.isDisposed()) {
      AndroidModuleSystem moduleSystem = ProjectSystemService.getInstance(myProject).getProjectSystem().getModuleSystem(myModule);
      missing = myPalette.getGradleCoordinateIds().stream()
        .map(id -> GradleCoordinate.parseCoordinateString(id + ":+"))
        .filter(Objects::nonNull)
        .filter(coordinate -> moduleSystem.getRegisteredDependency(coordinate) == null)
        .map(coordinate -> coordinate.getId())
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

      if (myMissingLibraries.equals(missing)) {
        return false;
      }
    }

    myMissingLibraries.clear();
    myMissingLibraries.addAll(missing);
    return true;
  }

  private void registerDependencyUpdates() {
    if (myRegisteredForDependencyUpdates) {
      return;
    }
    myRegisteredForDependencyUpdates = true;
    myProject.getMessageBus().connect(this).subscribe(PROJECT_SYSTEM_SYNC_TOPIC, result -> {
      if ((result == ProjectSystemSyncManager.SyncResult.SUCCESS && checkForRelevantDependencyChanges()) || myNotifyAlways) {
        myListeners.forEach(listener -> listener.onDependenciesChanged());
      }
    });
  }

  public void ensureLibraryIsIncluded(@NotNull Palette.Item item) {
    if (needsLibraryLoad(item)) {
      GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(item.getGradleCoordinateId() + ":+");

      if (coordinate != null) {
        DependencyManagementUtil.addDependencies(myModule, Collections.singletonList(coordinate), true);
      }
    }
  }

  private boolean computeUseAndroidXDependencies() {
    if (MigrateToAndroidxUtil.hasAndroidxProperty(myProject)) {
      return MigrateToAndroidxUtil.isAndroidx(myProject);
    }

    if (DependencyManagementUtil.dependsOnAndroidx(myModule)) {
      // It already depends on androidx
      return true;
    }

    // It does not depend on androidx. Check default to using androidx unless the module already depends on android.support
    return !DependencyManagementUtil.dependsOnOldSupportLib(myModule) && StudioFlags.NELE_USE_ANDROIDX_DEFAULT.get();
  }

  public interface DependencyChangeListener {
    void onDependenciesChanged();
  }
}
