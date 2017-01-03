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
package com.android.tools.idea.editors.hierarchyview;

import com.android.tools.adtui.workbench.*;
import com.android.tools.idea.editors.hierarchyview.ui.LayoutInspectorPanel;
import com.android.tools.idea.editors.hierarchyview.ui.LayoutTreeDefinition;
import com.android.tools.idea.editors.hierarchyview.ui.PropertiesDefinition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class HierarchyViewEditorPanel extends WorkBench<LayoutInspectorContext> {
  public HierarchyViewEditorPanel(@NotNull HierarchyViewEditor editor, @NotNull Project project, @NotNull LayoutInspectorContext context) {
    super(project, "Hierarchy View Editor", editor);
    Disposer.register(editor, this);

    List<ToolWindowDefinition<LayoutInspectorContext>> tools = new ArrayList<>(2);
    tools.add(new LayoutTreeDefinition(Side.LEFT, Split.TOP, AutoHide.DOCKED));
    tools.add(new PropertiesDefinition(Side.RIGHT, Split.TOP, AutoHide.DOCKED));

    init(new LayoutInspectorPanel(context), context, tools);
  }
}