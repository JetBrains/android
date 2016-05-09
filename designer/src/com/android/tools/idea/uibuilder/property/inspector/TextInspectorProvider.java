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
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

public class TextInspectorProvider implements InspectorProvider {
  private static final Set<String> TEXT_PROPERTIES = ImmutableSet.of(ATTR_TEXT, ATTR_HINT, ATTR_CONTENT_DESCRIPTION);

  private TextInspectorComponent myComponent;

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components, @NotNull Map<String, NlProperty> properties) {
    return properties.keySet().containsAll(TEXT_PROPERTIES);
  }

  @NotNull
  @Override
  public InspectorComponent createCustomInspector(@NotNull List<NlComponent> components,
                                                  @NotNull Map<String, NlProperty> properties,
                                                  @NotNull NlPropertiesManager propertiesManager) {
    if (myComponent == null) {
      myComponent = new TextInspectorComponent(propertiesManager);
    }
    myComponent.updateProperties(components, properties);
    return myComponent;
  }

  /**
   * Text font inspector component for setting font family, size, decorations, color.
   */
  private static class TextInspectorComponent implements InspectorComponent {
    private final NlReferenceEditor myTextEditor;
    private final NlReferenceEditor myDesignTextEditor;
    private final NlReferenceEditor myHintEditor;
    private final NlReferenceEditor myDescriptionEditor;

    private NlProperty myText;
    private NlProperty myDesignText;
    private NlProperty myHint;
    private NlProperty myDescription;

    public TextInspectorComponent(@NotNull NlPropertiesManager propertiesManager) {
      Project project = propertiesManager.getProject();
      myTextEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myDesignTextEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myHintEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myDescriptionEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
    }

    @Override
    public void updateProperties(@NotNull List<NlComponent> components, @NotNull Map<String, NlProperty> properties) {
      myText = properties.get(ATTR_TEXT);
      myDesignText = myText.getDesignTimeProperty();
      myHint = properties.get(ATTR_HINT);
      myDescription = properties.get(ATTR_CONTENT_DESCRIPTION);
    }

    @Override
    public int getMaxNumberOfRows() {
      return 6;
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector) {
      inspector.addSeparator();
      inspector.addTitle("TextView");
      inspector.addComponent(ATTR_TEXT, myText.getTooltipText(), myTextEditor.getComponent());
      JLabel designText = inspector.addComponent(ATTR_TEXT, myDesignText.getTooltipText(), myDesignTextEditor.getComponent());
      designText.setIcon(AndroidIcons.NeleIcons.DesignProperty);
      inspector.addComponent(ATTR_HINT, myHint.getTooltipText(), myHintEditor.getComponent());
      inspector.addComponent("description", myDescription.getTooltipText(), myDescriptionEditor.getComponent());
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
}
