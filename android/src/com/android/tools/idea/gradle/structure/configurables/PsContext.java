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

import com.android.tools.idea.gradle.structure.daemon.PsAnalyzerDaemon;
import com.android.tools.idea.gradle.structure.daemon.PsLibraryUpdateCheckerDaemon;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

public class PsContext implements Disposable {
  @NotNull private final PsProject myProject;
  @NotNull private final PsAnalyzerDaemon myAnalyzerDaemon;
  @NotNull private final PsLibraryUpdateCheckerDaemon myLibraryUpdateCheckerDaemon;

  @NotNull private final EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);

  @Nullable private String mySelectedModuleName;

  public PsContext(@NotNull PsProject project, @NotNull Disposable parentDisposable) {
    myProject = project;

    myLibraryUpdateCheckerDaemon = new PsLibraryUpdateCheckerDaemon(this);
    myLibraryUpdateCheckerDaemon.reset();
    myLibraryUpdateCheckerDaemon.queueUpdateCheck();

    myAnalyzerDaemon = new PsAnalyzerDaemon(this, myLibraryUpdateCheckerDaemon);
    myAnalyzerDaemon.reset();
    myProject.forEachModule(myAnalyzerDaemon::queueCheck);

    Disposer.register(parentDisposable, this);
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
    myEventDispatcher.getMulticaster().moduleSelectionChanged(mySelectedModuleName, source);
  }

  public void addListener(@NotNull ChangeListener listener, @NotNull Disposable parentDisposable) {
    myEventDispatcher.addListener(listener, parentDisposable);
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
