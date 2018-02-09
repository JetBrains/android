/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.PropertiesManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.naveditor.property.editors.NavPropertyEditors;
import com.android.tools.idea.naveditor.property.inspector.NavInspectorProviders;
import com.intellij.openapi.Disposable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NavPropertiesManager extends PropertiesManager<NavPropertiesManager> {
  @VisibleForTesting NavInspectorProviders myProviders;

  public NavPropertiesManager(@NotNull AndroidFacet facet, @Nullable DesignSurface designSurface) {
    super(facet, designSurface, NavPropertyEditors.Factory.getInstance(facet.getModule().getProject()));
  }

  @NotNull
  @Override
  protected NavPropertiesPanel createPropertiesPanel() {
    return new NavPropertiesPanel(this);
  }

  @NotNull
  @Override
  protected NavPropertiesPanel getPropertiesPanel() {
    return (NavPropertiesPanel)super.getPropertiesPanel();
  }

  @Override
  public void logPropertyChange(@NotNull NlProperty property) {
    // TODO
  }

  @NotNull
  @Override
  public NavInspectorProviders getInspectorProviders(@NotNull Disposable parentDisposable) {
    if (myProviders == null) {
      myProviders = new NavInspectorProviders(this, parentDisposable);
    }
    return myProviders;
  }
}
