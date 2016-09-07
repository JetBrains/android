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

import com.android.tools.idea.uibuilder.editor.NlEditor;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlPropertyEditors;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NlInspectorProviders implements ProjectComponent, LafManagerListener {
  private final Project myProject;
  private final IdInspectorProvider myIdInspectorProvider;
  private final List<InspectorProvider> myProviders;

  @NotNull
  public static NlInspectorProviders getInstance(@NotNull Project project) {
    return project.getComponent(NlInspectorProviders.class);
  }

  private NlInspectorProviders(@NotNull Project project) {
    myProject = project;
    myIdInspectorProvider = new IdInspectorProvider();
    myProviders = ImmutableList.of(myIdInspectorProvider,
                                   new ViewInspectorProvider(project),
                                   new ProgressBarInspectorProvider(),
                                   new TextInspectorProvider(),
                                   new MockupInspectorProvider());
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

  @NotNull
  @Override
  public String getComponentName() {
    return NlInspectorProviders.class.getSimpleName();
  }

  @Override
  public void lookAndFeelChanged(LafManager source) {
    // Clear all caches with UI elements:
    myProviders.forEach(InspectorProvider::resetCache);

    // Make sure the property sheet editors have been notified before the selection notification below:
    NlPropertyEditors.getInstance(myProject).lookAndFeelChanged(source);

    // Force a recreate of all UI elements by causing a new selection notification:
    updateSelectionOnAllEditors();
  }

  private void updateSelectionOnAllEditors() {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    for (FileEditor fileEditor : fileEditorManager.getAllEditors()) {
      if (fileEditor instanceof NlEditor) {
        NlEditor editor = (NlEditor)fileEditor;
        ScreenView screenView = editor.getComponent().getSurface().getCurrentScreenView();
        if (screenView != null) {
          screenView.getModel().getSelectionModel().updateListeners();
        }
      }
    }
  }

  @Override
  public void initComponent() {
    LafManager.getInstance().addLafManagerListener(this);
  }

  @Override
  public void disposeComponent() {
    LafManager.getInstance().removeLafManagerListener(this);
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }
}
