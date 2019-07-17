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
import com.android.tools.idea.res.binding.BindingLayoutGroup;
import com.android.tools.idea.res.binding.BindingLayoutInfo;
import com.android.tools.idea.res.binding.BindingLayoutPsi;
import com.intellij.psi.PsiManager;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for creating / finding data-binding related classes.
 */
public final class DataBindingClassFactory {
  /**
   * Returns a list of {@link LightBindingClass} instances corresponding to the layout XML files
   * related to the passed-in {@link BindingLayoutGroup}.
   *
   * If there is only one layout.xml (i.e. single configuration), this will return a single light
   * class (representing "(Layout)Binding"). If there are multiple layout.xmls (i.e. multi-
   * configuration), this will return a main light class ("Binding") as well as several
   * additional implementation light classes ("BindingImpl"s), one for each layout.
   *
   * This method abstracts away the concern of caching these light classes - subsequent calls will
   * fetch previously created instances.
   */
  @NotNull
  public static List<LightBindingClass> getOrCreateBindingClassesFor(@NotNull BindingLayoutGroup group) {
    BindingLayoutPsi layoutPsi = group.getMainLayout().getPsi();
    AndroidFacet facet = layoutPsi.getFacet();
    ModuleDataBinding dataBinding = ModuleDataBinding.getInstance(facet);

    List<LightBindingClass> bindingClasses = dataBinding.getLightBindingClasses(group);
    if (bindingClasses == null) {
      // All static factory methods need to lock against something consistent, so they use the
      // current facet.
      // TODO(b/138671228): Move this lock to a better, non-static location
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (facet) {
        bindingClasses = dataBinding.getLightBindingClasses(group);

        if (bindingClasses == null) {
          bindingClasses = new ArrayList<>();
          // Always add a full "Binding" class
          PsiManager psiManager = PsiManager.getInstance(layoutPsi.getProject());
          LightBindingClass bindingClass = new LightBindingClass(psiManager, new BindingClassConfig(group));
          bindingClasses.add(bindingClass);

          // "Impl" classes are only necessary if we have more than a single configuration
          if (group.getLayouts().size() > 1) {
            for (int layoutIndex = 0; layoutIndex < group.getLayouts().size(); layoutIndex++) {
              BindingLayoutInfo layout = group.getLayouts().get(layoutIndex);
              LightBindingClass bindingImplClass = new LightBindingClass(psiManager, new BindingImplClassConfig(group, layoutIndex));
              layout.getPsi().setPsiClass(bindingImplClass);
              bindingClasses.add(bindingImplClass);
            }
          }
          else {
            group.getMainLayout().getPsi().setPsiClass(bindingClass);
          }

          dataBinding.setLightBindingClasses(group, bindingClasses);
        }
      }
    }
    return bindingClasses;
  }

  /**
   * Method used by BR class finder and BR short names cache to create a BR file on demand.
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
      // All static factory methods need to lock against something consistent, so they use the
      // current facet.
      // TODO(b/138671228): Move this lock to a better, non-static location
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
   * Method used by binding component class finder to create a DataBindingComponent light file on demand.
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
      // All static factory methods need to lock against something consistent, so they use the
      // current facet.
      // TODO(b/138671228): Move this lock to a better, non-static location
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
