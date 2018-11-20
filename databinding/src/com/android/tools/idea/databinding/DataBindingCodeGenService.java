/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.databinding;


import com.android.tools.idea.databinding.config.DataBindingConfiguration;
import com.android.tools.idea.databinding.config.DataBindingConfiguration.CodeGenMode;
import com.android.tools.idea.res.DataBindingInfo;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.FileContentUtil;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Helper service that is aware of the global data binding code generation setting and invalidates
 * all relevant caches when the setting changes.
 */
public final class DataBindingCodeGenService {
  public static DataBindingCodeGenService getInstance() {
    return ServiceManager.getService(DataBindingCodeGenService.class);
  }

  private static Logger getLog() {
    return Logger.getInstance(DataBindingCodeGenService.class);
  }

  /**
   * Note: null at first - lazily initialized in {@link #initializeCodeGenModeLazily}
   */
  private final AtomicReference<CodeGenMode> myCodeGenMode = new AtomicReference<>();

  private static boolean invalidateSourcesFor(@NotNull DataBindingProjectComponent component) {
    boolean invalidated = false;
    for (AndroidFacet facet : component.getDataBindingEnabledFacets()) {
      LocalResourceRepository moduleResources = ResourceRepositoryManager.getModuleResources(facet);
      Map<String, DataBindingInfo> dataBindingResourceFiles = moduleResources.getDataBindingResourceFiles();
      if (dataBindingResourceFiles == null) {
        continue;
      }
      for (DataBindingInfo info : dataBindingResourceFiles.values()) {
        PsiClass psiClass = info.getPsiClass();
        if (psiClass != null) {
          PsiFile containingFile = psiClass.getContainingFile();
          if (containingFile != null) {
            containingFile.subtreeChanged();
            invalidated = true;
          }
        }
      }
    }
    return invalidated;
  }

  private static void invalidateSourcesForDataBindingModules() {
    DataBindingModeTrackingService.getInstance().incrementModificationCount();
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      DataBindingProjectComponent component = project.getComponent(DataBindingProjectComponent.class);
      if (component == null) {
        continue;
      }
      boolean invalidated = invalidateSourcesFor(component);
      if (!invalidated) {
        return;
      }
      PsiModificationTracker tracker = PsiManager.getInstance(project).getModificationTracker();
      if (tracker instanceof PsiModificationTrackerImpl) {
        ((PsiModificationTrackerImpl) tracker).incCounter();
      }
      FileContentUtil.reparseFiles(project, Collections.emptyList(), true);
    }
    DataBindingModeTrackingService.getInstance().incrementModificationCount();
  }

  private void initializeCodeGenModeLazily() {
    // Only initializes myCodeGenMode if it's null
    myCodeGenMode.compareAndSet(null, DataBindingConfiguration.getInstance().CODE_GEN_MODE);
  }

  /**
   * Returns {@code true} if the target {@code component} has data binding enabled and set to
   * {@link CodeGenMode#IN_MEMORY}.
   *
   * This is a useful utility method for testing if databinding actions can be enabled/disabled.
   */
  public boolean isCodeGenSetToInMemoryFor(@NotNull DataBindingProjectComponent component) {
    initializeCodeGenModeLazily();
    return myCodeGenMode.get() == CodeGenMode.IN_MEMORY && component.hasAnyDataBindingEnabledFacet();
  }

  /**
   * Should be called when {@link DataBindingConfiguration#CODE_GEN_MODE} is changed, so any
   * followup work can be done to invalidate relevant caches, etc..
   */
  public void handleCodeGenModeChanged() {
    initializeCodeGenModeLazily();
    CodeGenMode newValue = DataBindingConfiguration.getInstance().CODE_GEN_MODE;
    CodeGenMode oldValue = myCodeGenMode.getAndSet(newValue);
    if (newValue != oldValue) {
      getLog().debug(String.format("Data binding codegen setting has changed: [%s] -> [%s]", oldValue, newValue));
      ApplicationManager.getApplication().invokeLater(
        () -> ApplicationManager.getApplication().runWriteAction(() -> invalidateSourcesForDataBindingModules()));
    }
  }
}
