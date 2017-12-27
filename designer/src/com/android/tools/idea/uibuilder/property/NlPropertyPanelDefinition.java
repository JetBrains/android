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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.adtui.workbench.AutoHide;
import com.android.tools.adtui.workbench.Side;
import com.android.tools.adtui.workbench.Split;
import com.android.tools.adtui.workbench.ToolWindowDefinition;
import com.android.tools.idea.common.surface.DesignSurface;
import com.intellij.icons.AllIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class NlPropertyPanelDefinition extends ToolWindowDefinition<DesignSurface> {

  public NlPropertyPanelDefinition(@NotNull AndroidFacet facet, @NotNull Side side, @NotNull Split split, @NotNull AutoHide autoHide) {
    // TODO: Get a different 13x13 tool window icon
    super("Attributes", AllIcons.Toolwindows.ToolWindowStructure, "PROPERTIES", side, split, autoHide,
          () -> new NlPropertiesManager(facet, null));
  }
}
