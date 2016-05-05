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

import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlBooleanEditor;
import com.android.tools.idea.uibuilder.property.editors.NlEnumEditor;
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

public class ImageViewInspectorComponent implements InspectorComponent {
  private final NlProperty mySource;
  private final NlProperty myContentDescription;
  private final NlProperty myBackground;
  private final NlProperty myScaleType;
  private final NlProperty myAdjustViewBounds;
  private final NlProperty myCropToPadding;

  private final NlReferenceEditor mySourceEditor;
  private final NlReferenceEditor myContentDescriptionEditor;
  private final NlReferenceEditor myBackgroundEditor;
  private final NlEnumEditor myScaleTypeEditor;
  private final NlBooleanEditor myAdjustViewBoundsEditor;
  private final NlBooleanEditor myCropToPaddingEditor;

  public ImageViewInspectorComponent(@NotNull Map<String, NlProperty> properties,
                                     @NotNull NlPropertiesManager propertiesManager) {
    mySource = properties.get(ATTR_SRC);
    myContentDescription = properties.get(ATTR_CONTENT_DESCRIPTION);
    myBackground = properties.get(ATTR_BACKGROUND);
    myScaleType = properties.get(ATTR_SCALE_TYPE);
    myAdjustViewBounds = properties.get(ATTR_ADJUST_VIEW_BOUNDS);
    myCropToPadding = properties.get(ATTR_CROP_TO_PADDING);

    Project project = propertiesManager.getProject();
    mySourceEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
    myContentDescriptionEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
    myBackgroundEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
    myScaleTypeEditor = NlEnumEditor.createForInspector(NlEnumEditor.getDefaultListener());
    myAdjustViewBoundsEditor = NlBooleanEditor.createForInspector(DEFAULT_LISTENER);
    myCropToPaddingEditor = NlBooleanEditor.createForInspector(DEFAULT_LISTENER);
  }

  @Override
  public int getMaxNumberOfRows() {
    return 8;
  }

  @Override
  public void attachToInspector(@NotNull InspectorPanel inspector) {
    inspector.addSeparator();
    inspector.addTitle("ImageView");
    inspector.addComponent(ATTR_SRC, mySource.getTooltipText(), mySourceEditor.getComponent());
    inspector.addComponent(ATTR_CONTENT_DESCRIPTION, myContentDescription.getTooltipText(), myContentDescriptionEditor.getComponent());
    inspector.addComponent(ATTR_BACKGROUND, myBackground.getTooltipText(), myBackgroundEditor.getComponent());
    inspector.addComponent(ATTR_SCALE_TYPE, myScaleType.getTooltipText(), myScaleTypeEditor.getComponent());
    inspector.addComponent(ATTR_ADJUST_VIEW_BOUNDS, myAdjustViewBounds.getTooltipText(), myAdjustViewBoundsEditor.getComponent());
    inspector.addComponent(ATTR_CROP_TO_PADDING, myCropToPadding.getTooltipText(), myCropToPaddingEditor.getComponent());
    refresh();
  }

  @Override
  public void refresh() {
    mySourceEditor.setProperty(mySource);
    myContentDescriptionEditor.setProperty(myContentDescription);
    myBackgroundEditor.setProperty(myBackground);
    myScaleTypeEditor.setProperty(myScaleType);
    myAdjustViewBoundsEditor.setProperty(myAdjustViewBounds);
    myCropToPaddingEditor.setProperty(myCropToPadding);
  }
}
