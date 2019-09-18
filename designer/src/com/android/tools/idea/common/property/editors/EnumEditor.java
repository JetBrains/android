/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.property.editors;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.uibuilder.property.EmptyProperty;
import com.android.tools.idea.uibuilder.property.editors.BrowsePanel;
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener;
import com.android.tools.idea.uibuilder.property.editors.NlEnumEditor;
import com.android.tools.idea.uibuilder.property.editors.support.EnumSupport;
import com.android.tools.idea.uibuilder.property.editors.support.Quantity;
import com.android.tools.idea.uibuilder.property.editors.support.ValueWithDisplayString;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.TextUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.SystemUtils;

abstract public class EnumEditor extends BaseComponentEditor implements NlComponentEditor {
  private EnumSupport myEnumSupport;

  private final JPanel myPanel;
  private final NlEnumEditor.CustomComboBox myCombo;
  private final JTextField myEditor;
  private final BrowsePanel myBrowsePanel;

  private NlProperty myProperty;
  private int myAddedValueIndex;
  private boolean myPopupValueChanged;
  private boolean myDisplayRealValue;

  @TestOnly
  public static EnumEditor createForTest(@NotNull NlEditingListener listener, CustomComboBox comboBox) {
    return new EnumEditor(listener, comboBox, null, false, true) {

      @Override
      protected EnumSupport getEnumSupport(@NotNull NlProperty property) {
        return new EnumSupport(property) {
          @NotNull
          @Override
          public List<ValueWithDisplayString> getAllValues() {
            return ImmutableList.of(new ValueWithDisplayString("foo", "bar"));
          }
        };
      }
    };
  }

  protected EnumEditor(@NotNull NlEditingListener listener,
                       @NotNull NlEnumEditor.CustomComboBox comboBox,
                       @Nullable BrowsePanel browsePanel,
                       boolean includeBorder,
                       boolean comboEditable) {
    super(listener);
    myAddedValueIndex = -1; // nothing added
    myPanel = new JPanel(new BorderLayout(JBUIScale.scale(HORIZONTAL_COMPONENT_GAP), 0));
    myPanel.setFocusable(false);
    myBrowsePanel = browsePanel;

    myCombo = comboBox;
    myCombo.setEditable(comboEditable);

    myCombo.addPopupMenuListener(new PopupMenuHandler());
    myCombo.addActionListener(this::comboValuePicked);
    if (includeBorder) {
      myCombo.setBorderPanel(myPanel);
    }

    myPanel.add(myCombo, BorderLayout.CENTER);
    if (browsePanel != null) {
      if (includeBorder) {
        browsePanel.setBorder(JBUI.Borders.empty(4, 1));
      }
      myPanel.add(browsePanel, BorderLayout.LINE_END);
    }

    myEditor = (JTextField)myCombo.getEditor().getEditorComponent();
    myEditor.registerKeyboardAction(event -> enter(),
                                    KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                                    JComponent.WHEN_FOCUSED);
    myEditor.registerKeyboardAction(event -> cancel(),
                                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                    JComponent.WHEN_FOCUSED);
    myEditor.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myDisplayRealValue = true;
        ValueWithDisplayString value = myCombo.getSelectedItem();
        value.setUseValueForToString(myDisplayRealValue);
        myEditor.setText(value.toString());
        myEditor.selectAll();
        myEditor.setForeground(CHANGED_VALUE_TEXT_COLOR);
      }

