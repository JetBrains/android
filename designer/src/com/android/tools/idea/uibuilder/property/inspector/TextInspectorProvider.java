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

import com.android.assetstudiolib.AndroidVectorIcons;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlFlagPropertyItem;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlBooleanIconEditor;
import com.android.tools.idea.uibuilder.property.editors.NlEnumEditor;
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
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
    private final NlEnumEditor myStyleEditor;
    private final NlEnumEditor myFontFamilyEditor;
    private final NlEnumEditor myTypefaceEditor;
    private final NlEnumEditor myFontSizeEditor;
    private final NlEnumEditor mySpacingEditor;
    private final NlBooleanIconEditor myBoldEditor;
    private final NlBooleanIconEditor myItalicsEditor;
    private final NlBooleanIconEditor myAllCapsEditor;
    private final NlBooleanIconEditor myStartEditor;
    private final NlBooleanIconEditor myLeftEditor;
    private final NlBooleanIconEditor myCenterEditor;
    private final NlBooleanIconEditor myRightEditor;
    private final NlBooleanIconEditor myEndEditor;
    private final NlReferenceEditor myColorEditor;
    private final JPanel myTextStylePanel;
    private final JPanel myAlignmentPanel;

    private NlProperty myText;
    private NlProperty myDesignText;
    private NlProperty myHint;
    private NlProperty myDescription;
    private NlProperty myStyle;
    private NlProperty myFontFamily;
    private NlProperty myTypeface;
    private NlProperty myFontSize;
    private NlProperty mySpacing;
    private NlFlagPropertyItem myTextStyle;
    private NlProperty myTextAllCaps;
    private NlProperty myAlignment;
    private NlProperty myColor;

    public TextInspectorComponent(@NotNull NlPropertiesManager propertiesManager) {
      Project project = propertiesManager.getProject();
      myTextEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myDesignTextEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myHintEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myDescriptionEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);

      myStyleEditor = NlEnumEditor.createForInspector(createEnumStyleListener());
      myFontFamilyEditor = NlEnumEditor.createForInspector(NlEnumEditor.getDefaultListener());
      myTypefaceEditor = NlEnumEditor.createForInspector(NlEnumEditor.getDefaultListener());
      myFontSizeEditor = NlEnumEditor.createForInspector(NlEnumEditor.getDefaultListener());
      mySpacingEditor = NlEnumEditor.createForInspector(NlEnumEditor.getDefaultListener());
      myBoldEditor = new NlBooleanIconEditor(AndroidVectorIcons.EditorIcons.Bold);
      myItalicsEditor = new NlBooleanIconEditor(AndroidVectorIcons.EditorIcons.Italic);
      myAllCapsEditor = new NlBooleanIconEditor(AndroidVectorIcons.EditorIcons.AllCaps);
      myStartEditor = new NlBooleanIconEditor(AndroidVectorIcons.EditorIcons.AlignLeft, TextAlignment.VIEW_START);
      myLeftEditor = new NlBooleanIconEditor(AndroidVectorIcons.EditorIcons.AlignLeft, TextAlignment.TEXT_START);
      myCenterEditor = new NlBooleanIconEditor(AndroidVectorIcons.EditorIcons.AlignCenter, TextAlignment.CENTER);
      myRightEditor = new NlBooleanIconEditor(AndroidVectorIcons.EditorIcons.AlignRight, TextAlignment.TEXT_END);
      myEndEditor = new NlBooleanIconEditor(AndroidVectorIcons.EditorIcons.AlignRight, TextAlignment.VIEW_END);
      myColorEditor = NlReferenceEditor.createForInspectorWithBrowseButton(propertiesManager.getProject(), DEFAULT_LISTENER);

      myTextStylePanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
      myTextStylePanel.add(myBoldEditor.getComponent());
      myTextStylePanel.add(myItalicsEditor.getComponent());
      myTextStylePanel.add(myAllCapsEditor.getComponent());

      myAlignmentPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
      myAlignmentPanel.add(myStartEditor.getComponent());
      myAlignmentPanel.add(myLeftEditor.getComponent());
      myAlignmentPanel.add(myCenterEditor.getComponent());
      myAlignmentPanel.add(myRightEditor.getComponent());
      myAlignmentPanel.add(myEndEditor.getComponent());
    }

    @Override
    public void updateProperties(@NotNull List<NlComponent> components, @NotNull Map<String, NlProperty> properties) {
      myText = properties.get(ATTR_TEXT);
      myDesignText = myText.getDesignTimeProperty();
      myHint = properties.get(ATTR_HINT);
      myDescription = properties.get(ATTR_CONTENT_DESCRIPTION);
      myStyle = properties.get(ATTR_TEXT_APPEARANCE);
      myFontFamily = properties.get(ATTR_FONT_FAMILY);
      myTypeface = properties.get(ATTR_TYPEFACE);
      myFontSize = properties.get(ATTR_TEXT_SIZE);
      mySpacing = properties.get(ATTR_LINE_SPACING_EXTRA);
      myTextStyle = (NlFlagPropertyItem)properties.get(ATTR_TEXT_STYLE);
      myTextAllCaps = properties.get(ATTR_TEXT_ALL_CAPS);
      myAlignment = properties.get(ATTR_TEXT_ALIGNMENT);
      myColor = properties.get(ATTR_TEXT_COLOR);
    }

    @Override
    public int getMaxNumberOfRows() {
      return 13;
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector) {
      inspector.addTitle("TextView");
      inspector.addComponent(ATTR_TEXT, myText.getTooltipText(), myTextEditor.getComponent());
      JLabel designText = inspector.addComponent(ATTR_TEXT, myDesignText.getTooltipText(), myDesignTextEditor.getComponent());
      designText.setIcon(AndroidIcons.NeleIcons.DesignProperty);
      inspector.addComponent(ATTR_HINT, myHint.getTooltipText(), myHintEditor.getComponent());
      inspector.addComponent(ATTR_CONTENT_DESCRIPTION, myDescription.getTooltipText(), myDescriptionEditor.getComponent());

      inspector.addExpandableComponent(ATTR_TEXT_APPEARANCE, myStyle.getTooltipText(), myStyleEditor.getComponent());
      inspector.addComponent(ATTR_FONT_FAMILY, myFontFamily.getTooltipText(), myFontFamilyEditor.getComponent());
      inspector.addComponent(ATTR_TYPEFACE, myTypeface.getTooltipText(), myTypefaceEditor.getComponent());
      inspector.addComponent(ATTR_TEXT_SIZE, myFontSize.getTooltipText(), myFontSizeEditor.getComponent());
      inspector.addComponent(ATTR_LINE_SPACING_EXTRA, mySpacing.getTooltipText(), mySpacingEditor.getComponent());
      inspector.addComponent(ATTR_TEXT_COLOR, myColor.getTooltipText(), myColorEditor.getComponent());
      inspector.addComponent(ATTR_TEXT_STYLE, myTextStyle.getTooltipText(), myTextStylePanel);
      inspector.addComponent(ATTR_TEXT_ALIGNMENT, myAlignment.getTooltipText(), myAlignmentPanel);
      refresh();
    }

    @Override
    public void refresh() {
      myTextEditor.setProperty(myText);
      myDesignTextEditor.setProperty(myDesignText);
      myHintEditor.setProperty(myHint);
      myDescriptionEditor.setProperty(myDescription);
      myStyleEditor.setProperty(myStyle);
      myFontFamilyEditor.setProperty(myFontFamily);
      myFontSizeEditor.setProperty(myFontSize);
      mySpacingEditor.setProperty(mySpacing);
      myBoldEditor.setProperty(myTextStyle.getChildProperty(TextStyle.VALUE_BOLD));
      myItalicsEditor.setProperty(myTextStyle.getChildProperty(TextStyle.VALUE_ITALIC));
      myAllCapsEditor.setProperty(myTextAllCaps);
      myStartEditor.setProperty(myAlignment);
      myLeftEditor.setProperty(myAlignment);
      myCenterEditor.setProperty(myAlignment);
      myRightEditor.setProperty(myAlignment);
      myEndEditor.setProperty(myAlignment);
      myColorEditor.setProperty(myColor);
    }

    private NlEnumEditor.Listener createEnumStyleListener() {
      return new NlEnumEditor.Listener() {
        @Override
        public void itemPicked(@NotNull NlEnumEditor source, @Nullable String value) {
          // TODO: Create a write transaction here to include all these changes in one undo event
          myStyle.setValue(value);
          myFontFamily.setValue(null);
          myFontSize.setValue(null);
          mySpacing.setValue(null);
          myTextStyle.setValue(null);
          myTextAllCaps.setValue(null);
          myAlignment.setValue(null);
          myColor.setValue(null);
          refresh();
        }

        @Override
        public void resourcePicked(@NotNull NlEnumEditor source, @NotNull String value) {
          itemPicked(source, value);
        }

        @Override
        public void resourcePickerCancelled(@NotNull NlEnumEditor source) {
        }
      };
    }
  }
}
