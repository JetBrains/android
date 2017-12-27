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

import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintSetInspectorProvider;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NlInspectorProviders extends InspectorProviders {
  protected final List<InspectorProvider> myProviders;
  protected final InspectorProvider myNullProvider;

  public NlInspectorProviders(@NotNull NlPropertiesManager propertiesManager, @NotNull Disposable parentDisposable) {
    super(propertiesManager, parentDisposable);
    myNullProvider = new IdInspectorProvider();
    Project project = myPropertiesManager.getProject();
    myProviders = ImmutableList.of(myNullProvider,
                                   new ViewInspectorProvider(),
                                   new ConstraintSetInspectorProvider(),
                                   new ProgressBarInspectorProvider(),
                                   new TextInspectorProvider(),
                                   new MockupInspectorProvider(),
                                   new FavoritesInspectorProvider(),
                                   new LayoutInspectorProvider(project));
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
