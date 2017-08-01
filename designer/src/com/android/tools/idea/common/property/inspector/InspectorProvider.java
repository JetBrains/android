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
package com.android.tools.idea.common.property.inspector;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.PropertiesManager;
import com.android.tools.idea.common.property.NlProperty;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public interface InspectorProvider<PropMgr extends PropertiesManager<PropMgr>> {
  /**
   * Returns true if this {@code InspectorProvider} should be used for the given
   * components and properties.
   */
  boolean isApplicable(@NotNull List<NlComponent> components,
                       @NotNull Map<String, NlProperty> properties,
                       @NotNull PropMgr propertiesManager);

  /**
   * Return an {@link InspectorComponent} for editing a subset of properties for
   * the given components.<br/>
   * The provider may choose to cache a {@link InspectorComponent} with editors
   * for a given set of component types and properties.
   */
  @NotNull
  InspectorComponent<PropMgr> createCustomInspector(@NotNull List<NlComponent> components,
                                                    @NotNull Map<String, NlProperty> properties,
                                                    @NotNull PropMgr propertiesManager);


  /**
   * Get rid of cache that a provider may maintain.
   */
  void resetCache();
}
