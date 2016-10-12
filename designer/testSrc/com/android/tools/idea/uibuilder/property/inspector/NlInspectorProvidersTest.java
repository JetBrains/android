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
import com.android.tools.idea.uibuilder.editor.NlEditorPanel;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.ui.docking.DockManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public class NlInspectorProvidersTest extends PropertyTestCase {
  private NlInspectorProviders myProviders;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProviders = NlInspectorProviders.getInstance(getProject());
  }

  public void testLookAndFeelChange() {
    ((ProjectImpl)getProject()).registerComponentImplementation(FileEditorManager.class, TestFileEditorManager.class);

    List<InspectorComponent> emptyInspectors = getInspectorsFor(ImmutableList.of());
    List<InspectorComponent> textInspectors = getInspectorsFor(myTextView);
    List<InspectorComponent> progressInspectors = getInspectorsFor(myProgressBar);
    List<InspectorComponent> buttonInspectors = getInspectorsFor(myButton);

    assertSameInstances(emptyInspectors, getInspectorsFor(ImmutableList.of()));
    assertSameInstances(textInspectors, getInspectorsFor(myTextView));
    assertSameInstances(progressInspectors, getInspectorsFor(myProgressBar));
    assertSameInstances(buttonInspectors, getInspectorsFor(myButton));

    myProviders.lookAndFeelChanged(LafManager.getInstance());

    assertDifferentInstances(emptyInspectors, getInspectorsFor(ImmutableList.of()));
    assertDifferentInstances(textInspectors, getInspectorsFor(myTextView));
    assertDifferentInstances(progressInspectors, getInspectorsFor(myProgressBar));
    assertDifferentInstances(buttonInspectors, getInspectorsFor(myButton));

    verify(((TestFileEditorManager)FileEditorManager.getInstance(getProject())).getSelectionModel()).updateListeners();
  }

  @NotNull
  private List<InspectorComponent> getInspectorsFor(@NotNull NlComponent component) {
    return getInspectorsFor(ImmutableList.of(component));
  }

  @NotNull
  private List<InspectorComponent> getInspectorsFor(@NotNull List<NlComponent> components) {
    Map<String, NlProperty> properties = getPropertyMap(components);
    return myProviders.createInspectorComponents(components, properties, myPropertiesManager);
  }

  private static void assertSameInstances(@NotNull List<InspectorComponent> expected,
                                          @NotNull List<InspectorComponent> actual) {
    assertThat(expected.size()).isEqualTo(actual.size());
    for (int index = 0; index < expected.size(); index++) {
      assertThat(expected.get(index)).isSameAs(actual.get(index));
    }
  }

  private static void assertDifferentInstances(@NotNull List<InspectorComponent> expected,
                                               @NotNull List<InspectorComponent> actual) {
    assertThat(expected.size()).isEqualTo(actual.size());
    for (int index = 0; index < expected.size(); index++) {
      assertThat(expected.get(index)).isNotSameAs(actual.get(index));
    }
  }

  private static class TestFileEditorManager extends FileEditorManagerImpl {
    private SelectionModel mySelectionModel;

    private TestFileEditorManager(@NotNull Project project, @NotNull DockManager dockManager) {
      super(project, dockManager);
      mySelectionModel = mock(SelectionModel.class);
    }

    @NotNull
    private SelectionModel getSelectionModel() {
      return mySelectionModel;
    }

    @NotNull
    @Override
    public FileEditor[] getAllEditors() {
      NlEditor editor = mock(NlEditor.class);
      NlEditorPanel panel = mock(NlEditorPanel.class);
      DesignSurface surface = mock(DesignSurface.class);
      ScreenView screenView = mock(ScreenView.class);
      NlModel model = mock(NlModel.class);

      when(editor.getComponent()).thenReturn(panel);
      when(panel.getSurface()).thenReturn(surface);
      when(surface.getCurrentScreenView()).thenReturn(screenView);
      when(screenView.getModel()).thenReturn(model);
      when(model.getSelectionModel()).thenReturn(mySelectionModel);

      return new FileEditor[]{editor};
    }
  }
}
