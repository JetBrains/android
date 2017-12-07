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
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.inspector.InspectorPanel;
import com.android.tools.idea.uibuilder.property.NlDesignProperties;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;

public class NlInspectorPanel extends InspectorPanel<NlPropertiesManager> {
  private final NlDesignProperties myDesignProperties;

  public NlInspectorPanel(@NotNull Disposable parentDisposable,
                          @Nullable JComponent bottomLink) {
    super(parentDisposable, bottomLink);
    myDesignProperties = new NlDesignProperties();
  }

  @Override
  protected void collectExtraProperties(@NotNull List<NlComponent> components,
                                        @NotNull NlPropertiesManager propertiesManager,
                                        Map<String, NlProperty> propertiesByName) {
    // Add access to known design properties
    for (NlProperty property : myDesignProperties.getKnownProperties(components, propertiesManager)) {
      propertiesByName.putIfAbsent(property.getName(), property);
    }
  }

}
