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

import com.android.tools.idea.databinding.psiclass.DataBindingClassFactory;
import com.android.tools.idea.databinding.psiclass.LightDataBindingComponentClass;
import com.android.tools.idea.databinding.psiclass.LightBrClass;
import com.android.tools.idea.model.AndroidModel;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.util.messages.MessageBusConnection;
import net.jcip.annotations.NotThreadSafe;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@NotThreadSafe
public class ModuleDataBinding {
  @Nullable private LightBrClass myLightBrClass;
  @Nullable private LightDataBindingComponentClass myLightDataBindingComponentClass;

  @NotNull
  private DataBindingMode myDataBindingMode = DataBindingMode.NONE;
  private Module myModule;

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
          syncWithConfiguration();
        }
      }
    });
    syncWithConfiguration();
  }

  private void syncWithConfiguration() {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    if (facet != null) {
      AndroidModel androidModel = facet.getConfiguration().getModel();
      if (androidModel != null) {
        setMode(androidModel.getDataBindingMode());
      }
    }
  }

  public void setMode(@NotNull DataBindingMode mode) {
    if (myDataBindingMode != mode) {
      myDataBindingMode = mode;
      DataBindingModeTrackingService.getInstance().incrementModificationCount();
    }
  }

  @NotNull
  public DataBindingMode getDataBindingMode() {
    return myDataBindingMode;
  }

  /**
   * Convenience method to check if data binding is enabled for the project (covers but support and androidX namespaces).
   * @return
   */
  public boolean isEnabled() {
    return myDataBindingMode != DataBindingMode.NONE;
  }

  /**
   * Each data binding module has exactly one BR class generated for it.
   *
   * An external caller should ensure that the current module gets associated with an in-memory
   * light version of the BR class.
   *
   * See also: <a href="https://developer.android.com/topic/libraries/data-binding/observability#observable_objects">official docs</a>
   *
   * @see DataBindingClassFactory#getOrCreateBrClassFor(AndroidFacet)
   */
  public void setLightBrClass(@NotNull LightBrClass lightBrClass) {
    myLightBrClass = lightBrClass;
  }

  /**
   * Returns the light BR class for this facet if it is already set.
   *
   * @return The BR class for this facet, if exists
   * @see DataBindingClassFactory#getOrCreateBrClassFor(AndroidFacet)
   */
  @Nullable
  public LightBrClass getLightBrClass() {
    return myLightBrClass;
  }

  /**
   * Each data binding <i>app</i> module has exactly one DataBindingComponent class generated for
   * it.
   *
   * An external caller should ensure that the current module, if it's an application module, gets
   * associated with an in-memory light version of a DataBindingComponent.
   *
   * See also: <a href="https://developer.android.com/reference/android/databinding/DataBindingComponent">official docs</a>
   */
  public void setLightDataBindingComponentClass(@NotNull LightDataBindingComponentClass lightBindingComponentClass) {
    myLightDataBindingComponentClass = lightBindingComponentClass;
  }

  /**
   * Returns the light DataBindingComponent class for this module if it is already set.
   *
   * @see DataBindingClassFactory#getOrCreateDataBindingComponentClassFor(AndroidFacet)
   */
  @Nullable
  public LightDataBindingComponentClass getLightDataBindingComponentClass() {
    return myLightDataBindingComponentClass;
  }

}
