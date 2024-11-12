/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.idea.blaze.android.projectsystem;

import static com.android.tools.idea.util.DependencyManagementUtil.addDependenciesWithUiConfirmation;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.naveditor.surface.NavDesignSurfaceToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

/** Blaze implementation of project system tokens for the navigation editor: Nav Design Surface */
public class BazelNavDesignSurfaceToken implements NavDesignSurfaceToken<BazelProjectSystem>, BazelToken {
  @Override
  public boolean modifyProject(@NotNull BazelProjectSystem projectSystem, @NotNull NlModel model) {
    AtomicBoolean didAdd = new AtomicBoolean(false);
    Module module = model.getModule();
    List<GradleCoordinate> coordinates =
        NavDesignSurface.getDependencies(module).stream()
            .map((a) -> a.getCoordinate("+"))
            .collect(toImmutableList());
    Runnable runnable =
        () -> {
          try {
            didAdd.set(
                addDependenciesWithUiConfirmation(module, coordinates, true, false).isEmpty());
          } catch (Throwable t) {
            Logger.getInstance(NavDesignSurface.class).warn("Failed to add dependencies", t);
            didAdd.set(false);
          }
        };
    ApplicationManager.getApplication().invokeAndWait(runnable);
    return didAdd.get();
  }
}
