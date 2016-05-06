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
package com.android.tools.idea.gradle.structure.configurables;

import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.structure.FastGradleSync;
import com.android.tools.idea.gradle.structure.daemon.PsAnalyzerDaemon;
import com.android.tools.idea.gradle.structure.daemon.PsLibraryUpdateCheckerDaemon;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

import static com.intellij.util.ExceptionUtil.getRootCause;

public class PsContext implements Disposable {
  @NotNull private final PsProject myProject;
  @NotNull private final PsAnalyzerDaemon myAnalyzerDaemon;
  @NotNull private final FastGradleSync myGradleSync;
  @NotNull private final PsLibraryUpdateCheckerDaemon myLibraryUpdateCheckerDaemon;

  @NotNull private final EventDispatcher<ChangeListener> myChangeEventDispatcher = EventDispatcher.create(ChangeListener.class);
  @NotNull private final EventDispatcher<GradleSyncListener> myGradleSyncEventDispatcher = EventDispatcher.create(GradleSyncListener.class);

  @Nullable private String mySelectedModuleName;

  public PsContext(@NotNull PsProject project, @NotNull Disposable parentDisposable) {
    myProject = project;
    myGradleSync = new FastGradleSync();

    getMainConfigurable().add(this::requestGradleSync, this);

    myLibraryUpdateCheckerDaemon = new PsLibraryUpdateCheckerDaemon(this);
    myLibraryUpdateCheckerDaemon.reset();
    myLibraryUpdateCheckerDaemon.queueAutomaticUpdateCheck();

    myAnalyzerDaemon = new PsAnalyzerDaemon(this, myLibraryUpdateCheckerDaemon);
    myAnalyzerDaemon.reset();
    myProject.forEachModule(myAnalyzerDaemon::queueCheck);

    Disposer.register(parentDisposable, this);
  }

  private void requestGradleSync() {
    Project project = myProject.getResolvedModel();
    myGradleSyncEventDispatcher.getMulticaster().syncStarted(project);
    FastGradleSync.Callback callback = myGradleSync.requestProjectSync(project);
    callback.doWhenDone(() -> myGradleSyncEventDispatcher.getMulticaster().syncSucceeded(project));
    callback.doWhenRejected(() -> {
      Throwable failure = callback.getFailure();
      assert failure != null;
      //noinspection ThrowableResultOfMethodCallIgnored
      myGradleSyncEventDispatcher.getMulticaster().syncFailed(project, getRootCause(failure).getMessage());
    });
  }

  public void add(@NotNull GradleSyncListener listener, @NotNull Disposable parentDisposable) {
    myGradleSyncEventDispatcher.addListener(listener, parentDisposable);
  }

  @NotNull
  public PsAnalyzerDaemon getAnalyzerDaemon() {
    return myAnalyzerDaemon;
  }

  @NotNull
  public PsLibraryUpdateCheckerDaemon getLibraryUpdateCheckerDaemon() {
    return myLibraryUpdateCheckerDaemon;
  }

  @Nullable
  public String getSelectedModule() {
    return mySelectedModuleName;
  }

  public void setSelectedModule(@NotNull String moduleName, @NotNull Object source) {
    mySelectedModuleName = moduleName;
    myChangeEventDispatcher.getMulticaster().moduleSelectionChanged(mySelectedModuleName, source);
  }

  public void add(@NotNull ChangeListener listener, @NotNull Disposable parentDisposable) {
    myChangeEventDispatcher.addListener(listener, parentDisposable);
  }

  @NotNull
  public PsProject getProject() {
    return myProject;
  }

  @Override
  public void dispose() {
  }

  @NotNull
  public ProjectStructureConfigurable getMainConfigurable() {
    return ProjectStructureConfigurable.getInstance(myProject.getResolvedModel());
  }

  public interface ChangeListener extends EventListener {
    void moduleSelectionChanged(@NotNull String moduleName, @NotNull Object source);
  }
}
