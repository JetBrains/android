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
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

/**
 * Text font inspector component for setting font family, size, decorations, color.
 */
public class TextInspectorComponent implements InspectorComponent {
  public static final Set<String> TEXT_PROPERTIES = ImmutableSet.of(ATTR_TEXT, ATTR_HINT, ATTR_CONTENT_DESCRIPTION);

  private final NlProperty myText;
  private final NlProperty myDesignText;
  private final NlProperty myHint;
  private final NlProperty myDescription;

  private final NlReferenceEditor myTextEditor;
  private final NlReferenceEditor myDesignTextEditor;
  private final NlReferenceEditor myHintEditor;
  private final NlReferenceEditor myDescriptionEditor;

  public TextInspectorComponent(@NotNull Map<String, NlProperty> properties,
                                @NotNull NlPropertiesManager propertiesManager) {
    myText = properties.get(ATTR_TEXT);
    myDesignText = myText.getDesignTimeProperty();
    myHint = properties.get(ATTR_HINT);
    myDescription = properties.get(ATTR_CONTENT_DESCRIPTION);

    Project project = propertiesManager.getProject();
    myTextEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
    myDesignTextEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
    myHintEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
    myDescriptionEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
  }

  @Override
  public int getMaxNumberOfRows() {
    return 6;
  }

  @Override
  public void attachToInspector(@NotNull InspectorPanel inspector) {
    inspector.addSeparator();
    inspector.addExpandableTitle("Text", myText);
    inspector.addComponent("Text", myText.getTooltipText(), myTextEditor.getComponent());
    inspector.restartExpansionGroup();
    inspector.addComponent("Text (Design)", myDesignText.getTooltipText(), myDesignTextEditor.getComponent());
    inspector.addComponent("Hint", myHint.getTooltipText(), myHintEditor.getComponent());
    inspector.addComponent("Description", myDescription.getTooltipText(), myDescriptionEditor.getComponent());
    refresh();
  }

  @Override
  public void refresh() {
    myTextEditor.setProperty(myText);
    myDesignTextEditor.setProperty(myDesignText);
    myHintEditor.setProperty(myHint);
    myDescriptionEditor.setProperty(myDescription);
  }
}
