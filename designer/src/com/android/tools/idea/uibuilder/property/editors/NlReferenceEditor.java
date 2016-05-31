/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.SdkConstants;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.renderer.NlDefaultRenderer;
import com.google.common.collect.ImmutableList;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import com.intellij.ui.components.JBLabel;
import icons.AndroidIcons;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;

public class NlReferenceEditor extends NlBaseComponentEditor implements NlComponentEditor {
  private static final int HORIZONTAL_SPACING = 4;
  private static final int VERTICAL_SPACING = 2;
  private static final int MIN_TEXT_WIDTH = 50;

  private final boolean myIncludeBrowseButton;
  private final JPanel myPanel;
  private final JLabel myIconLabel;
  private final JSlider mySlider;
  private final TextEditor myTextFieldWithAutoCompletion;
  private final CompletionProvider myCompletionProvider;
  private final JComponent myBrowsePanel;

  private NlProperty myProperty;
  private boolean myPropertyHasSlider;
  private String myLastReadValue;
  private Object myLastWriteValue;
  private boolean myUpdatingProperty;
  private boolean myCompletionsUpdated;

  public static NlTableCellEditor createForTable(@NotNull Project project) {
    NlTableCellEditor cellEditor = new NlTableCellEditor();
    cellEditor.init(new NlReferenceEditor(project, cellEditor, cellEditor, true));
    return cellEditor;
  }

  public static NlReferenceEditor createForInspector(@NotNull Project project, @NotNull NlEditingListener listener) {
    return new NlReferenceEditor(project, listener, null, false);
  }

  public static NlReferenceEditor createForInspectorWithBrowseButton(@NotNull Project project, @NotNull NlEditingListener listener) {
    return new NlReferenceEditor(project, listener, null, true);
  }

