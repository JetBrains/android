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

import com.android.tools.adtui.common.AdtSecondaryPanel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.editors.NlComponentEditor;
import com.android.tools.idea.common.property.inspector.InspectorComponent;
import com.android.tools.idea.common.property.inspector.InspectorPanel;
import com.android.tools.idea.common.property.inspector.InspectorProvider;
import com.android.tools.idea.uibuilder.model.PreferenceUtils;
import com.android.tools.idea.uibuilder.property.EmptyProperty;
import com.android.tools.idea.uibuilder.property.NlFlagPropertyItem;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

public class TextInspectorProvider implements InspectorProvider<NlPropertiesManager> {
  @VisibleForTesting
  static final List<String> REQUIRED_TEXT_PROPERTIES = ImmutableList.of(
    ATTR_TEXT,
    ATTR_CONTENT_DESCRIPTION,
    ATTR_TEXT_APPEARANCE,
    ATTR_TYPEFACE,
    ATTR_TEXT_SIZE,
    ATTR_LINE_SPACING_EXTRA,
    ATTR_TEXT_STYLE,
    ATTR_TEXT_ALL_CAPS,
    ATTR_TEXT_COLOR);

  private TextInspectorComponent myComponent;

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components,
                              @NotNull Map<String, NlProperty> properties,
                              @NotNull NlPropertiesManager propertiesManager) {
    if (!properties.keySet().containsAll(REQUIRED_TEXT_PROPERTIES)) {
      return false;
    }
    for (NlComponent component : components) {
      // Do not show Text properties for preferences even though the component may have all the properties
      if (PreferenceUtils.VALUES.contains(component.getTagName())) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  @Override
  public TextInspectorComponent createCustomInspector(@NotNull List<NlComponent> components,
                                                      @NotNull Map<String, NlProperty> properties,
                                                      @NotNull NlPropertiesManager propertiesManager) {
    if (myComponent == null) {
      myComponent = new TextInspectorComponent(propertiesManager);
    }
    myComponent.updateProperties(components, properties, propertiesManager);
    return myComponent;
  }

  @Override
  public void resetCache() {
    myComponent = null;
  }

  /**
   * Text font inspector component for setting font family, size, decorations, color.
   */
  static class TextInspectorComponent implements InspectorComponent<NlPropertiesManager> {
    private final NlReferenceEditor myTextEditor;
    private final NlReferenceEditor myDesignTextEditor;
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
      myDescriptionEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);

      myStyleEditor = NlEnumEditor.createForInspector(createEnumStyleListener());
      myFontFamilyEditor = NlEnumEditor.createForInspector(DEFAULT_LISTENER);
      myTypefaceEditor = NlEnumEditor.createForInspector(DEFAULT_LISTENER);
      myFontSizeEditor = NlEnumEditor.createForInspectorWithBrowseButton(DEFAULT_LISTENER);
      mySpacingEditor = NlEnumEditor.createForInspectorWithBrowseButton(DEFAULT_LISTENER);
      myBoldEditor = new NlBooleanIconEditor(StudioIcons.LayoutEditor.Properties.TEXT_STYLE_BOLD, "Bold");
      myItalicsEditor = new NlBooleanIconEditor(StudioIcons.LayoutEditor.Properties.TEXT_STYLE_ITALIC, "Italics");
      myAllCapsEditor = new NlBooleanIconEditor(StudioIcons.LayoutEditor.Properties.TEXT_STYLE_UPPERCASE, "All Caps");
      myStartEditor = new NlBooleanIconEditor(StudioIcons.LayoutEditor.Properties.TEXT_ALIGN_LAYOUT_LEFT, "Align Start of View", TextAlignment.VIEW_START);
      myLeftEditor = new NlBooleanIconEditor(StudioIcons.LayoutEditor.Properties.TEXT_ALIGN_LEFT, "Align Start of Text", TextAlignment.TEXT_START);
      myCenterEditor = new NlBooleanIconEditor(StudioIcons.LayoutEditor.Properties.TEXT_ALIGN_CENTER, "Align Center", TextAlignment.CENTER);
      myRightEditor = new NlBooleanIconEditor(StudioIcons.LayoutEditor.Properties.TEXT_ALIGN_RIGHT, "Align End of Text", TextAlignment.TEXT_END);
      myEndEditor = new NlBooleanIconEditor(StudioIcons.LayoutEditor.Properties.TEXT_ALIGN_LAYOUT_RIGHT, "Align End of View", TextAlignment.VIEW_END);
      myColorEditor = NlReferenceEditor.createForInspectorWithBrowseButton(propertiesManager.getProject(), DEFAULT_LISTENER);

      myTextStylePanel = new AdtSecondaryPanel(new FlowLayout(FlowLayout.LEADING));
      myTextStylePanel.setFocusable(false);
      myTextStylePanel.add(myBoldEditor.getComponent());
      myTextStylePanel.add(myItalicsEditor.getComponent());
      myTextStylePanel.add(myAllCapsEditor.getComponent());

      myAlignmentPanel = new AdtSecondaryPanel(new FlowLayout(FlowLayout.LEADING));
      myAlignmentPanel.setFocusable(false);
      myAlignmentPanel.add(myStartEditor.getComponent());
      myAlignmentPanel.add(myLeftEditor.getComponent());
      myAlignmentPanel.add(myCenterEditor.getComponent());
      myAlignmentPanel.add(myRightEditor.getComponent());
      myAlignmentPanel.add(myEndEditor.getComponent());
    }

    @Override
    public void updateProperties(@NotNull List<NlComponent> components,
                                 @NotNull Map<String, NlProperty> properties,
                                 @NotNull NlPropertiesManager propertiesManager) {
      myText = properties.get(ATTR_TEXT);
      myDesignText = myText.getDesignTimeProperty();
      myDescription = properties.get(ATTR_CONTENT_DESCRIPTION);
      myStyle = properties.get(ATTR_TEXT_APPEARANCE);
      myFontFamily = properties.getOrDefault(ATTR_FONT_FAMILY, EmptyProperty.INSTANCE);
      myTypeface = properties.get(ATTR_TYPEFACE);
      myFontSize = properties.get(ATTR_TEXT_SIZE);
      mySpacing = properties.get(ATTR_LINE_SPACING_EXTRA);
      myTextStyle = (NlFlagPropertyItem)properties.get(ATTR_TEXT_STYLE);
      myTextAllCaps = properties.get(ATTR_TEXT_ALL_CAPS);
      myAlignment = properties.getOrDefault(ATTR_TEXT_ALIGNMENT, EmptyProperty.INSTANCE);
      myColor = properties.get(ATTR_TEXT_COLOR);
    }

    @Override
    public int getMaxNumberOfRows() {
      return 12;
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector) {
      refresh();
      inspector.addTitle("TextView");
      inspector.addComponent(ATTR_TEXT, myText.getTooltipText(), myTextEditor.getComponent());
      JLabel designText = inspector.addComponent(ATTR_TEXT, myDesignText.getTooltipText(), myDesignTextEditor.getComponent());
      designText.setIcon(StudioIcons.LayoutEditor.Properties.DESIGN_PROPERTY);
      inspector.addComponent(ATTR_CONTENT_DESCRIPTION, myDescription.getTooltipText(), myDescriptionEditor.getComponent());

      inspector.addExpandableComponent(ATTR_TEXT_APPEARANCE, myStyle.getTooltipText(), myStyleEditor.getComponent(),
                                       myStyleEditor.getKeySource());
      if (myFontFamily != EmptyProperty.INSTANCE) {
        inspector.addComponent(ATTR_FONT_FAMILY, myFontFamily.getTooltipText(), myFontFamilyEditor.getComponent());
      }
      inspector.addComponent(ATTR_TYPEFACE, myTypeface.getTooltipText(), myTypefaceEditor.getComponent());
      inspector.addComponent(ATTR_TEXT_SIZE, myFontSize.getTooltipText(), myFontSizeEditor.getComponent());
      inspector.addComponent(ATTR_LINE_SPACING_EXTRA, mySpacing.getTooltipText(), mySpacingEditor.getComponent());
      inspector.addComponent(ATTR_TEXT_COLOR, myColor.getTooltipText(), myColorEditor.getComponent());
      inspector.addComponent(ATTR_TEXT_STYLE, myTextStyle.getTooltipText(), myTextStylePanel);
      if (myAlignment != EmptyProperty.INSTANCE) {
        inspector.addComponent(ATTR_TEXT_ALIGNMENT, myAlignment.getTooltipText(), myAlignmentPanel);
      }
    }

    @Override
    public void refresh() {
      myTextEditor.setProperty(myText);
      myDesignTextEditor.setProperty(myDesignText);
      myDescriptionEditor.setProperty(myDescription);
      myStyleEditor.setProperty(myStyle);
      myFontFamilyEditor.setProperty(myFontFamily);
      myTypefaceEditor.setProperty(myTypeface);
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

    @Override
    @NotNull
    public List<NlComponentEditor> getEditors() {
      return ImmutableList.of(
        myTextEditor,
        myDesignTextEditor,
        myDescriptionEditor,
        myStyleEditor,
        myFontFamilyEditor,
        myTypefaceEditor,
        myFontSizeEditor,
        mySpacingEditor,
        myColorEditor);
    }

    @TestOnly
    public List<NlBooleanIconEditor> getTextStyleEditors() {
      return ImmutableList.of(
        myBoldEditor,
        myItalicsEditor,
        myAllCapsEditor
      );
    }

    @TestOnly
    public List<NlBooleanIconEditor> getTextAlignmentEditors() {
      return ImmutableList.of(
        myStartEditor,
        myLeftEditor,
        myCenterEditor,
        myRightEditor,
        myEndEditor
      );
    }

    private NlEditingListener createEnumStyleListener() {
      return new NlEditingListener() {
        @Override
        public void stopEditing(@NotNull NlComponentEditor editor, @Nullable Object value) {
          // TODO: Create a write transaction here to include all these changes in one undo event
          if (!Objects.equal(value, myStyle.getValue())) {
            myStyle.setValue(value);
            myTypeface.setValue(null);
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

        @Override
        public void cancelEditing(@NotNull NlComponentEditor editor) {
        }
      };
    }
  }
}
