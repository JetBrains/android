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
package com.android.tools.idea.naveditor.property.inspector;

import com.android.tools.idea.uibuilder.property.inspector.InspectorProvider;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.inspector.InspectorProviders;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Creates the {@link InspectorProvider}s for navigation editor elements.
 */
public class NavInspectorProviders extends InspectorProviders {
  private final List<InspectorProvider> myProviders;
  private final InspectorProvider myNullProvider;

  public NavInspectorProviders(@NotNull NlPropertiesManager propertiesManager, @NotNull Disposable parentDisposable) {
    super(propertiesManager, parentDisposable);
    NavigationSchema schema = NavigationSchema.getOrCreateSchema(propertiesManager.getFacet());
    NavigationInspectorProvider provider = new NavigationInspectorProvider();
    myNullProvider = provider;
    myProviders = ImmutableList.of(provider, new ActionInspectorProvider());
  }

  @NotNull
  @Override
  protected List<InspectorProvider> getProviders() {
    return myProviders;
  }

  @NotNull
  @Override
  protected InspectorProvider getNullProvider() {
    return myNullProvider;
  }
}
