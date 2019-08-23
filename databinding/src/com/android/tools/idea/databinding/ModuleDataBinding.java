/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.databinding.psiclass.BindingClassConfig;
import com.android.tools.idea.databinding.psiclass.BindingImplClassConfig;
import com.android.tools.idea.databinding.psiclass.LightBindingClass;
import com.android.tools.idea.databinding.psiclass.LightBrClass;
import com.android.tools.idea.databinding.psiclass.LightDataBindingComponentClass;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.res.binding.BindingLayoutGroup;
import com.android.tools.idea.res.binding.BindingLayoutInfo;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBusConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ThreadSafe
public final class ModuleDataBinding {
  private final Object myLock = new Object();

  /**
   * A (weak) cache of all generated light binding classes.
   *
   * A binding module should generate one or more binding classes per bindable layout, and we cache
   * everything here. Such a bindable layout should have a {@link BindingLayoutGroup} associated
   * with it, with the assumption that its own lifetime is tied to the lifetime of the layout
   * itself. For example, if the user deletes a layout file, its binding group should also be
   * released, so in turn these cached binding classes would be collected as well.
   *
   * See also: {@link #getLightBindingClasses(BindingLayoutGroup)}
   */
  @GuardedBy("myLock")
  private WeakHashMap<BindingLayoutGroup, List<LightBindingClass>> myLightBindingClasses = new WeakHashMap<>();

  /**
   * The singleton light BR class associated with this module.
   *
   * See also: {@link #getLightBrClass()}
   */
  @GuardedBy("myLock")
  @Nullable private LightBrClass myLightBrClass;

  /**
   * The singleton light DataBindingComponent associated with this module.
   *
   * See also: {@link #getLightDataBindingComponentClass()}
   */
  @GuardedBy("myLock")
  @Nullable private LightDataBindingComponentClass myLightDataBindingComponentClass;

  @NotNull
  @GuardedBy("myLock")
  private DataBindingMode myDataBindingMode = DataBindingMode.NONE;

  private final Module myModule;

  @NotNull
  public static ModuleDataBinding getInstance(@NotNull AndroidFacet facet) {
    ModuleDataBinding dataBinding = ModuleServiceManager.getService(facet.getModule(), ModuleDataBinding.class);
    assert dataBinding != null; // service registered in android plugin
    return dataBinding;
  }

  private ModuleDataBinding(Module module) {
    myModule = module;
    final MessageBusConnection connection = module.getMessageBus().connect(module);

    connection.subscribe(FacetManager.FACETS_TOPIC, new FacetManagerAdapter() {
      @Override
      public void facetConfigurationChanged(@NotNull Facet facet) {
        if (facet.getModule() == myModule) {
          syncModeWithFacetConfiguration();
        }
      }
    });
    syncModeWithFacetConfiguration();
  }

  private void syncModeWithFacetConfiguration() {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    if (facet != null) {
      AndroidModel androidModel = facet.getConfiguration().getModel();
      if (androidModel != null) {
        setMode(androidModel.getDataBindingMode());
      }
    }
  }

  public void setMode(@NotNull DataBindingMode mode) {
    synchronized (myLock) {
      if (myDataBindingMode != mode) {
        myDataBindingMode = mode;
        DataBindingModeTrackingService.getInstance().incrementModificationCount();
      }
    }
  }

  @NotNull
  public DataBindingMode getDataBindingMode() {
    synchronized (myLock) {
      return myDataBindingMode;
    }
  }

  /**
   * Returns a list of {@link LightBindingClass} instances corresponding to the layout XML files
   * related to the passed-in {@link BindingLayoutGroup}.
   *
   * If there is only one layout.xml (i.e. single configuration), this will return a single light
   * class (a "Binding"). If there are multiple layout.xmls (i.e. multi- configuration), this will
   * return a main light class ("Binding") as well as several additional implementation light
   * classes ("BindingImpl"s), one for each layout.
   *
   * If this is the first time requesting this information, they will be created on the fly.
   *
   * This information is backed by a weak map, so when the {@code group} object goes out of scope,
   * the associated light binding classes will eventually get released.
   */
  @NotNull
  public List<LightBindingClass> getLightBindingClasses(@NotNull BindingLayoutGroup group) {
    synchronized (myLock) {
      List<LightBindingClass> bindingClasses = myLightBindingClasses.get(group);
      if (bindingClasses == null) {
        bindingClasses = new ArrayList<>();

        // Always add a full "Binding" class.
        PsiManager psiManager = PsiManager.getInstance(myModule.getProject());
        LightBindingClass bindingClass = new LightBindingClass(psiManager, new BindingClassConfig(group));
        bindingClasses.add(bindingClass);

        // "Impl" classes are only necessary if we have more than a single configuration.
        if (group.getLayouts().size() > 1) {
          for (int layoutIndex = 0; layoutIndex < group.getLayouts().size(); layoutIndex++) {
            BindingLayoutInfo layout = group.getLayouts().get(layoutIndex);
            LightBindingClass bindingImplClass = new LightBindingClass(psiManager, new BindingImplClassConfig(group, layoutIndex));
            layout.setPsiClass(bindingImplClass);
            bindingClasses.add(bindingImplClass);
          }
        }
        else {
          group.getMainLayout().setPsiClass(bindingClass);
        }

        myLightBindingClasses.put(group, bindingClasses);
      }
      return bindingClasses;
    }
  }

  /**
   * Fetches the singleton light BR class associated with this module.
   *
   * If this is the first time requesting this information, it will be created on the fly.
   *
   * This can return {@code null} if the current module is not associated with an
   * {@link AndroidFacet} OR if we were not able to obtain enough information from the given facet
   * at this time (e.g. because we couldn't determine the class's fully-qualified name).
   */
  @Nullable
  public LightBrClass getLightBrClass() {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    if (facet == null) {
      return null;
    }

    synchronized (myLock) {
      if (myLightBrClass == null) {
        String qualifiedName = DataBindingUtil.getBrQualifiedName(facet);
        if (qualifiedName == null) {
          return null;
        }
        myLightBrClass = new LightBrClass(PsiManager.getInstance(facet.getModule().getProject()), facet, qualifiedName);
      }
      return myLightBrClass;
    }
  }

  /**
   * Fetches the singleton light DataBindingComponent class associated with this module.
   *
   * If this is the first time requesting this information, it will be created on the fly.
   *
   * This can return {@code null} if the current module is not associated with an
   * {@link AndroidFacet} OR if the current module doesn't provide one (e.g. it's not an app
   * module).
   */
  @Nullable
  public LightDataBindingComponentClass getLightDataBindingComponentClass() {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    if (facet == null) {
      return null;
    }
    if (facet.getConfiguration().isLibraryProject()) {
      return null;
    }

    synchronized (myLock) {
      if (myLightDataBindingComponentClass == null) {
        myLightDataBindingComponentClass = new LightDataBindingComponentClass(PsiManager.getInstance(myModule.getProject()), facet);
      }
      return myLightDataBindingComponentClass;
    }
  }
}
