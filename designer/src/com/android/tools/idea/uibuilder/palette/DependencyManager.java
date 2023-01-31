/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ProjectSystemService;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jetbrains.android.refactoring.MigrateToAndroidxUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.ide.PooledThreadExecutor;

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
  private final List<DependencyChangeListener> myListeners;
  private final AtomicReference<Set<String>> myMissingLibraries;
  private final AtomicBoolean myUseAndroidXDependencies;
  private final Object mySync;
  @GuardedBy("mySync")
  private Module myModule;
  @GuardedBy("mySync")
  private Palette myPalette;
  private boolean myRegisteredForDependencyUpdates;
  private boolean myNotifyAlways;
  private Consumer<Future<?>> mySyncTopicConsumer;

  public DependencyManager(@NotNull Project project) {
    myProject = project;
    myMissingLibraries = new AtomicReference<>(Collections.emptySet());
    myUseAndroidXDependencies = new AtomicBoolean(false);
    myListeners = new ArrayList<>(2);
    myPalette = Palette.EMPTY;
    mySyncTopicConsumer = future -> {};
    mySync = new Object();
  }

  public void addDependencyChangeListener(@NotNull DependencyChangeListener listener) {
    myListeners.add(listener);
  }

  /**
   * This method sets the {@link Palette} and {@link Module} to be used by the dependency manager as source for the data.
   * The method runs expensive computations to calculate the dependency changes so do not run this method on the UI thread.
   */
  public void setPalette(@NotNull Palette palette, @NotNull Module module) {
    synchronized (mySync) {
      myPalette = palette;
      myModule = module;
    }

    checkForRelevantDependencyChanges();
    registerDependencyUpdates();
    ApplicationManager.getApplication().invokeLater(() -> myListeners.forEach(listener -> listener.onDependenciesChanged()));
  }

  public boolean useAndroidXDependencies() {
    return myUseAndroidXDependencies.get();
  }

  public boolean needsLibraryLoad(@NotNull Palette.Item item) {
    return myMissingLibraries.get().contains(item.getGradleCoordinateId());
  }

  @TestOnly
  public void setSyncTopicListener(@NotNull Consumer<Future<?>> listener) {
    mySyncTopicConsumer = listener;
  }

  @TestOnly
  public void setNotifyAlways(boolean notifyAlways) {
    myNotifyAlways = notifyAlways;
  }

  @Override
  public void dispose() {
  }

  private boolean checkForRelevantDependencyChanges() {
    synchronized (mySync) {
      return checkForNewMissingDependencies() | checkForNewAndroidXDependencies();
    }
  }

  private boolean checkForNewAndroidXDependencies() {
    boolean useAndroidX = computeUseAndroidXDependencies();
    if (useAndroidX == myUseAndroidXDependencies.get()) {
      return false;
    }
    myUseAndroidXDependencies.set(useAndroidX);
    return true;
  }

  /**
   * This method will update {@link #myMissingLibraries} to the current set.
   *
   * The current set of dependencies referenced from {@link #myPalette} that are not
   * part of the dependencies in the current {@link AndroidModuleSystem}.
   *
   * The method is called from a background thread and is safe even if there are more
   * than 1 background thread running at a given time since we synchronize on the
   * calling method: checkForRelevantDependencyChanges.
   *
   * @return true if the set of missing libraries has changed. This indicates that users of this service should be notified.
   */
  // This method is only called from checkForRelevantDependencyChanges where the mySync monitor is already held.
  @SuppressWarnings("FieldAccessNotGuarded")
  private boolean checkForNewMissingDependencies() {
    Set<String> missing = Collections.emptySet();

    if (myModule != null && !myModule.isDisposed() && !myProject.isDisposed()) {
      AndroidModuleSystem moduleSystem = ProjectSystemService.getInstance(myProject).getProjectSystem().getModuleSystem(myModule);
      missing = myPalette.getGradleCoordinateIds().stream()
        .map(id -> GradleCoordinate.parseCoordinateString(id + ":+"))
        .filter(Objects::nonNull)
        .filter(coordinate -> moduleSystem.getRegisteredDependency(coordinate) == null)
        .map(coordinate -> coordinate.getId())
        .filter(Objects::nonNull)
        .collect(ImmutableSet.toImmutableSet());

      if (myMissingLibraries.get().equals(missing)) {
        return false;
      }
    }

    myMissingLibraries.set(missing);
    return true;
  }

  /**
   * Register for project sync updates.
   *
   * The dependencies of a module for Android Studio is dictated from the {@link ProjectSystemService},
   * which will update the IJ libraries based on the build specification for the service.
   * All successful syncs means there is potentially a new set of dependencies.
   * There will be no successful dependency changes without a successful project sync.
   */
  private void registerDependencyUpdates() {
    if (myRegisteredForDependencyUpdates || myProject.isDisposed()) {
      return;
    }
    myRegisteredForDependencyUpdates = true;
    myProject.getMessageBus().connect(this).subscribe(PROJECT_SYSTEM_SYNC_TOPIC, result -> {
      // checkForRelevantDependencyChanges can be quite expensive, run this on a background thread,
      // however the DependencyChangeListeners must be called from the UI thread as they update the UI.
      mySyncTopicConsumer.accept(ensureRunningOnBackgroundThread(() -> {
        if ((result == ProjectSystemSyncManager.SyncResult.SUCCESS && checkForRelevantDependencyChanges()) || myNotifyAlways) {
          ApplicationManager.getApplication().invokeLater(() -> myListeners.forEach(listener -> listener.onDependenciesChanged()));
        }
      }));
    });
  }

  public void ensureLibraryIsIncluded(@NotNull Palette.Item item) {
    if (needsLibraryLoad(item)) {
      GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(item.getGradleCoordinateId() + ":+");

      if (coordinate != null) {
        synchronized (mySync) {
          DependencyManagementUtil.addDependenciesWithUiConfirmation(myModule, Collections.singletonList(coordinate), true);
        }
      }
    }
  }

  // This method is only called indirectly from checkForRelevantDependencyChanges where the mySync monitor is already held.
  @SuppressWarnings("FieldAccessNotGuarded")
  private boolean computeUseAndroidXDependencies() {
    if (myProject.isDisposed()) {
      return false;
    }

    Boolean useAndroidX = MigrateToAndroidxUtil.useAndroidX(myProject);
    if (useAndroidX != null) {
      return useAndroidX;
    }

    if (myModule == null || myModule.isDisposed()) {
      return false;
    }

    if (DependencyManagementUtil.dependsOnAndroidx(myModule)) {
      // It already depends on androidx
      return true;
    }

    // It does not depend on androidx. Check default to using androidx unless the module already depends on android.support
    return !DependencyManagementUtil.dependsOnOldSupportLib(myModule);
  }

  public interface DependencyChangeListener {
    void onDependenciesChanged();
  }

  private Future<?> ensureRunningOnBackgroundThread(@NotNull Runnable runnable) {
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      // The runnable cannot safely be run after the project is disposed.
      // In order to avoid that we will cancel the task when the DependencyManager
      // is disposed.
      CompletableFuture<?> future = CompletableFuture.runAsync(runnable, PooledThreadExecutor.INSTANCE);
      Disposable taskCanceller = () -> future.cancel(true);
      Disposer.register(this, taskCanceller);
      return future.whenComplete((r, e) -> Disposer.dispose(taskCanceller));
    }
    else {
      runnable.run();
      return Futures.immediateFuture(null);
    }
  }
}
