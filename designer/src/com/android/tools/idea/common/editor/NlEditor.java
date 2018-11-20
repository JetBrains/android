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
package com.android.tools.idea.common.editor;

import com.android.tools.adtui.workbench.AutoHide;
import com.android.tools.adtui.workbench.Side;
import com.android.tools.adtui.workbench.Split;
import com.android.tools.adtui.workbench.ToolWindowDefinition;
import com.android.tools.adtui.workbench.WorkBench;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.mockup.editor.MockupToolDefinition;
import com.android.tools.idea.uibuilder.palette2.PaletteDefinition;
import com.android.tools.idea.uibuilder.property.NlPropertyPanelDefinition;
import com.android.tools.idea.uibuilder.property2.NelePropertiesPanelDefinition;
import com.android.tools.idea.uibuilder.structure.NlComponentTreeDefinition;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class NlEditor extends DesignerEditor {

  public static final String NL_EDITOR_ID = "android-designer2";

  private static final String WORKBENCH_NAME = "NELE_EDITOR";

  public NlEditor(@NotNull VirtualFile file, @NotNull Project project) {
    super(file, project);
  }

  @Override
  @NotNull
  public String getEditorId() {
    return NL_EDITOR_ID;
  }

  @NotNull
  @Override
  protected DesignerEditorPanel createEditorPanel() {
    WorkBench<DesignSurface> workBench = new WorkBench<>(myProject, WORKBENCH_NAME, this);
    NlDesignSurface surface = new NlDesignSurface(myProject, false, this);
    surface.setCentered(true);
    return new DesignerEditorPanel(this, myProject, myFile, workBench, surface, surface.getAccessoryPanel(), this::toolWindowDefinitions);
  }

  @NotNull
  private List<ToolWindowDefinition<DesignSurface>> toolWindowDefinitions(@NotNull AndroidFacet facet) {
    ImmutableList.Builder<ToolWindowDefinition<DesignSurface>> definitions = ImmutableList.builder();

    definitions.add(new PaletteDefinition(myProject, Side.LEFT, Split.TOP, AutoHide.DOCKED));
    if (StudioFlags.NELE_NEW_PROPERTY_PANEL.get()) {
      definitions.add(new NelePropertiesPanelDefinition(facet, Side.RIGHT, Split.TOP, AutoHide.DOCKED));
    }
    else {
      definitions.add(new NlPropertyPanelDefinition(facet, Side.RIGHT, Split.TOP, AutoHide.DOCKED));
    }
    definitions.add(new NlComponentTreeDefinition(myProject, Side.LEFT, Split.BOTTOM, AutoHide.DOCKED));
    if (StudioFlags.NELE_MOCKUP_EDITOR.get()) {
      definitions.add(new MockupToolDefinition(Side.RIGHT, Split.TOP, AutoHide.AUTO_HIDE));
    }

    return definitions.build();
  }

  @NotNull
  @Override
  public String getName() {
    return "Design";
  }
}
