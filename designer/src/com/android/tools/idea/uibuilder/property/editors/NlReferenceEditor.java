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
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.editors.BaseComponentEditor;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.property.EmptyProperty;
import com.android.tools.idea.uibuilder.property.editors.support.Quantity;
import com.android.tools.idea.uibuilder.property.editors.support.TextEditorWithAutoCompletion;
import com.android.tools.idea.uibuilder.property.renderer.NlDefaultRenderer;
import com.google.common.collect.ImmutableList;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.lookup.LookupAdapter;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.Clock;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import static com.android.SdkConstants.TOOLS_URI;
import static com.android.tools.idea.uibuilder.api.ViewEditor.resolveDimensionPixelSize;

public class NlReferenceEditor extends BaseComponentEditor {
  private static final int MIN_TEXT_WIDTH = 50;
  private static final int HORIZONTAL_SPACE_AFTER_LABEL = 4;

  private final JPanel myPanel;
  private final JLabel myIconLabel;
  private final JSlider mySlider;
  private final ComponentManager myProject;
  private final TextEditorWithAutoCompletion myTextEditorWithAutoCompletion;
  private final BrowsePanel myBrowsePanel;
  private final boolean myHasSliderSupport;
  private final boolean myIsInspector;

  private NlProperty myProperty;
  private boolean myPropertyHasSlider;
  private String myLastReadValue;
  private Object myLastWriteValue;
  private boolean myUpdatingProperty;
  private boolean myCompletionsUpdated;

  public static NlTableCellEditor createForTable(@NotNull Project project) {
    NlTableCellEditor cellEditor = new NlTableCellEditor();
    BrowsePanel browsePanel = new BrowsePanel(cellEditor, true);
    cellEditor.init(new NlReferenceEditor(project, cellEditor, browsePanel, false, true, false, VERTICAL_PADDING), browsePanel);
    return cellEditor;
  }

  public static NlReferenceEditor createForInspector(@NotNull Project project, @NotNull NlEditingListener listener) {
    return new NlReferenceEditor(project, listener, null, true, true, true, VERTICAL_SPACING);
  }

  @TestOnly
  public static NlReferenceEditor createForTableTesting(@NotNull Project project,
                                                        @NotNull NlEditingListener listener,
                                                        @NotNull BrowsePanel browsePanel) {
    return new NlReferenceEditor(project, listener, browsePanel, false, true, false, VERTICAL_SPACING);
  }

  public static NlReferenceEditor createForInspectorWithBrowseButton(@NotNull Project project, @NotNull NlEditingListener listener) {
    BrowsePanel.ContextDelegate delegate = new BrowsePanel.ContextDelegate();
    BrowsePanel browsePanel = new BrowsePanel(delegate, false);
    NlReferenceEditor editor = new NlReferenceEditor(project, listener, browsePanel, true, true, true, VERTICAL_SPACING);
    delegate.setEditor(editor);
    return editor;
  }

