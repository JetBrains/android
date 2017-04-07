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
package com.android.tools.idea.uibuilder.property.inspector;

import com.android.tools.idea.uibuilder.model.NlComponent;
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

public class NlInspectorProviders implements LafManagerListener, Disposable {
  private final NlPropertiesManager myPropertiesManager;
  private final IdInspectorProvider myIdInspectorProvider;
  private final List<InspectorProvider> myProviders;

  public NlInspectorProviders(@NotNull NlPropertiesManager propertiesManager, @NotNull Disposable parentDisposable) {
    myPropertiesManager = propertiesManager;
    myIdInspectorProvider = new IdInspectorProvider();
    myProviders = ImmutableList.of(myIdInspectorProvider,
                                   new ViewInspectorProvider(myPropertiesManager.getProject()),
                                   new ProgressBarInspectorProvider(),
                                   new TextInspectorProvider(),
                                   new MockupInspectorProvider(),
                                   new FavoritesInspectorProvider());
    Disposer.register(parentDisposable, this);
    LafManager.getInstance().addLafManagerListener(this);
  }

  @NotNull
  public List<InspectorComponent> createInspectorComponents(@NotNull List<NlComponent> components,
                                                            @NotNull Map<String, NlProperty> properties,
                                                            @NotNull NlPropertiesManager propertiesManager) {
    List<InspectorComponent> inspectors = new ArrayList<>(myProviders.size());

    if (components.isEmpty()) {
      // create just the id inspector, which we know can handle a null component
      // this is simply to avoid the screen flickering when switching components
      return ImmutableList.of(myIdInspectorProvider.createCustomInspector(components, properties, propertiesManager));
    }

    for (InspectorProvider provider : myProviders) {
      if (provider.isApplicable(components, properties, propertiesManager)) {
        inspectors.add(provider.createCustomInspector(components, properties, propertiesManager));
      }
    }

    return inspectors;
  }

  @Override
  public void lookAndFeelChanged(LafManager source) {
    // Clear all caches with UI elements:
    myProviders.forEach(InspectorProvider::resetCache);

    // Force a recreate of all UI elements by causing a new selection notification:
    myPropertiesManager.updateSelection();
  }

  @Override
  public void dispose() {
    LafManager.getInstance().removeLafManagerListener(this);
  }
}