      @Override
      public void focusLost(FocusEvent event) {
        myDisplayRealValue = false;
        if (myProperty == EmptyProperty.INSTANCE) {
          return;
        }
        ValueWithDisplayString value = createFromEditorValue(myEditor.getText());
        UndoManager undoManager = UndoManager.getInstance(myProperty.getModel().getProject());
        // b/110880308: Avoid updating the property during undo/redo
        if (!Objects.equals(value.getValue(), myProperty.getValue()) && !undoManager.isUndoOrRedoInProgress()) {
          stopEditing(value.getValue());
        }

        value.setUseValueForToString(myDisplayRealValue);
        myEditor.setText(value.toString());
        myEditor.setForeground(value.getValue() != null ? CHANGED_VALUE_TEXT_COLOR : DEFAULT_VALUE_TEXT_COLOR);
        myEditor.select(0, 0);
      }
    });
    //noinspection unchecked
    myCombo.setRenderer(new EnumRenderer());
    myProperty = EmptyProperty.INSTANCE;
  }

  @Override
  public void setProperty(@NotNull NlProperty property) {
    setProperty(property, false);
  }

  public void setProperty(@NotNull NlProperty property, boolean force) {
    if (property != myProperty || force) {
      setModel(property);
    }
    if (myBrowsePanel != null) {
      myBrowsePanel.setProperty(property);
    }
    selectItem(createFromEditorValue(property.getValue()));
  }

  @Override
  public void setEnabled(boolean en) {
    myCombo.setEnabled(en);
  }

  @Override
  public void requestFocus() {
    myCombo.requestFocus();
  }

  protected abstract EnumSupport getEnumSupport(@NotNull NlProperty property);

  protected void setModel(@NotNull NlProperty property) {
    myProperty = property;
    myEnumSupport = getEnumSupport(property);

    List<ValueWithDisplayString> values = myEnumSupport.getAllValues();
    ValueWithDisplayString[] valueArray = values.toArray(new ValueWithDisplayString[0]);

    DefaultComboBoxModel<ValueWithDisplayString> newModel = new DefaultComboBoxModel<ValueWithDisplayString>(valueArray) {
      @Override
      public void setSelectedItem(Object object) {
        if (object instanceof String) {
          // This is a weird callback from Swing.
          // It seems to happen when we are losing focus.
          // We need to convert this string to a ValueWithDisplayString.
          // Also note that the toString() value will be set in the editor and we want the real value.
          object = createFromEditorValue((String)object);
        }
        if (object instanceof ValueWithDisplayString) {
          ValueWithDisplayString value = (ValueWithDisplayString)object;
          ValueWithDisplayString.ValueSelector selector = value.getValueSelector();
          if (selector != null) {
            value = selector.selectValue(myProperty.getValue());
            if (value == null) {
              return;
            }
            String selectedValue = value.getValue();
            ApplicationManager.getApplication().invokeLater(() -> stopEditing(selectedValue));
          }
          value.setUseValueForToString(myDisplayRealValue);
          object = value;
        }
        super.setSelectedItem(object);
      }
    };
    ValueWithDisplayString defaultValue = createFromEditorValue(null);
    newModel.insertElementAt(defaultValue, 0);

    //noinspection unchecked
    myCombo.setModel(newModel);
    myAddedValueIndex = -1; // nothing added
  }

  private void updateModel() {
    ValueWithDisplayString selected = myCombo.getSelectedItem();
    ValueWithDisplayString added = myAddedValueIndex >= 0 ? myCombo.getModel().getElementAt(myAddedValueIndex) : null;
    setModel(myProperty);
    if (added != null) {
      DefaultComboBoxModel<ValueWithDisplayString> model = (DefaultComboBoxModel<ValueWithDisplayString>)myCombo.getModel();
      myAddedValueIndex = findBestInsertionPoint(added);
      model.insertElementAt(added, myAddedValueIndex);
    }
    myCombo.setSelectedItem(selected);
  }

  @Override
  @NotNull
  public NlProperty getProperty() {
    return myProperty;
  }

  private void selectItem(@NotNull ValueWithDisplayString value) {
    DefaultComboBoxModel<ValueWithDisplayString> model = (DefaultComboBoxModel<ValueWithDisplayString>)myCombo.getModel();
    int index = model.getIndexOf(value);
    if (index == -1) {
      if (myAddedValueIndex >= 0 && model.getSize() > myAddedValueIndex) {
        model.removeElementAt(myAddedValueIndex);
      }
      myAddedValueIndex = findBestInsertionPoint(value);
      model.insertElementAt(value, myAddedValueIndex);
    }
    value.setUseValueForToString(myDisplayRealValue);
    if (!value.equals(model.getSelectedItem())) {
      model.setSelectedItem(value);
    }
    myEditor.setText(value.toString());
    myEditor.setForeground(value.getValue() != null ? CHANGED_VALUE_TEXT_COLOR : DEFAULT_VALUE_TEXT_COLOR);
  }

  @TestOnly
  public void selectItem(@Nullable String value) {
    DefaultComboBoxModel<ValueWithDisplayString> model = (DefaultComboBoxModel<ValueWithDisplayString>)myCombo.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      ValueWithDisplayString candidate = model.getElementAt(i);
      if (Objects.equals(candidate.getValue(), value)) {
        model.setSelectedItem(candidate);
        return;
      }
    }
  }

  private int findBestInsertionPoint(@NotNull ValueWithDisplayString newValue) {
    AttributeDefinition definition = myProperty.getDefinition();
    boolean isDimension = definition != null && definition.getFormats().contains(AttributeFormat.DIMENSION);
    int startIndex = 1;
    if (!isDimension) {
      return startIndex;
    }
    String newTextValue = newValue.getDisplayString();
    if (StringUtil.isEmpty(newTextValue)) {
      return startIndex;
    }

    Quantity newQuantity = Quantity.parse(newTextValue);
    if (newQuantity == null) {
      return startIndex;
    }

    ComboBoxModel<ValueWithDisplayString> model = myCombo.getModel();
    for (int index = startIndex, size = model.getSize(); index < size; index++) {
      String textValue = model.getElementAt(index).getValue();
      if (textValue != null) {
        Quantity quantity = Quantity.parse(textValue);
        if (newQuantity.compareTo(quantity) <= 0) {
          return index;
        }
      }
    }
    return model.getSize();
  }

  @Override
  @Nullable
  public Object getValue() {
    ValueWithDisplayString value = myCombo.getSelectedItem();
    if (value == null) {
      return null;
    }
    return value.getValue();
  }

  /**
   * Get the component to display this editor
   */
  @Override
  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  /**
   * Get the component of this editor that will have the focus.
   */
  @NotNull
  public Component getKeySource() {
    return myEditor;
  }

  private void enter() {
    if (!myCombo.isPopupVisible()) {
      ValueWithDisplayString value = createFromEditorValue(myEditor.getText());
      selectItem(value);
      stopEditing(value.getValue());
      myCombo.getEditor().selectAll();
    }
    myCombo.hidePopup();
  }

  private void cancel() {
    String text = myProperty.getValue();
    myCombo.getEditor().setItem(text);
    ValueWithDisplayString value = createFromEditorValue(myProperty.getValue());
    selectItem(value);
    myPopupValueChanged = false;
    cancelEditing();
    myCombo.getEditor().selectAll();
    myCombo.hidePopup();
  }

  @NotNull
  private ValueWithDisplayString createFromEditorValue(@Nullable String editorValue) {
    assert myEnumSupport != null : "EnumSupport should have been setup by setModel";
    return myEnumSupport.createValue(StringUtil.notNullize(editorValue));
  }

  private class EnumRenderer extends ColoredListCellRenderer<ValueWithDisplayString> {
    @Override
    public Component getListCellRendererComponent(JList<? extends ValueWithDisplayString> list,
                                                  ValueWithDisplayString value,
                                                  int index,
                                                  boolean selected,
                                                  boolean hasFocus) {
      if (value == ValueWithDisplayString.SEPARATOR) {
        return new JSeparator();
      }
      return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list, ValueWithDisplayString value, int index, boolean selected, boolean hasFocus) {
      if (value != null) {
        boolean skipDefault = myEnumSupport.customizeCellRenderer(this, value, selected);
        if (skipDefault) {
          return;
        }
        String displayString = value.getDisplayString();
        String actualValue = value.getValue();
        String hint = value.getHint();
        boolean isDefaultValue = myProperty.isDefaultValue(actualValue);
        if (!selected && !isDefaultValue && Objects.equals(actualValue, getValue())) {
          myForeground = CHANGED_VALUE_TEXT_COLOR;
        }
        else if (index == 0 || isDefaultValue) {
          myForeground = DEFAULT_VALUE_TEXT_COLOR;
        }
        if (!StringUtil.isEmpty(displayString)) {
          append(displayString);

          if (!StringUtil.isEmpty(hint)) {
            myForeground = DEFAULT_VALUE_TEXT_COLOR;
            append(" [");
            append(hint);
            append("]");
          }
        }
        else if (actualValue != null) {
          append(actualValue);
        }
      }
    }
  }

  private void comboValuePicked(@NotNull ActionEvent event) {
    if ("comboBoxChanged".equals(event.getActionCommand())) {
      myPopupValueChanged = true;
    }
  }

  private class PopupMenuHandler implements PopupMenuListener {

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
      myPopupValueChanged = false;
      updateModel();
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
      if (myPopupValueChanged) {
        myPopupValueChanged = false;
        ValueWithDisplayString value = (ValueWithDisplayString)myCombo.getModel().getSelectedItem();
        value.setUseValueForToString(myDisplayRealValue);
        stopEditing(value.getValue());
      }
    }

    @Override
    public void popupMenuCanceled(PopupMenuEvent event) {
      myPopupValueChanged = false;
    }
  }

  public static class CustomComboBox extends ComboBox {
    private JPanel myBorderPanel;
    private boolean myUseDarculaUI;

    public CustomComboBox() {
      setBackground(JBColor.WHITE);
      setBorders();
    }

    public void setBorderPanel(@NotNull JPanel borderPanel) {
      myBorderPanel = borderPanel;
      setBorders();
    }

    @Override
    public ComboBoxModel<ValueWithDisplayString> getModel() {
      //noinspection unchecked
      return super.getModel();
    }

    @Override
    public ValueWithDisplayString getSelectedItem() {
      return (ValueWithDisplayString)super.getSelectedItem();
    }

    private void setBorders() {
      int horizontalSpacing = HORIZONTAL_SPACING + (myUseDarculaUI ? 0 : 1);
      if (myBorderPanel != null) {
        myBorderPanel.setBorder(JBUI.Borders.empty(VERTICAL_SPACING, horizontalSpacing, VERTICAL_SPACING, 0));
      }
    }

    @Override
    public void setUI(ComboBoxUI ui) {
      // Only check for WindowsComboBoxUI when on Windows
      if (SystemUtils.IS_OS_WINDOWS) {
        myUseDarculaUI = !(ui instanceof com.sun.java.swing.plaf.windows.WindowsComboBoxUI) && !ApplicationManager.getApplication().isUnitTestMode();
        if (myUseDarculaUI) {
          // There are multiple reasons for hardcoding the ComboBoxUI here:
          // 1) Some LAF will draw a beveled border which does not look good in the table grid.
          // 2) In the inspector we would like the reference editor and the combo boxes to have a similar width.
          //    This is very hard unless you can control the UI.
          // Note: forcing the Darcula UI does not imply dark colors.
          ui = new CustomDarculaComboBoxUI(this);
        }
        super.setUI(ui);
        setBorders();
      }
    }
  }

  private static class CustomDarculaComboBoxUI extends DarculaComboBoxUI {

    public CustomDarculaComboBoxUI(@NotNull JComboBox comboBox) {
    }

    @Override
    protected Insets getInsets() {
      // Minimize the vertical padding used in the UI
      return JBUI.insets(VERTICAL_PADDING, HORIZONTAL_ENUM_PADDING, VERTICAL_PADDING, 4).asUIResource();
    }

    @Override
    @NotNull
    protected Color getArrowButtonFillColor(@NotNull Color defaultColor) {
      // Use a lighter gray for the IntelliJ LAF. Darcula remains what is was.
      return JBColor.LIGHT_GRAY;
    }

    @Override protected void configureEditor() {
      super.configureEditor();

      if (editor instanceof JTextField) {
        JTextField tf = (JTextField)editor;
        tf.setUI((TextUI)DarculaTextFieldUI.createUI(tf));
      }
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      if (!JBColor.isBright()) {
        super.paintBorder(c, g, x, y, width, height);
        return;
      }

      Color panelBefore = UIManager.getColor("Panel.background");
      Color comboBefore = UIManager.getColor("ComboBox.background");
      try {
        // DarculaComboBoxUI picks up the background for the renderer from the combobox background, but for noneditable controls gets the
        // overall background (exposed behind the edges of the renderer) comes from the default panel background.
        // Temporarily set the panel background to be the same as the component background here.
        UIManager.getLookAndFeelDefaults().put("Panel.background", c.getBackground());
        UIManager.getLookAndFeelDefaults().put("ComboBox.background", c.getBackground());
        super.paintBorder(c, g, x, y, width, height);
      }
      finally {
        UIManager.getLookAndFeelDefaults().put("Panel.background", panelBefore);
        UIManager.getLookAndFeelDefaults().put("ComboBox.background", comboBefore);
      }
    }

  }
}