  protected NlReferenceEditor(@NotNull Project project,
                              @NotNull NlEditingListener listener,
                              @Nullable BrowsePanel browsePanel,
                              boolean includeBorder,
                              boolean includeSliderSupport,
                              boolean isInspector,
                              int verticalSpacing) {
    super(listener);
    myPanel = new JPanel(new BorderLayout());

    myIconLabel = new JBLabel();
    myPanel.add(myIconLabel, BorderLayout.LINE_START);
    myPanel.setFocusable(false);
    myIconLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        showBrowseDialog();
      }
    });
    myIconLabel.setBorder(JBUI.Borders.emptyRight(HORIZONTAL_SPACE_AFTER_LABEL));

    mySlider = new SliderWithTimeDelay();
    myPanel.add(mySlider, BorderLayout.LINE_START);
    mySlider.addChangeListener(event -> sliderChange());
    Dimension size = mySlider.getMinimumSize();
    size.setSize(size.width * 2, size.height);
    mySlider.setPreferredSize(size);
    mySlider.setVisible(includeSliderSupport);

    myProject = project;

    myTextEditorWithAutoCompletion = TextEditorWithAutoCompletion.create(project, JBUI.insets(verticalSpacing,
                                                                                              HORIZONTAL_PADDING,
                                                                                              verticalSpacing,
                                                                                              HORIZONTAL_PADDING));
    if (includeBorder) {
      myTextEditorWithAutoCompletion.setBorder(JBUI.Borders.empty(VERTICAL_SPACING, 0));
      myPanel.setBorder(JBUI.Borders.emptyLeft(HORIZONTAL_SPACING));
    }

    myTextEditorWithAutoCompletion.addLookupListener(new LookupAdapter() {
      @Override
      public void itemSelected(@NotNull LookupEvent event) {
        stopEditing(getText());
      }
    });

    myBrowsePanel = browsePanel;
    myPanel.add(myTextEditorWithAutoCompletion, BorderLayout.CENTER);
    if (browsePanel != null) {
      myBrowsePanel.setBorder(JBUI.Borders.emptyLeft(HORIZONTAL_COMPONENT_GAP));
      myPanel.add(myBrowsePanel, BorderLayout.LINE_END);
    }
    myPanel.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        updateSliderAndIconVisibility();
      }
    });
    myTextEditorWithAutoCompletion.registerKeyboardAction(event -> stopEditing(getText()),
                                                          KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                                                          JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    myTextEditorWithAutoCompletion.registerKeyboardAction(event -> cancel(),
                                                          KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                                          JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myTextEditorWithAutoCompletion.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(@NotNull FocusEvent event) {
        editorFocusGain(event);
      }

      @Override
      public void focusLost(@NotNull FocusEvent event) {
        editorFocusLost(event);
      }
    });
    myProperty = EmptyProperty.INSTANCE;
    myHasSliderSupport = includeSliderSupport;
    myIsInspector = isInspector;
  }

  protected TextEditorWithAutoCompletion getTextEditor() {
    return myTextEditorWithAutoCompletion;
  }

  protected void editorFocusGain(@NotNull FocusEvent event) {
    if (event.getOppositeComponent() != mySlider) {
      if (!myCompletionsUpdated && myProperty != EmptyProperty.INSTANCE) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          NlProperty property = myProperty;
          AndroidFacet facet = myProperty.getModel().getFacet();
          List<String> completions = TextEditorWithAutoCompletion.loadCompletions(facet, getResourceTypes(myProperty), myProperty);
          if (property == myProperty) {
            myTextEditorWithAutoCompletion.updateCompletions(completions);
            myCompletionsUpdated = true;

            // Auto completions may have failed to load because the completions were not computed yet.
            // Display them now to make auto completions more predictable.
            // This also allows us to test this consistently in a ui test see NlPropertyTableTest.
            UIUtil.invokeLaterIfNeeded(() -> {
              if (!getText().equals(StringUtil.notNullize(property.getValue()))) {
                Project project = property.getModel().getProject();
                AutoPopupController.getInstance(project).scheduleAutoPopup(myTextEditorWithAutoCompletion.getEditor());
              }
            });
          }
        });
      }
    }
    myTextEditorWithAutoCompletion.selectAll();
  }

  protected void editorFocusLost(@NotNull FocusEvent event) {
    if (event.getOppositeComponent() != mySlider) {
      stopEditing(getText());
      // Remove the selection after we lose focus for feedback on which editor is the active editor
      myTextEditorWithAutoCompletion.removeSelection();
    }
  }

  private static EnumSet<ResourceType> getResourceTypes(@NotNull NlProperty property) {
    AttributeDefinition definition = property.getDefinition();
    if (definition == null || SdkConstants.ATTR_ID.equals(property.getName())) {
      // Don't offer code completion on id's; you typically want to specify a new, unique
      // one here, not reference an existing one
      return EnumSet.noneOf(ResourceType.class);
    }

    EnumSet<ResourceType> resourceTypes = BrowsePanel.getResourceTypes(property);
    if (TOOLS_URI.equals(property.getNamespace())) {
      // Tools attributes can use sample data as source
      resourceTypes.add(ResourceType.SAMPLE_DATA);
    }

    return resourceTypes;
  }

  @NotNull
  private String getText() {
    String text = myTextEditorWithAutoCompletion.getDocument().getText();
    return Quantity.addUnit(myProperty, text);
  }

  @Override
  public void setEnabled(boolean enabled) {
    if (myProject.isDisposed()) {
      return;
    }

    myTextEditorWithAutoCompletion.setEnabled(enabled);
    if (myBrowsePanel != null) {
      myBrowsePanel.setVisible(enabled);
    }
    if (!enabled) {
      myLastReadValue = "";
      myLastWriteValue = "";
      myTextEditorWithAutoCompletion.setText("");
    }
  }

  @NotNull
  @Override
  public NlProperty getProperty() {
    return myProperty;
  }

  @Override
  public void setProperty(@NotNull NlProperty property) {
    if (myProperty != property) {
      myProperty = property;
      myLastReadValue = null;
      if (myBrowsePanel != null) {
        myBrowsePanel.setProperty(property);
      }
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
      }

      String propValue = StringUtil.notNullize(myProperty.getValue());
      if (!propValue.equals(myLastReadValue)) {
        myLastReadValue = propValue;
        myLastWriteValue = propValue;
        myTextEditorWithAutoCompletion.setText(propValue);
      }
      Color color = myProperty.isDefaultValue(myLastReadValue) ? DEFAULT_VALUE_TEXT_COLOR : CHANGED_VALUE_TEXT_COLOR;
      myTextEditorWithAutoCompletion.setTextColor(color);

      updateSliderAndIconVisibility();
    }
    finally {
      myUpdatingProperty = false;
    }
  }

  private void updateSliderAndIconVisibility() {
    if (myPropertyHasSlider) {
      int widthBrowsePanel = myBrowsePanel != null ? myBrowsePanel.getPreferredSize().width : 0;
      int widthForEditor = myPanel.getWidth() - mySlider.getPreferredSize().width - widthBrowsePanel;
      mySlider.setVisible(widthForEditor >= JBUI.scale(MIN_TEXT_WIDTH));
    }
    else {
      int iconSize = myTextEditorWithAutoCompletion.getHeight() - 4 * JBUI.scale(VERTICAL_SPACING);
      Icon icon = NlDefaultRenderer.getIcon(myProperty, iconSize);
      myIconLabel.setIcon(icon);
      myIconLabel.setVisible(icon != null);
      myIconLabel.setToolTipText("Pick a Resource");
    }
  }

  private boolean configureSlider() {
    if (myProperty == null || !myHasSliderSupport) {
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
      case SdkConstants.ATTR_MOCKUP_OPACITY:
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

  @AndroidDpCoordinate
  private int getValueInDp(@AndroidDpCoordinate int defaultValue) {
    String valueAsString = myProperty.getValue();
    if (valueAsString == null) {
      return defaultValue;
    }
    ResourceResolver resolver = myProperty.getResolver();
    if (resolver == null) {
      return defaultValue;
    }
    Configuration configuration = myProperty.getModel().getConfiguration();
    Integer value = resolveDimensionPixelSize(resolver, valueAsString, configuration);
    if (value == null) {
      return defaultValue;
    }

    return Coordinates.pxToDp(myProperty.getModel(), value);
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
      case SdkConstants.ATTR_MOCKUP_OPACITY:
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
    myTextEditorWithAutoCompletion.setText(getSliderValue());
    if (myIsInspector && !mySlider.getValueIsAdjusting()) {
      // For an editor in the inspector we want to update the value after the user
      // stops dragging the slider knob.
      // For an editor in the property table we don't want to update, since that
      // would remove the cell editor. We will update when the user stops editing
      // the cell.
      stopEditing(getText());
    }
  }

  @Override
  public void requestFocus() {
    myTextEditorWithAutoCompletion.requestFocus();
    myTextEditorWithAutoCompletion.selectAll();
    myTextEditorWithAutoCompletion.scrollRectToVisible(myTextEditorWithAutoCompletion.getBounds());
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
    myTextEditorWithAutoCompletion.setText((String)newValue);
    // Select all the text to give visual confirmation that the value has been applied.
    if (myTextEditorWithAutoCompletion.editorHasFocus()) {
      myTextEditorWithAutoCompletion.selectAll();
    }

    if (!Objects.equals(newValue, myLastWriteValue)) {
      myLastWriteValue = newValue;
      myLastReadValue = null;
      super.stopEditing(newValue);
    }
  }

  protected void cancel() {
    // Update the selected value for immediate feedback from resource editor.
    myTextEditorWithAutoCompletion.setText(myProperty.getValue());
    myTextEditorWithAutoCompletion.selectAll();
    cancelEditing();
  }

  // This is a workaround to avoid the problem where a click on an activate editor
  // in a table also causes the slider to change value. The workaround is simply
  // to delay all mouse events until a short time after the editor is created.
  public static class SliderWithTimeDelay extends JSlider {
    private static final long SHORT_WAIT_MILLIS = 300;
    private Clock myClock;
    private long myLastAddNotifyMillis;

    private SliderWithTimeDelay() {
      myClock = Clock.systemUTC();
    }

    @TestOnly
    public void setClock(@NotNull Clock clock) {
      myClock = clock;
    }

    @Override
    public void addNotify() {
      super.addNotify();
      myLastAddNotifyMillis = myClock.millis();
    }

    @Override
    protected void processMouseEvent(@NotNull MouseEvent event) {
      if (myClock.millis() - myLastAddNotifyMillis > SHORT_WAIT_MILLIS) {
        super.processMouseEvent(event);
      }
    }
  }
}
