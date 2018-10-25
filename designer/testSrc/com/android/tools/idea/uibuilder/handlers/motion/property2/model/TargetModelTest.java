/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property2.model;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.NlComponentMixin;
import com.intellij.openapi.project.Project;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

public class TargetModelTest {

  @Test
  public void testModelWithTextView() {
    NlComponent component = mockComponent("textView1", SdkConstants.TEXT_VIEW);

    TargetModel model = new TargetModel(component, "Label");
    assertThat(model.getComponentIcon()).isSameAs(StudioIcons.LayoutEditor.Palette.TEXT_VIEW);
    assertThat(model.getComponentName()).isEqualTo("textView1");
    assertThat(model.getElementDescription()).isEqualTo("Label");
  }

  @Test
  public void testModelWithImageView() {
    NlComponent component = mockComponent("image2", SdkConstants.IMAGE_VIEW);

    TargetModel model = new TargetModel(component, "OtherLabel");
    assertThat(model.getComponentIcon()).isSameAs(StudioIcons.LayoutEditor.Palette.IMAGE_VIEW);
    assertThat(model.getComponentName()).isEqualTo("image2");
    assertThat(model.getElementDescription()).isEqualTo("OtherLabel");
  }

  @Test
  public void testModelWithButtonEWithoutId() {
    NlComponent component = mockComponent(null, SdkConstants.BUTTON);

    TargetModel model = new TargetModel(component, "Overview");
    assertThat(model.getComponentIcon()).isSameAs(StudioIcons.LayoutEditor.Palette.BUTTON);
    assertThat(model.getComponentName()).isEqualTo("<unnamed>");
    assertThat(model.getElementDescription()).isEqualTo("Overview");
  }

  @NotNull
  private static NlComponent mockComponent(@Nullable String id, @NotNull String tagName) {
    Project project = mock(Project.class);
    ViewHandlerManager viewHandlerManager = new ViewHandlerManager(project);
    when(project.getComponent(ArgumentMatchers.eq(ViewHandlerManager.class))).thenReturn(viewHandlerManager);
    NlModel model = mock(NlModel.class);
    when(model.getProject()).thenReturn(project);
    NlComponent component = mock(NlComponent.class);
    when(component.getId()).thenReturn(id);
    when(component.getTagName()).thenReturn(tagName);
    when(component.getModel()).thenReturn(model);
    NlComponentMixin mixin = new NlComponentMixin(component);
    when(component.getMixin()).thenReturn(mixin);
    return component;
  }
}
