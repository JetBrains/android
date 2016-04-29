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
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.*;

/**
 * Text font inspector component for setting font family, size, decorations, color.
 */
public class TextInspectorComponent implements InspectorComponent {
  public static final Set<String> TEXT_PROPERTIES = ImmutableSet.of(ATTR_TEXT, ATTR_HINT, ATTR_CONTENT_DESCRIPTION);

  private final NlPropertiesManager myPropertiesManager;

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
    myPropertiesManager = propertiesManager;

    myText = properties.get(ATTR_TEXT);
    myDesignText = myText.getDesignTimeProperty();
    myHint = properties.get(ATTR_HINT);
    myDescription = properties.get(ATTR_CONTENT_DESCRIPTION);

    NlReferenceEditor.EditingListener listener = createReferenceListener();
    myTextEditor = NlReferenceEditor.createForInspectorWithBrowseButton(propertiesManager.getProject(), listener);
    myDesignTextEditor = NlReferenceEditor.createForInspectorWithBrowseButton(propertiesManager.getProject(), listener);
    myHintEditor = NlReferenceEditor.createForInspectorWithBrowseButton(propertiesManager.getProject(), listener);
    myDescriptionEditor = NlReferenceEditor.createForInspectorWithBrowseButton(propertiesManager.getProject(), listener);
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

  private NlReferenceEditor.EditingListener createReferenceListener() {
    return new NlReferenceEditor.EditingListener() {
      @Override
      public void stopEditing(@NotNull NlReferenceEditor source, @NotNull String value) {
        if (source.getProperty() != null) {
          myPropertiesManager.setValue(source.getProperty(), value);
          source.setProperty(source.getProperty());
        }
      }

      @Override
      public void cancelEditing(@NotNull NlReferenceEditor editor) {
      }
    };
  }
}
