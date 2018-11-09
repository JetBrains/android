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
import com.android.tools.idea.res.DataBindingInfo;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.FileContentUtil;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * Utility class that handles adding class finders and short names caches for DataBinding related code
 * completion etc.
 */
public class InternalDataBindingUtil {
  private static AtomicLong ourDataBindingEnabledModificationCount = new AtomicLong(0);

  private static AtomicBoolean ourCreateInMemoryClasses = new AtomicBoolean(false);

  private static AtomicBoolean ourReadInMemoryClassGenerationSettings = new AtomicBoolean(false);

  private static Logger getLog() {
    return Logger.getInstance(InternalDataBindingUtil.class);
  }

  private static void invalidateJavaCodeOnOpenDataBindingProjects() {
    ourDataBindingEnabledModificationCount.incrementAndGet();
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      DataBindingProjectComponent component = project.getComponent(DataBindingProjectComponent.class);
      if (component == null) {
        continue;
      }
      boolean invalidated = invalidateAllSources(component);
      if (!invalidated) {
        return;
      }
      PsiModificationTracker tracker = PsiManager.getInstance(project).getModificationTracker();
      if (tracker instanceof PsiModificationTrackerImpl) {
        ((PsiModificationTrackerImpl) tracker).incCounter();
      }
      FileContentUtil.reparseFiles(project, Collections.emptyList(), true);

    }
    ourDataBindingEnabledModificationCount.incrementAndGet();
  }

  public static boolean invalidateAllSources(DataBindingProjectComponent component) {
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

  public static boolean inMemoryClassGenerationIsEnabled() {
    if (!ourReadInMemoryClassGenerationSettings.getAndSet(true)) {
      // just calculate, don't notify for the first one since we don't have anything to invalidate
      ourCreateInMemoryClasses.set(calculateEnableInMemoryClasses());
    }
    return ourCreateInMemoryClasses.get();
  }

  public static void recalculateEnableInMemoryClassGeneration() {
    boolean newValue = calculateEnableInMemoryClasses();
    boolean oldValue = ourCreateInMemoryClasses.getAndSet(newValue);
    if (newValue != oldValue) {
      getLog().debug("Data binding in memory completion value change. (old, new)", oldValue, newValue);
      ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(InternalDataBindingUtil::invalidateJavaCodeOnOpenDataBindingProjects));
    }
  }

  private static boolean calculateEnableInMemoryClasses() {
    DataBindingConfiguration config = DataBindingConfiguration.getInstance();
    return config.CODE_NAVIGATION_MODE == DataBindingConfiguration.CodeNavigationMode.XML;
  }

  /**
   * Package private class used by BR class finder and BR short names cache to create a BR file on demand.
   *
   * @param facet The facet for which the BR file is necessary.
   * @return The LightBRClass that belongs to the given AndroidFacet
   */
  static LightBrClass getOrCreateBrClassFor(AndroidFacet facet) {
    ModuleDataBinding dataBinding = ModuleDataBinding.getInstance(facet);

    LightBrClass existing = dataBinding.getLightBrClass();
    if (existing == null) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (facet) {
        existing = dataBinding.getLightBrClass();
        if (existing == null) {
          existing = new LightBrClass(PsiManager.getInstance(facet.getModule().getProject()), facet);
          dataBinding.setLightBrClass(existing);
        }
      }
    }
    return existing;
  }

  public static PsiClass getOrCreatePsiClass(DataBindingInfo info) {
    PsiClass psiClass = info.getPsiClass();
    if (psiClass == null) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (info) {
        psiClass = info.getPsiClass();
        if (psiClass == null) {
          psiClass = new LightBindingClass(info.getFacet(), PsiManager.getInstance(info.getProject()), info);
          info.setPsiClass(psiClass);
        }
      }
    }
    return psiClass;
  }

  public static void incrementModificationCount() {
    ourDataBindingEnabledModificationCount.incrementAndGet();
  }

  /**
   * Tracker that changes when a facet's data binding enabled value changes
   */
  public static ModificationTracker DATA_BINDING_ENABLED_TRACKER = () -> ourDataBindingEnabledModificationCount.longValue();
}
