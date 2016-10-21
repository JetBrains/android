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

import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public class LayoutInspectorProviderTest extends PropertyTestCase {

  private LayoutInspectorProvider myProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    // Use the fake version of ViewHandlerManager in this test so that we can test the LayoutInspectorProvider with
    // a view handler that extends extends ViewHandler#getLayoutInspectorProperties
    // because only the NlComponent for the classes that extends android.view.View are correctly handled by the
    // PropertyTestCase#getPropertyMap(components) method.
    // At this moment, FlexboxLayoutHandler is the only class that overrides
    // ViewHandler#getLayoutInspectorProperties but FlexboxLayout is not included as the default
    // Android SDK.
    // Thus in this test, fake version of the view handler is injected for LinearLayout.
    ViewHandlerManager fakeViewHandlerManager = new FakeViewHandlerManager(getProject());
    Project mockProject = mock(Project.class);
    when(mockProject.getComponent(ViewHandlerManager.class)).thenReturn(fakeViewHandlerManager);
    myProvider = new LayoutInspectorProvider(mockProject);
  }

  public void testIsApplicable() {
    assertThat(isApplicable(myProvider, myMerge)).isFalse();
    assertThat(isApplicable(myProvider, myTextView)).isFalse();
    assertThat(isApplicable(myProvider, myCheckBox1)).isFalse();
    assertThat(isApplicable(myProvider, myButton)).isFalse();
    assertThat(isApplicable(myProvider, myButtonInLinearLayout)).isTrue();
    assertThat(isApplicable(myProvider, myTextViewInLinearLayout, myButtonInLinearLayout)).isTrue();
  }

  public void testInspectorComponent() {
    List<NlComponent> components = ImmutableList.of(myTextViewInLinearLayout, myButtonInLinearLayout);
    Map<String, NlProperty> properties = getPropertyMap(components);
    assertThat(myProvider.isApplicable(components, properties, myPropertiesManager)).isTrue();
    InspectorComponent inspector = myProvider.createCustomInspector(components, properties, myPropertiesManager);

    InspectorPanel panel = mock(InspectorPanel.class);
    when(panel.addComponent(anyString(), anyString(), any())).thenAnswer(invocation -> new JLabel());
    inspector.attachToInspector(panel);

    List<NlComponentEditor> editors = inspector.getEditors();
    assertThat(editors.size()).isEqualTo(1);
    assertThat(inspector.getMaxNumberOfRows()).isEqualTo(2);
    NlComponentEditor editor = editors.get(0);
    NlProperty property = editor.getProperty();
    assertThat(property).isNotNull();
    verify(panel).addTitle("LinearLayout_layout");
    verify(panel).addComponent(eq(ATTR_LAYOUT_WEIGHT), eq(property.getTooltipText()), eq(editor.getComponent()));
  }

  /**
   * Fake implementation of the {@link ViewHandlerManager} to inject a test only view handler.
   */
  private static class FakeViewHandlerManager extends ViewHandlerManager {

    public FakeViewHandlerManager(@NotNull Project project) {
      super(project);
    }

    @Nullable
    @Override
    public ViewHandler getHandler(@NotNull String viewTag) {
      ViewHandler viewHandler = createViewHandler(viewTag);
      if (viewHandler == null) {
        viewHandler = super.getHandler(viewTag);
      }
      return viewHandler;
    }

    private static ViewHandler createViewHandler(String viewTag) {
      ViewHandler viewHandler = null;
      switch (viewTag) {
        case LINEAR_LAYOUT:
          viewHandler = new FakeLinearLayoutHandler();
      }
      return viewHandler;
    }
  }

  /**
   * Fake implementation for the LinearLayout so that {@link ViewHandler#getLayoutInspectorProperties()}
   * can be overridden.
   */
  private static class FakeLinearLayoutHandler extends ViewGroupHandler {

    @NotNull
    @Override
    public List<String> getLayoutInspectorProperties() {
      return ImmutableList.of(ATTR_LAYOUT_WEIGHT);
    }
  }
}