  private NlReferenceEditor(@NotNull Project project,
                            @NotNull NlEditingListener listener,
                            @Nullable BrowsePanel.Context context,
                            boolean includeBrowseButton) {
    super(listener);
    myIncludeBrowseButton = includeBrowseButton;
    myPanel = new JPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));

    myIconLabel = new JBLabel();
    myPanel.add(myIconLabel, BorderLayout.LINE_START);
    myIconLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        displayResourcePicker();
      }
    });

    mySlider = new JSlider();
    myPanel.add(mySlider, BorderLayout.LINE_START);
    mySlider.addChangeListener(event -> sliderChange());
    Dimension size = mySlider.getMinimumSize();
    size.setSize(size.width * 2, size.height);
    mySlider.setPreferredSize(size);

    myCompletionProvider = new CompletionProvider();
    myTextFieldWithAutoCompletion = new TextEditor(project, myCompletionProvider);
    myTextFieldWithAutoCompletion.setBorder(
      BorderFactory.createEmptyBorder(VERTICAL_SPACING, HORIZONTAL_SPACING, VERTICAL_SPACING, HORIZONTAL_SPACING));
    myPanel.add(myTextFieldWithAutoCompletion, BorderLayout.CENTER);

    boolean showDesignButton = context != null;
    if (!showDesignButton) {
      context = this;
    }
    myBrowsePanel = new BrowsePanel(context, showDesignButton);
    myPanel.add(myBrowsePanel, BorderLayout.LINE_END);

    myPanel.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        updateSliderVisibility();
      }
    });
    myTextFieldWithAutoCompletion.registerKeyboardAction(event -> stopEditing(getText()),
                                                         KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                                                         JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myTextFieldWithAutoCompletion.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent focusEvent) {
        if (!myCompletionsUpdated) {
          myCompletionProvider.updateCompletions(myProperty);
          myCompletionsUpdated = true;
        }
        myTextFieldWithAutoCompletion.selectAll();
      }

      @Override
      public void focusLost(FocusEvent event) {
        stopEditing(getText());
        // Remove the selection after we looses focus for feedback on which editor is the active editor
        myTextFieldWithAutoCompletion.removeSelection();
      }
    });
  }

  @NotNull
  private String getText() {
    String text = myTextFieldWithAutoCompletion.getDocument().getText();
    return Quantity.addUnit(myProperty, text);
  }

  @Override
  public void setEnabled(boolean enabled) {
    myTextFieldWithAutoCompletion.setEnabled(enabled);
    myBrowsePanel.setVisible(enabled && myIncludeBrowseButton);
    if (!enabled) {
      myLastReadValue = "";
      myLastWriteValue = "";
      myTextFieldWithAutoCompletion.setText("");
    }
  }

  @Override
  public NlProperty getProperty() {
    return myProperty;
  }

  @Override
  public void setProperty(@NotNull NlProperty property) {
    if (myProperty != property) {
      myProperty = property;
      myLastReadValue = null;

      myBrowsePanel.setVisible(myIncludeBrowseButton && BrowsePanel.hasResourceChooser(myProperty));
      myCompletionsUpdated = false;
    }

    myUpdatingProperty = true;
    try {
      myPropertyHasSlider = configureSlider();
      if (myPropertyHasSlider) {
        myPanel.remove(myIconLabel);
        myPanel.add(mySlider, BorderLayout.LINE_START);
      }
      else {
        myPanel.remove(mySlider);
        myPanel.add(myIconLabel, BorderLayout.LINE_START);
        Icon icon = NlDefaultRenderer.getIcon(myProperty);
        myIconLabel.setIcon(icon);
        myIconLabel.setVisible(icon != null);
      }

      String propValue = StringUtil.notNullize(myProperty.getValue());
      if (!propValue.equals(myLastReadValue)) {
        myLastReadValue = propValue;
        myLastWriteValue = propValue;
        myTextFieldWithAutoCompletion.setText(propValue);
      }
      Color color = myProperty.isDefaultValue(myLastReadValue) ? DEFAULT_VALUE_TEXT_COLOR : CHANGED_VALUE_TEXT_COLOR;
      myTextFieldWithAutoCompletion.setTextColor(color);
    }
    finally {
      myUpdatingProperty = false;
    }
  }

  private void updateSliderVisibility() {
    if (myPropertyHasSlider) {
      int widthForEditor =
        myPanel.getWidth() - 2 * HORIZONTAL_SPACING - mySlider.getPreferredSize().width - myBrowsePanel.getPreferredSize().width;
      mySlider.setVisible(widthForEditor >= MIN_TEXT_WIDTH);
    }
  }

  private boolean configureSlider() {
    if (myProperty == null) {
      return false;
    }
    AttributeDefinition definition = myProperty.getDefinition();
    if (definition == null || Collections.disjoint(definition.getFormats(),
                                                   ImmutableList.of(AttributeFormat.Dimension, AttributeFormat.Float))) {
      return false;
    }

    int maximum;
    int value;
    switch (myProperty.getName()) {
      case SdkConstants.ATTR_ELEVATION:
      case SdkConstants.ATTR_CARD_ELEVATION:
        // Range: [0, 24] integer (dp)
        maximum = 24;
        value = getValueInDp(0);
        break;
      case SdkConstants.ATTR_MIN_HEIGHT:
        // Range: [0, 250] integer (dp)
        maximum = 250;
        value = getValueInDp(180);
        break;
      case SdkConstants.ATTR_COLLAPSE_PARALLAX_MULTIPLIER:
        // Range: [0.0, 1.0] float (no unit)
        maximum = 10;
        value = (int)(getValueAsFloat(1.0) * 10 + 0.5);
        break;
      default:
        return false;
    }
    mySlider.setMinimum(0);
    mySlider.setMaximum(maximum);
    mySlider.setValue(value);
    return true;
  }

  private int getValueInDp(int defaultValue) {
    String valueAsString = myProperty.getValue();
    if (valueAsString == null) {
      return defaultValue;
    }
    Configuration configuration = myProperty.getModel().getConfiguration();
    Integer value = ResourceHelper.resolveDimensionPixelSize(myProperty.getResolver(), valueAsString, configuration);
    if (value == null) {
      return defaultValue;
    }

    return value * Density.DEFAULT_DENSITY / configuration.getDensity().getDpiValue();
  }

  private double getValueAsFloat(double defaultValue) {
    String valueAsString = myProperty.getValue();
    if (valueAsString == null) {
      return defaultValue;
    }
    try {
      return Float.parseFloat(valueAsString);
    }
    catch (NumberFormatException ignore) {
      return defaultValue;
    }
  }

  private String getSliderValue() {
    int value = mySlider.getValue();
    switch (myProperty.getName()) {
      case SdkConstants.ATTR_COLLAPSE_PARALLAX_MULTIPLIER:
        if (value == 10) {
          return "1.0";
        }
        return "0." + value;
      default:
        return Quantity.addUnit(myProperty, String.valueOf(value));
    }
  }

  private void sliderChange() {
    if (myUpdatingProperty) {
      return;
    }
    myTextFieldWithAutoCompletion.setText(getSliderValue());
    if (!mySlider.getValueIsAdjusting()) {
      stopEditing(getText());
    }
  }

  @Override
  public void requestFocus() {
    myTextFieldWithAutoCompletion.requestFocus();
    myTextFieldWithAutoCompletion.selectAll();
    myTextFieldWithAutoCompletion.scrollRectToVisible(myTextFieldWithAutoCompletion.getBounds());
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @NotNull
  @Override
  public Object getValue() {
    return getText();
  }

  @Override
  public void stopEditing(@Nullable Object newValue) {
    // Update the selected value for immediate feedback from resource editor.
    myTextFieldWithAutoCompletion.setText((String)newValue);
    // Select all the text to give visual confirmation that the value has been applied.
    if (myTextFieldWithAutoCompletion.hasFocus()) {
      myTextFieldWithAutoCompletion.selectAll();
    }

    if (!Objects.equals(newValue, myLastWriteValue)) {
      myLastWriteValue = newValue;
      myLastReadValue = null;
      super.stopEditing(newValue);
    }
  }

  private static class TextEditor extends TextFieldWithAutoCompletion<String> {
    private final TextAttributes myTextAttributes;

    public TextEditor(@NotNull Project project, @NotNull CompletionProvider provider) {
      super(project, provider, true, null);
      myTextAttributes = new TextAttributes(DEFAULT_VALUE_TEXT_COLOR, null, null, null, Font.PLAIN);
    }

    @Override
    public void addNotify() {
      super.addNotify();
      EditorEx editor = (EditorEx)getEditor();
      assert editor != null;
      editor.getColorsScheme().setAttributes(HighlighterColors.TEXT, myTextAttributes);
      editor.setHighlighter(new EmptyEditorHighlighter(myTextAttributes));
      editor.getDocument().putUserData(UndoConstants.DONT_RECORD_UNDO, true);
    }

    public void setTextColor(@NotNull Color color) {
      myTextAttributes.setForegroundColor(color);
      EditorEx editor = (EditorEx)getEditor();
      if (editor != null) {
        editor.getColorsScheme().setAttributes(HighlighterColors.TEXT, myTextAttributes);
        editor.setHighlighter(new EmptyEditorHighlighter(myTextAttributes));
      }
    }
  }

  private static class CompletionProvider extends TextFieldWithAutoCompletionListProvider<String> {
    protected CompletionProvider() {
      super(null);
    }

    @Nullable
    @Override
    public PrefixMatcher createPrefixMatcher(@NotNull String prefix) {
      return new CamelHumpMatcher(prefix);
    }

    @Nullable
    @Override
    protected Icon getIcon(@NotNull String item) {
      return item.startsWith(SdkConstants.ANDROID_PREFIX) ? AndroidIcons.Android : null;
    }

    @NotNull
    @Override
    protected String getLookupString(@NotNull String item) {
      return item;
    }

    @Nullable
    @Override
    protected String getTailText(@NotNull String item) {
      return null;
    }

    @Nullable
    @Override
    protected String getTypeText(@NotNull String item) {
      return null;
    }

    @Override
    public int compare(String item1, String item2) {
      return ResourceHelper.compareResourceReferences(item1, item2);
    }

    public void updateCompletions(@NotNull NlProperty property) {
      AttributeDefinition definition = property.getDefinition();
      if (definition == null) {
        setItems(null);
        return;
      }

      EnumSet<ResourceType> types = BrowsePanel.getResourceTypes(property.getName(), definition);

      // We include mipmap directly in the drawable maps
      if (types.contains(ResourceType.MIPMAP)) {
        types = types.clone();
        types.remove(ResourceType.MIPMAP);
        types.add(ResourceType.DRAWABLE);
      }

      if (types.contains(ResourceType.ID) && SdkConstants.ATTR_ID.equals(property.getName())) {
        // Don't offer code completion on id's; you typically want to specify a new, unique
        // one here, not reference an existing one
        setItems(null);
        return;
      }

      AndroidFacet facet = property.getModel().getFacet();
      // No point sorting: TextFieldWithAutoCompletionListProvider performs its
      // own sorting afterwards (by calling compare() above)
      setItems(ResourceHelper.getCompletionFromTypes(facet, types, false));
    }
  }
}
