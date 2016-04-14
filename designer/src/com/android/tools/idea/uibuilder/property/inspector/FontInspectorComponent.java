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
import com.android.tools.idea.uibuilder.property.NlFlagPropertyItem;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlBooleanIconEditor;
import com.android.tools.idea.uibuilder.property.editors.NlEnumEditor;
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.*;

/**
 * Text font inspector component for setting font family, size, decorations, color.
 */
public class FontInspectorComponent implements InspectorComponent {
  public static final Set<String> TEXT_PROPERTIES = ImmutableSet.of(
    ATTR_TEXT_APPEARANCE, ATTR_FONT_FAMILY, ATTR_TEXT_SIZE, ATTR_LINE_SPACING_EXTRA, ATTR_TEXT_STYLE, ATTR_TEXT_ALIGNMENT, ATTR_TEXT_COLOR,
    ATTR_TEXT_COLOR_HINT);

  private final NlPropertiesManager myPropertiesManager;

  private final NlProperty myStyle;
  private final NlProperty myFontFamily;
  private final NlProperty myFontSize;
  private final NlProperty mySpacing;
  private final NlFlagPropertyItem myTextStyle;
  private final NlProperty myTextAllCaps;
  private final NlProperty myAlignment;
  private final NlProperty myColor;

  private final NlEnumEditor myStyleEditor;
  private final NlEnumEditor myFontFamilyEditor;
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

  public FontInspectorComponent(@NotNull Map<String, NlProperty> properties,
                                @NotNull NlPropertiesManager propertiesManager) {
    myPropertiesManager = propertiesManager;

    myStyle = properties.get(ATTR_TEXT_APPEARANCE);
    myFontFamily = properties.get(ATTR_FONT_FAMILY);
    myFontSize = properties.get(ATTR_TEXT_SIZE);
    mySpacing = properties.get(ATTR_LINE_SPACING_EXTRA);
    myTextStyle = (NlFlagPropertyItem)properties.get(ATTR_TEXT_STYLE);
    myTextAllCaps = properties.get(ATTR_TEXT_ALL_CAPS);
    myAlignment = properties.get(ATTR_TEXT_ALIGNMENT);
    myColor = properties.get(ATTR_TEXT_COLOR);

    NlEnumEditor.Listener enumListener = createEnumListener();

    myStyleEditor = NlEnumEditor.createForInspector(enumListener);
    myFontFamilyEditor = NlEnumEditor.createForInspector(enumListener);
    myFontSizeEditor = NlEnumEditor.createForInspector(enumListener);
    mySpacingEditor = NlEnumEditor.createForInspector(enumListener);
    myBoldEditor = new NlBooleanIconEditor(AndroidVectorIcons.EditorIcons.Bold);
    myItalicsEditor = new NlBooleanIconEditor(AndroidVectorIcons.EditorIcons.Italic);
    myAllCapsEditor = new NlBooleanIconEditor(AndroidVectorIcons.EditorIcons.AllCaps);
    myStartEditor = new NlBooleanIconEditor(AndroidVectorIcons.EditorIcons.AlignLeft, TextAlignment.VIEW_START);
    myLeftEditor = new NlBooleanIconEditor(AndroidVectorIcons.EditorIcons.AlignLeft, TextAlignment.TEXT_START);
    myCenterEditor = new NlBooleanIconEditor(AndroidVectorIcons.EditorIcons.AlignCenter, TextAlignment.CENTER);
    myRightEditor = new NlBooleanIconEditor(AndroidVectorIcons.EditorIcons.AlignRight, TextAlignment.TEXT_END);
    myEndEditor = new NlBooleanIconEditor(AndroidVectorIcons.EditorIcons.AlignRight, TextAlignment.VIEW_END);
    myColorEditor = NlReferenceEditor.createForInspectorWithBrowseButton(propertiesManager.getProject(), createReferenceListener());

    myTextStylePanel = new JPanel();
    myTextStylePanel.add(myBoldEditor.getComponent());
    myTextStylePanel.add(myItalicsEditor.getComponent());
    myTextStylePanel.add(myAllCapsEditor.getComponent());

    myAlignmentPanel = new JPanel();
    myAlignmentPanel.add(myStartEditor.getComponent());
    myAlignmentPanel.add(myLeftEditor.getComponent());
    myAlignmentPanel.add(myCenterEditor.getComponent());
    myAlignmentPanel.add(myRightEditor.getComponent());
    myAlignmentPanel.add(myEndEditor.getComponent());
  }

  @Override
  public void attachToInspector(@NotNull JPanel inspector) {
    InspectorPanel.addSeparator(inspector);
    InspectorPanel.addComponent(inspector, "Font", myStyle.getTooltipText(), myStyleEditor.getComponent());
    InspectorPanel.addComponent(inspector, "Font family", myFontFamily.getTooltipText(), myFontFamilyEditor.getComponent());
    InspectorPanel.addSplitComponents(inspector,
                                      "Size", myFontSize.getTooltipText(), myFontSizeEditor.getComponent(),
                                      "Spacing", mySpacing.getTooltipText(), mySpacingEditor.getComponent());
    InspectorPanel.addSplitComponents(inspector,
                                      "Decoration", myTextStyle.getTooltipText(), null,
                                      "Alignment", myAlignment.getTooltipText(), null);
    InspectorPanel.addSplitComponents(inspector, null, null, myTextStylePanel, null, null, myAlignmentPanel);
    InspectorPanel.addComponent(inspector, "Color", myColor.getTooltipText(), myColorEditor.getComponent());
    refresh();
  }

  @Override
  public void refresh() {
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

  private NlEnumEditor.Listener createEnumListener() {
    return new NlEnumEditor.Listener() {
      @Override
      public void itemPicked(@NotNull NlEnumEditor source, @Nullable String value) {
        if (source.getProperty() != null) {
          myPropertiesManager.setValue(source.getProperty(), value);
          if (source == myStyleEditor) {
            myFontFamily.setValue(null);
            myFontSize.setValue(null);
            mySpacing.setValue(null);
            myTextStyle.setValue(null);
            myTextAllCaps.setValue(null);
            myAlignment.setValue(null);
            myColor.setValue(null);
            refresh();
          }
        }
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
