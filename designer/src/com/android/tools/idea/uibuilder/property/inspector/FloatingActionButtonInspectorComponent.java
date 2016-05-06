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

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlEnumEditor;
import com.android.tools.idea.uibuilder.property.editors.NlFlagsEditor;
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

/**
 * Text font inspector component for setting font family, size, decorations, color.
 */
public class FloatingActionButtonInspectorComponent implements InspectorComponent {
  private final NlReferenceEditor myImageEditor;
  private final NlReferenceEditor myBackgroundEditor;
  private final NlReferenceEditor myRippleColorEditor;
  private final NlReferenceEditor myTintEditor;
  private final NlEnumEditor myFabSizeEditor;
  private final NlReferenceEditor myAnchorEditor;
  private final NlFlagsEditor myAnchorGravityEditor;
  private final NlReferenceEditor myElevationEditor;

  private NlProperty myImage;
  private NlProperty myBackground;
  private NlProperty myRippleColor;
  private NlProperty myTint;
  private NlProperty myFabSize;
  private NlProperty myAnchor;
  private NlProperty myAnchorGravity;
  private NlProperty myElevation;

  public FloatingActionButtonInspectorComponent(@NotNull NlPropertiesManager propertiesManager) {
    Project project = propertiesManager.getProject();
    myImageEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
    myBackgroundEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
    myRippleColorEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
    myTintEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
    myFabSizeEditor = NlEnumEditor.createForInspector(NlEnumEditor.getDefaultListener());
    myAnchorEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
    myAnchorGravityEditor = NlFlagsEditor.create();
    myElevationEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
  }

  @Override
  public void updateProperties(@NotNull List<NlComponent> components, @NotNull Map<String, NlProperty> properties) {
    myImage = properties.get(ATTR_SRC);
    myBackground = properties.get(ATTR_BACKGROUND_TINT);
    myRippleColor = properties.get(ATTR_RIPPLE_COLOR);
    myTint = properties.get(ATTR_TINT);
    myFabSize = properties.get(ATTR_FAB_SIZE);
    myAnchor = properties.get(ATTR_LAYOUT_ANCHOR);
    myAnchorGravity = properties.get(ATTR_LAYOUT_ANCHOR_GRAVITY);
    myElevation = properties.get(ATTR_ELEVATION);
  }

  @Override
  public int getMaxNumberOfRows() {
    return 10;
  }

  @Override
  public void attachToInspector(@NotNull InspectorPanel inspector) {
    inspector.addSeparator();
    inspector.addTitle("FloatingActionButton");
    inspector.addComponent(ATTR_SRC, myImage.getTooltipText(), myImageEditor.getComponent());
    inspector.addComponent(ATTR_BACKGROUND_TINT, myBackground.getTooltipText(), myBackgroundEditor.getComponent());
    inspector.addComponent(ATTR_RIPPLE_COLOR, myRippleColor.getTooltipText(), myRippleColorEditor.getComponent());
    inspector.addComponent(ATTR_TINT, myTint.getTooltipText(), myTintEditor.getComponent());
    inspector.addComponent(ATTR_FAB_SIZE, myFabSize.getTooltipText(), myFabSizeEditor.getComponent());
    if (myAnchor != null) {
      inspector.addComponent(ATTR_LAYOUT_ANCHOR, myAnchor.getTooltipText(), myAnchorEditor.getComponent());
    }
    if (myAnchorGravity != null) {
      inspector.addComponent(ATTR_LAYOUT_ANCHOR_GRAVITY, myAnchorGravity.getTooltipText(), myAnchorGravityEditor.getComponent());
    }
    inspector.addComponent(ATTR_ELEVATION, myElevation.getTooltipText(), myElevationEditor.getComponent());
    refresh();
  }

  @Override
  public void refresh() {
    myImageEditor.setProperty(myImage);
    myBackgroundEditor.setProperty(myBackground);
    myRippleColorEditor.setProperty(myRippleColor);
    myTintEditor.setProperty(myTint);
    myFabSizeEditor.setProperty(myFabSize);
    myAnchorEditor.setVisible(myAnchor != null);
    if (myAnchor != null) {
      myAnchorEditor.setProperty(myAnchor);
    }
    myAnchorGravityEditor.setVisible(myAnchorGravity != null);
    if (myAnchorGravity != null) {
      myAnchorGravityEditor.setProperty(myAnchorGravity);
    }
    myElevationEditor.setProperty(myElevation);
  }
}
