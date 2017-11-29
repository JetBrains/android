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

import com.android.tools.idea.common.property.inspector.InspectorProvider;
import com.android.tools.idea.common.property.inspector.InspectorProviders;
import com.android.tools.idea.naveditor.property.NavPropertiesManager;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Creates the {@link InspectorProvider}s for navigation editor elements.
 */
public class NavInspectorProviders extends InspectorProviders<NavPropertiesManager> {
  private final List<InspectorProvider<NavPropertiesManager>> myProviders;
  private final InspectorProvider<NavPropertiesManager> myNullProvider;

  public NavInspectorProviders(@NotNull NavPropertiesManager propertiesManager, @NotNull Disposable parentDisposable) {
    super(propertiesManager, parentDisposable);
    NavigationPropertiesInspectorProvider provider = new NavigationPropertiesInspectorProvider();
    myNullProvider = provider;
    myProviders = ImmutableList.of(provider,
                                   new NavSetStartProvider(),
                                   new NavDestinationArgumentsInspectorProvider(),
                                   new NavActionArgumentsInspectorProvider(),
                                   new NavigationActionsInspectorProvider(),
                                   new NavigationDeeplinkInspectorProvider());
  }

  @NotNull
  @Override
  protected List<InspectorProvider<NavPropertiesManager>> getProviders() {
    return myProviders;
  }

  @NotNull
  @Override
  protected InspectorProvider<NavPropertiesManager> getNullProvider() {
    return myNullProvider;
  }
}
