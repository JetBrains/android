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
package com.android.tools.idea.databinding.psiclass;

import com.android.tools.idea.databinding.DataBindingUtil;
import com.android.tools.idea.databinding.ModuleDataBinding;
import com.android.tools.idea.res.binding.BindingLayoutInfo;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for creating / finding data-binding related classes.
 */
public final class DataBindingClassFactory {
  @NotNull
  public static PsiClass getOrCreatePsiClass(@NotNull BindingLayoutInfo info) {
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

  /**
   * Package private class used by BR class finder and BR short names cache to create a BR file on demand.
   *
   * We may not be able to obtain enough information from the given facet to create a corresponding
   * LightBRClass at this time (i.e. because we couldn't determine the class's fully-qualified name).
   * In such cases, this method will return null.
   *
   * @param facet The facet for which the BR file is necessary.
   * @return The LightBRClass that belongs to the given AndroidFacet, or null if it wasn't cached and couldn't be created at this time.
   */
  @Nullable
  public static LightBrClass getOrCreateBrClassFor(@NotNull AndroidFacet facet) {
    ModuleDataBinding dataBinding = ModuleDataBinding.getInstance(facet);

    LightBrClass existing = dataBinding.getLightBrClass();
    if (existing == null) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (facet) {
        existing = dataBinding.getLightBrClass();
        if (existing == null) {
          String qualifiedName = DataBindingUtil.getBrQualifiedName(facet);
          if (qualifiedName == null) {
            return null;
          }
          existing = new LightBrClass(PsiManager.getInstance(facet.getModule().getProject()), facet, qualifiedName);
          dataBinding.setLightBrClass(existing);
        }
      }
    }
    return existing;
  }

  /**
   * Package private class used by binding component class finder to create a DataBindingComponent
   * light file on demand.
   *
   * @param facet The facet for which the binding component may be necessary.
   * @return The {@link LightDataBindingComponentClass} that belongs to the given module, or {@code null}
   * if not relevant for the current module (i.e. it's not an app module)
   */
  @Nullable
  public static LightDataBindingComponentClass getOrCreateDataBindingComponentClassFor(@NotNull AndroidFacet facet) {
    if (facet.getConfiguration().isLibraryProject()) {
      return null;
    }

    ModuleDataBinding dataBinding = ModuleDataBinding.getInstance(facet);

    LightDataBindingComponentClass lightClass = dataBinding.getLightDataBindingComponentClass();
    if (lightClass == null) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (facet) {
        lightClass = dataBinding.getLightDataBindingComponentClass();
        if (lightClass == null) {
          lightClass = new LightDataBindingComponentClass(PsiManager.getInstance(facet.getModule().getProject()), facet);
          dataBinding.setLightDataBindingComponentClass(lightClass);
        }
      }
    }
    return lightClass;
  }
}
