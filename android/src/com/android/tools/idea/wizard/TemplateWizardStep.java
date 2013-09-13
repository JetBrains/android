/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.ATTR_ID;
import static com.android.tools.idea.templates.Template.ATTR_DEFAULT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MIN_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_PACKAGE_NAME;

/**
 * TemplateWizardStep is the base class for step pages in Freemarker template-based wizards.
 */
public abstract class TemplateWizardStep extends ModuleWizardStep
  implements ActionListener, FocusListener, DocumentListener, ChangeListener {
  private static final Logger LOG = Logger.getInstance("#" + TemplateWizardStep.class.getName());

  protected final TemplateWizardState myTemplateState;
  protected final BiMap<String, JComponent> myParamFields = HashBiMap.create();
  protected final Map<JRadioButton, Pair<String, Object>> myRadioButtonValues = Maps.newHashMap();
  private final Project myProject;
  private final Icon mySidePanelIcon;
  protected boolean myIgnoreUpdates = false;
  protected boolean myIsValid = true;
  protected boolean myVisible = true;
  protected final UpdateListener myUpdateListener;

  public interface UpdateListener {
    public void update();
  }

  public TemplateWizardStep(@NotNull TemplateWizardState state, @Nullable Project project, @Nullable Icon sidePanelIcon,
                            UpdateListener updateListener) {
    myTemplateState = state;
    myProject = project;
    mySidePanelIcon = sidePanelIcon;
    myUpdateListener = updateListener;
  }

  @Override
  public void _init() {
    super._init();
    update();
  }

  /** Override this to return a {@link JTextArea} that holds the description of the UI element that has focus. */
  @NotNull protected abstract JLabel getDescription();

  /** Wraps the given string in &lt;html&gt; and &lt;/html&gt; tags and sets it into the description label. */
  protected void setDescriptionHtml(@Nullable String s) {
    if (s == null) {
      s = "";
    }
    if (!s.startsWith("<html>")) {
      s = "<html>" + s + "</html>";
      s.replaceAll("\n", "<br>");
    }
    JLabel label = getDescription();
    if (label != null) {
      label.setText(s);
      growLabelIfNecessary(label);
    }
  }

  /** Override this to return a {@link JTextArea} that displays a validation error message. */
  @NotNull protected abstract JLabel getError();

  /** Wraps the given string in &lt;html&gt; and &lt;/html&gt; tags and sets it into the error label. */
  protected void setErrorHtml(@Nullable String s) {
    if (s == null) {
      s = "";
    }
    if (!s.startsWith("<html>")) {
      s = "<html><font color='#" + ColorUtil.toHex(JBColor.RED) + "'>" + XmlUtils.toXmlTextValue(s) + "</font></html>";
      s.replaceAll("\n", "<br>");
    }
    JLabel label = getError();
    if (label != null) {
      label.setText(s);
      growLabelIfNecessary(label);
    }
  }

  /**
   * Increases the given label's vertical size if necessary to accommodate the amount of text it currently displays. If you are using
   * IntelliJ's {@link GridLayoutManager}, then the minimum, maximum, and preferred sizes for the label component must be unspecified in
   * the layout, or the layout manager will use those and override the component-level sizes that this method adjusts.
   */
  protected void growLabelIfNecessary(JLabel label) {
    Dimension newSize = label.getMinimumSize();
    label.setPreferredSize(null);
    label.validate();
    Dimension pd = label.getPreferredSize();
    int currentWidth = label.getSize().width;
    int preferredHeight = 0;
    if (currentWidth != 0 && pd.width != 0 && pd.height != 0) {
      preferredHeight = pd.height * (int) ((float)pd.width / (float)currentWidth);
      if (currentWidth % pd.width != 0) {
        preferredHeight += pd.height;
      }
    }
    newSize.height = Math.max(newSize.height, preferredHeight);
    label.setMinimumSize(newSize);
    label.setPreferredSize(newSize);
    getComponent().revalidate();
  }

  /** Override this to provide help text for a parameter that does not have its own help text in the template. */
  @Nullable
  protected String getHelpText(@NotNull String param) {
    return null;
  }

  /** Returns true if the data in this wizard step is correct and the user is permitted to move to the next step. */
  public boolean isValid() {
    return myIsValid;
  }

  public void setVisible(boolean visible) {
    myVisible = visible;
  }

  @Override
  public boolean isStepVisible() {
    return myVisible;
  }

  @Override
  public boolean validate() {
    myTemplateState.convertApisToInt();
    if (!myVisible) {
      return true;
    }
    JComponent focusedComponent = (JComponent)KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    Integer minApi = (Integer)myTemplateState.get(ATTR_MIN_API);
    Integer buildApi = (Integer)myTemplateState.get(ATTR_BUILD_API);
    setDescriptionHtml("");
    setErrorHtml("");
    for (String paramName : myParamFields.keySet()) {
      if (myTemplateState.myHidden.contains(paramName)) {
        continue;
      }
      Parameter param = myTemplateState.hasTemplate() ? myTemplateState.getTemplateMetadata().getParameter(paramName) : null;
      Object oldValue = myTemplateState.get(paramName);
      JComponent component = myParamFields.get(paramName);
      if (component == focusedComponent) {
        String help = param != null && param.help != null && param.help.length() > 0 ? param.help : getHelpText(paramName);
        setDescriptionHtml(help);
      }
      Object newValue = null;
      if (component instanceof JCheckBox) {
        newValue = ((JCheckBox)component).isSelected();
      }
      else if (component instanceof JComboBox) {
        ComboBoxItem selectedItem = (ComboBoxItem)((JComboBox)component).getSelectedItem();
        if (selectedItem != null) {
          newValue = selectedItem.id;
        }
        if (minApi != null && selectedItem.minApi > minApi) {
          setErrorHtml(String.format("The \"%s\" option for %s requires a minimum API level of %d", selectedItem.label, param.name,
                                     selectedItem.minApi));
          return false;
        }
        if (buildApi != null && selectedItem.minBuildApi > buildApi) {
          setErrorHtml(String.format("The \"%s\" option for %s requires a minimum API level of %d", selectedItem.label, param.name,
                                     selectedItem.minBuildApi));
          return false;
        }
      }
      else if (component instanceof JTextField) {
        newValue = ((JTextField)component).getText();
      } else if (component instanceof TextFieldWithBrowseButton) {
        newValue = ((TextFieldWithBrowseButton)component).getText();
      } else if (component instanceof JSlider) {
        newValue = ((JSlider)component).getValue();
      } else if (component instanceof JSpinner) {
        newValue = ((JSpinner)component).getValue();
      } else if (component instanceof ColorPanel) {
        newValue = ((ColorPanel)component).getSelectedColor();
      }
      if (newValue != null && !newValue.equals(oldValue)) {
        myTemplateState.put(paramName, newValue);
        myTemplateState.myModified.add(paramName);
      }

      if (param != null) {
        String error = param.validate(myProject, (String)myTemplateState.get(ATTR_PACKAGE_NAME), newValue);
        if (error != null) {
          setErrorHtml(error);
          return false;
        }
      }
    }
    for (Map.Entry<JRadioButton, Pair<String, Object>> entry : myRadioButtonValues.entrySet()) {
      if (entry.getKey().isSelected()) {
        Pair<String, Object> value = entry.getValue();
        myTemplateState.put(value.getFirst(), value.getSecond());
      }
    }
    return true;
  }

  public void refreshUiFromParameters() {
    myTemplateState.myModified.clear();

    for (Parameter param : myTemplateState.myTemplate.getMetadata().getParameters()) {
      if (param.initial != null) {
        myTemplateState.myParameters.remove(param.id);
      }
    }
    myTemplateState.setParameterDefaults();
    try {
      myIgnoreUpdates = true;
      for (String paramName : myParamFields.keySet()) {
        if (myTemplateState.myHidden.contains(paramName)) {
          continue;
        }
        JComponent component = myParamFields.get(paramName);
        Object value = myTemplateState.get(paramName);
        if (value == null) {
          continue;
        }
        if (component instanceof JCheckBox) {
          ((JCheckBox)component).setSelected(Boolean.parseBoolean(value.toString()));
        }
        else if (component instanceof JComboBox) {
          for (int i = 0; i < ((JComboBox)component).getItemCount(); i++) {
            if (((ComboBoxItem)((JComboBox)component).getItemAt(i)).id.equals(value)) {
              ((JComboBox)component).setSelectedIndex(i);
              break;
            }
          }
        }
        else if (component instanceof JTextField) {
          ((JTextField)component).setText(value.toString());
        } else if (component instanceof TextFieldWithBrowseButton) {
          ((TextFieldWithBrowseButton)component).setText(value.toString());
        } else if (component instanceof JSlider) {
          ((JSlider)component).setValue(Integer.parseInt(value.toString()));
        } else if (component instanceof JSpinner) {
          ((JSpinner)component).setValue(Integer.parseInt(value.toString()));
        } else if (component instanceof ColorPanel) {
          ((ColorPanel)component).setSelectedColor((Color)value);
        }
      }
    } finally {
      myIgnoreUpdates = false;
    }
    update();
  }

  /**
   * Takes a {@link JComboBox} instance and a {@Parameter} that represents an enumerated type and
   * populates the combo box with all possible values of the enumerated type.
   */
  protected static void populateComboBox(@NotNull JComboBox comboBox, @NotNull Parameter parameter) {
    List<Element> options = parameter.getOptions();
    assert !options.isEmpty();
    for (int i = 0, n = options.size(); i < n; i++) {
      Element option = options.get(i);
      String optionId = option.getAttribute(ATTR_ID);
      assert optionId != null && !optionId.isEmpty() : ATTR_ID;
      NodeList childNodes = option.getChildNodes();
      assert childNodes.getLength() == 1 && childNodes.item(0).getNodeType() == Node.TEXT_NODE;
      String optionLabel = childNodes.item(0).getNodeValue().trim();

      int minSdk = 1;
      try { minSdk = Integer.parseInt(option.getAttribute(TemplateMetadata.ATTR_MIN_API)); } catch (Exception e) { }
      int minBuildApi = 1;
      try { minBuildApi = Integer.parseInt(option.getAttribute(TemplateMetadata.ATTR_MIN_BUILD_API)); } catch (Exception e) { }
      comboBox.addItem(new ComboBoxItem(optionId, optionLabel, minSdk, minBuildApi));
      String isDefault = option.getAttribute(ATTR_DEFAULT);
      if (isDefault != null && !isDefault.isEmpty() && Boolean.valueOf(isDefault)) {
        comboBox.setSelectedIndex(comboBox.getItemCount() - 1);
      }
    }
  }

  /**
   * Connects the given {@link JCheckBox} to the given parameter and sets a listener to pick up changes that need to trigger validation
   * and UI updates.
   */
  protected void register(@NotNull String paramName, @NotNull JCheckBox checkBox) {
    myParamFields.put(paramName, (JComponent)checkBox);
    Object value = myTemplateState.get(paramName);
    if (value != null) {
      checkBox.setSelected(Boolean.parseBoolean(value.toString()));
    } else {
      myTemplateState.put(paramName, false);
    }
    checkBox.addFocusListener(this);
    checkBox.addActionListener(this);
  }

  /**
   * Connects the given {@link JComboBox} to the given parameter and sets a listener to pick up changes that need to trigger validation
   * and UI updates.
   */
  protected void register(@NotNull String paramName, @NotNull JComboBox comboBox) {
    myParamFields.put(paramName, (JComponent)comboBox);
    Object value = myTemplateState.get(paramName);
    if (value != null) {
      for (int i = 0; i < comboBox.getItemCount(); i++) {
        Object item = comboBox.getItemAt(i);
        if (!(item instanceof ComboBoxItem)) {
          continue;
        }
        if (((ComboBoxItem)item).id.equals(value)) {
          comboBox.setSelectedIndex(i);
          break;
        }
      }
    }
    comboBox.addFocusListener(this);
    comboBox.addActionListener(this);
  }

  /**
   * Connects the given {@link JTextField} to the given parameter and sets a listener to pick up changes that need to trigger validation
   * and UI updates.
   */
  protected void register(@NotNull String paramName, @NotNull JTextField textField) {
    String value = (String)myTemplateState.get(paramName);
    if (value != null) {
      textField.setText(value);
    } else {
      myTemplateState.put(paramName, "");
    }
    myParamFields.put(paramName, (JComponent)textField);
    textField.addFocusListener(this);
    textField.getDocument().addDocumentListener(this);
  }

  protected void register(@NotNull String paramName, @NotNull JRadioButton radioButton, @Nullable Object value) {
    Object currentValue = myTemplateState.get(paramName);
    radioButton.setSelected(currentValue != null && currentValue.equals(value));
    if (value != null) {
      myRadioButtonValues.put(radioButton, Pair.of(paramName, value));
    }
    radioButton.addFocusListener(this);
    radioButton.addActionListener(this);
  }

  protected void register(@NotNull String paramName, @NotNull JSlider paddingSlider) {
    Integer value = (Integer)myTemplateState.get(paramName);
    if (value != null) {
      paddingSlider.setValue(value);
    } else {
      myTemplateState.put(paramName, paddingSlider.getValue());
    }
    myParamFields.put(paramName, (JComponent)paddingSlider);
    paddingSlider.addFocusListener(this);
    paddingSlider.addChangeListener(this);
  }

  protected void register(@NotNull String paramName, @NotNull JSpinner spinner) {
    Integer value = (Integer)myTemplateState.get(paramName);
    if (value != null) {
      spinner.setValue(value);
    } else {
      myTemplateState.put(paramName, spinner.getValue());
    }
    myParamFields.put(paramName, (JComponent)spinner);
    spinner.addFocusListener(this);
    spinner.addChangeListener(this);
  }

  protected void register(@NotNull String paramName, @NotNull final TextFieldWithBrowseButton field) {
    String value = (String)myTemplateState.get(paramName);
    if (value != null) {
      field.setText(value);
    } else {
      myTemplateState.put(paramName, field.getText());
    }
    myParamFields.put(paramName, (JComponent)field);
    field.addFocusListener(this);
    field.getTextField().getDocument().addDocumentListener(this);
  }

  protected void register(@NotNull String paramName, @NotNull ColorPanel colorPanel) {
    Color value = (Color)myTemplateState.get(paramName);
    if (value != null) {
      colorPanel.setSelectedColor(value);
    } else {
      myTemplateState.put(paramName, colorPanel.getSelectedColor());
    }
    myParamFields.put(paramName, (JComponent)colorPanel);
    colorPanel.addFocusListener(this);
    colorPanel.addActionListener(this);
  }

  /** Revalidates the UI and asks the parent wizard to update its state to reflect changes. */
  protected void update() {
    if (myIgnoreUpdates) {
      return;
    }

    myIsValid = validate();
    if (myUpdateListener != null) {
      myUpdateListener.update();
    }
  }

  @Override
  public JComponent getComponent() {
    return null;
  }

  @Override
  public Icon getIcon() {
    return mySidePanelIcon;
  }

  @Override
  public void updateDataModel() {
  }

  @Override
  public void actionPerformed(@NotNull ActionEvent actionEvent) {
    update();
  }

  @Override
  public void focusGained(@NotNull FocusEvent focusEvent) {
    update();
  }

  @Override
  public void focusLost(@NotNull FocusEvent focusEvent) {
    update();
  }

  @Override
  public void insertUpdate(@NotNull DocumentEvent documentEvent) {
    update();
  }

  @Override
  public void removeUpdate(@NotNull DocumentEvent documentEvent) {
    update();
  }

  @Override
  public void changedUpdate(@NotNull DocumentEvent documentEvent) {
    update();
  }

  @Override
  public void stateChanged(@NotNull ChangeEvent e) {
    update();
  }

  protected void hide(JComponent... components) {
    for (JComponent component : components) {
      component.setVisible(false);
    }
  }

  protected void show(JComponent... components) {
    for (JComponent component : components) {
      component.setVisible(true);
    }
  }
}
