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
package com.android.tools.idea.uibuilder.property.inspector;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class InspectorProviders implements LafManagerListener, Disposable {
  protected final NlPropertiesManager myPropertiesManager;

  public InspectorProviders(NlPropertiesManager propertiesManager, Disposable parentDisposable) {
    myPropertiesManager = propertiesManager;
    Disposer.register(parentDisposable, this);
    LafManager.getInstance().addLafManagerListener(this);
  }

  @NotNull
  public List<InspectorComponent> createInspectorComponents(@NotNull List<NlComponent> components,
                                                            @NotNull Map<String, NlProperty> properties,
                                                            @NotNull NlPropertiesManager propertiesManager) {
    List<InspectorProvider> providers = getProviders();
    List<InspectorComponent> inspectors = new ArrayList<>(providers.size());

    if (components.isEmpty()) {
      return ImmutableList.of(getNullProvider().createCustomInspector(components, properties, propertiesManager));
    }

    for (InspectorProvider provider : providers) {
      if (provider.isApplicable(components, properties, propertiesManager)) {
        inspectors.add(provider.createCustomInspector(components, properties, propertiesManager));
      }
    }

    return inspectors;
  }

  @Override
  public void lookAndFeelChanged(LafManager source) {
    // Clear all caches with UI elements:
    getProviders().forEach(InspectorProvider::resetCache);

    // Force a recreate of all UI elements by causing a new selection notification:
    myPropertiesManager.updateSelection();
  }

  @Override
  public void dispose() {
    LafManager.getInstance().removeLafManagerListener(this);
  }

  protected abstract List<InspectorProvider> getProviders();

  /**
   * @return A provider known to be able to handle null components.
   */
  protected abstract InspectorProvider getNullProvider();
}
