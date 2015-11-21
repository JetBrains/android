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
package com.android.tools.idea.wizard.template;

import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.ui.ApiComboBoxItem;
import com.android.tools.idea.wizard.dynamic.AndroidStudioWizardStep;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithHeaderAndDescription;
import com.android.tools.idea.templates.StringEvaluator;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
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
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.android.SdkConstants.ATTR_ID;
import static com.android.tools.idea.templates.Template.ATTR_DEFAULT;
import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * TemplateWizardStep is the base class for step pages in Freemarker template-based wizards.
 *
 * Deprecated. Use {@link DynamicWizardStepWithHeaderAndDescription} instead.
 */
@Deprecated
public abstract class TemplateWizardStep extends ModuleWizardStep
  implements ActionListener, FocusListener, DocumentListener, ChangeListener, AndroidStudioWizardStep {
  private static final Logger LOG = Logger.getInstance("#" + TemplateWizardStep.class.getName());

  protected static final String LAST_USED_CLASS_PREFIX_KEY = "LAST_USED_CLASS_PREFIX";

  protected final TemplateWizardState myTemplateState;
  protected final BiMap<String, JComponent> myParamFields = HashBiMap.create();
  protected final Map<String, JLabel> myParamFieldLabels = Maps.newHashMap();
  protected final Map<JRadioButton, Pair<String, Object>> myRadioButtonValues = Maps.newHashMap();
  protected final Map<Parameter, ApiComboBoxItem> myComboBoxValues = Maps.newHashMap();
  protected final Project myProject;
  protected final Module myModule;
  private final Icon mySidePanelIcon;
  protected boolean myIgnoreUpdates = false;
  protected boolean myFirstUpdate = true;
  protected boolean myIsValid = true;
  protected boolean myVisible = true;
  protected final UpdateListener myUpdateListener;
  private final StringEvaluator myStringEvaluator = new StringEvaluator();

  // Ids for params that have been edited in this round of updating/validation
  protected Queue<String> myIdsWithNewValues = new ConcurrentLinkedQueue<String>();

  public interface UpdateListener {
    public void update();
  }

  public static final UpdateListener NONE = new UpdateListener() {
    @Override
    public void update() {}
  };

  public TemplateWizardStep(@NotNull TemplateWizardState state, @Nullable Project project, @Nullable Module module,
                            @Nullable Icon sidePanelIcon, @Nullable UpdateListener updateListener) {
    myTemplateState = state;
    myProject = project;
    myModule = module;
    mySidePanelIcon = sidePanelIcon;
    myUpdateListener = updateListener;
  }

  @Override
  public void _init() {
    super._init();
    update();
  }

  /** Override this to return a {@link JLabel} that holds the description of the UI element that has focus. */
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

  /** Override this to return a {@link JLabel} that displays a validation error message. */
  @NotNull protected abstract JLabel getError();

  /** Wraps the given string in &lt;html&gt; and &lt;/html&gt; tags and sets it into the error label. */
  public void setErrorHtml(@Nullable String s) {
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
    if (getComponent() != null) {
      getComponent().revalidate();
    }
  }

  /** Override this to provide help text for a parameter that does not have its own help text in the template. */
  @Nullable
  protected String getHelpText(@NotNull String param) {
    return null;
  }

  /** Returns true if the data in this wizard step is correct and the user is permitted to move to the next step. */
  @Override
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

  /**
   * Called by update to write the new values of the parameters being edited into the template model.
   */
  public void updateParams() {
    if (!myVisible) {
      return;
    }

    Template.convertApisToInt(myTemplateState.getParameters());

    Component focusedComponent = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    setDescriptionHtml("");
    setErrorHtml("");
    for (String paramName : myParamFields.keySet()) {
      if (myTemplateState.myHidden.contains(paramName)) {
        continue;
      }
      Parameter param = myTemplateState.hasTemplate() ? myTemplateState.getTemplateMetadata().getParameter(paramName) : null;
      Object oldValue = myTemplateState.get(paramName);
      JComponent component = myParamFields.get(paramName);
      if (component == focusedComponent || component.isAncestorOf(focusedComponent)) {
        String help = param != null && param.help != null && param.help.length() > 0 ? param.help : getHelpText(paramName);
        setDescriptionHtml(help);
      }
      Object newValue = getComponentValue(param, component);

      if (newValue != null && !newValue.equals(oldValue)) {
        myTemplateState.put(paramName, newValue);
        if (oldValue != null) {
          myTemplateState.myModified.add(paramName);
        }
        if (!myIdsWithNewValues.contains(paramName)) {
          myIdsWithNewValues.add(paramName);
        }
      }
    }
    for (Map.Entry<JRadioButton, Pair<String, Object>> entry : myRadioButtonValues.entrySet()) {
      if (entry.getKey().isSelected()) {
        Pair<String, Object> value = entry.getValue();
        myTemplateState.put(value.getFirst(), value.getSecond());
      }
    }
  }

  /**
   * Called by update() to write derived values to the template model.
   */
  protected void deriveValues() {
    TemplateMetadata metadata = myTemplateState.getTemplateMetadata();
    if (metadata == null) {
      return;
    }

    for (String changedParamId : myIdsWithNewValues) {
      for (String paramName : myParamFields.keySet()) {
        Parameter param = myTemplateState.hasTemplate() ? metadata.getParameter(paramName) : null;

        // If this parameter is null or doesn't have anything to update (both visibility and suggestion are null or empty), skip it.
        if (param == null ||
            ((param.suggest == null || param.suggest.isEmpty()) && (param.visibility == null || param.visibility.isEmpty()))) {
          continue;
        }

        // If this parameter has dynamic visibility, calculate it and process accordingly
        if (param.visibility != null && param.visibility.contains(changedParamId)) {
          updateVisibility(param);
        }

        // Don't process hidden fields
        if (myTemplateState.myHidden.contains(paramName)) {
          continue;
        }

        // If this parameter has a suggestion depending on the changed parameter, calculate it, record the new value
        // and add it for consideration so that things dependent on it can be updated
        if (param.suggest != null && param.suggest.contains(changedParamId)) {
          final String updated = myStringEvaluator.evaluate(param.suggest, myTemplateState.getParameters());
          if (updated != null && !updated.equals(myTemplateState.get(param.id))) {
            myIdsWithNewValues.add(param.id);

            updateDerivedValue(param.id, (JTextField)myParamFields.get(param.id), new Callable<String>() {
              @Override
              public String call() throws Exception {
                return updated;
              }
            });
          }
        }
      }
    }
  }

  protected void updateVisibility(Parameter param) {
    if (param.visibility != null && !param.visibility.isEmpty()) {
      boolean visible = myStringEvaluator.evaluateBooleanExpression(param.visibility, myTemplateState.getParameters(), true);
      if (visible) {
        myTemplateState.myHidden.remove(param.id);
        // Update the newly shown parameter if necessary
        if (param.suggest != null) {
          final String updated = myStringEvaluator.evaluate(param.suggest, myTemplateState.getParameters());
          if (updated != null && !updated.equals(myTemplateState.get(param.id))) {
            updateDerivedValue(param.id, (JTextField)myParamFields.get(param.id), new Callable<String>() {
              @Override
              public String call() throws Exception {
                return updated;
              }
            });
          }
        }
      }
      else {
        myTemplateState.myHidden.add(param.id);
      }
      myParamFields.get(param.id).setVisible(visible);
      if (myParamFieldLabels.containsKey(param.id)) {
        myParamFieldLabels.get(param.id).setVisible(visible);
      }
    }
  }

  @Override
  public boolean validate() {
    Template.convertApisToInt(myTemplateState.getParameters());
    if (!myVisible) {
      return true;
    }
    Integer minApi = (Integer)myTemplateState.get(ATTR_MIN_API_LEVEL);
    Integer buildApi = (Integer)myTemplateState.get(ATTR_BUILD_API);

    for (String paramName : myParamFields.keySet()) {
      if (myTemplateState.myHidden.contains(paramName)) {
        continue;
      }
      Parameter param = myTemplateState.hasTemplate() ? myTemplateState.getTemplateMetadata().getParameter(paramName) : null;
      if (param != null) {
        Set<Object> relatedValues = Sets.newHashSet();
        for (Parameter related : myTemplateState.getTemplateMetadata().getRelatedParams(param)) {
          relatedValues.add(myTemplateState.get(related.id));
        }
        String error = param.validate(myProject, myModule, myTemplateState.getSourceProvider(),
                                      (String)myTemplateState.get(ATTR_PACKAGE_NAME), myTemplateState.get(paramName), relatedValues);
        if (error != null) {
          setErrorHtml(error);
          return false;
        }

        // Check to see that the selection's constraints are met if this is a combo box
        if (myComboBoxValues.containsKey(param)) {
          ApiComboBoxItem selectedItem = myComboBoxValues.get(param);

          if (selectedItem == null) {
            return false;
          }
          if (minApi == null || buildApi == null) {
            return false;
          }

          String message = selectedItem.validate(minApi, buildApi);
          if (message != null) {
            setErrorHtml(message);
            return false;
          }
        }
      }
    }

    return true;
  }

  public void refreshUiFromParameters() {
    if (myTemplateState.myTemplate == null) {
      return;
    }

    for (Parameter param : myTemplateState.myTemplate.getMetadata().getParameters()) {
      if (param.initial != null && !myTemplateState.myModified.contains(param.id)) {
        myTemplateState.myParameters.remove(param.id);
      }
    }
    myTemplateState.setParameterDefaults();
    Template.convertApisToInt(myTemplateState.getParameters());
    boolean oldIgnoreUpdates = myIgnoreUpdates;
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
            if (((ApiComboBoxItem)((JComboBox)component).getItemAt(i)).getData().equals(value)) {
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
      myIgnoreUpdates = oldIgnoreUpdates;
    }
  }

  /**
   * Retrieve a value from the given JComponent
   */
  @Nullable
  protected Object getComponentValue(Parameter param, JComponent component) {
    Object newValue = null;
    if (component instanceof JCheckBox) {
      newValue = ((JCheckBox)component).isSelected();
    }
    else if (component instanceof JComboBox) {
      ApiComboBoxItem selectedItem = (ApiComboBoxItem)((JComboBox)component).getSelectedItem();
      myComboBoxValues.put(param, selectedItem);

      if (selectedItem != null) {
        newValue = selectedItem.getData();
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
    return newValue;
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
      comboBox.addItem(new ApiComboBoxItem(optionId, optionLabel, minSdk, minBuildApi));
      String isDefault = option.getAttribute(ATTR_DEFAULT);
      if (isDefault != null && !isDefault.isEmpty() && Boolean.valueOf(isDefault)) {
        comboBox.setSelectedIndex(comboBox.getItemCount() - 1);
      }
    }
  }

  /**
   * Takes a {@link JComboBox} instance and a {@Enum} and
   * populates the combo box with all possible values of the enumerated type.
   */
  protected static <E extends Enum<E>> void populateComboBox(@NotNull JComboBox comboBox, @NotNull Class<E> enumClass) {
    for (Enum<E> e : enumClass.getEnumConstants()) {
      comboBox.addItem(new ApiComboBoxItem(e.name(), e.toString(), 1, 1));
    }
  }

  /**
   * Takes a {@link JComboBox} instance and an array and
   * populates the combo box with the values in the array.
   * Similar to the {@link DefaultComboBoxModel}, but uses our ApiComboBoxItem.
   */
  protected static void populateComboBox(@NotNull JComboBox comboBox, @NotNull Object[] array) {
    for (int i = 0; i < array.length; ++i) {
      comboBox.addItem(new ApiComboBoxItem(i, array[i].toString(), 1, 1));
    }
  }

  /**
   * Connects the given {@link JLabel} to the given parameter and sets a listener to pick up changes to visibility that need to
   * trigger UI updates.
   */
  protected void registerLabel(@NotNull String paramName, @NotNull JLabel label) {
    myParamFieldLabels.put(paramName, label);
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
    myParamFields.put(paramName, comboBox);
    Object value = myTemplateState.get(paramName);
    if (value != null) {
      for (int i = 0; i < comboBox.getItemCount(); i++) {
        Object item = comboBox.getItemAt(i);
        if (!(item instanceof ApiComboBoxItem)) {
          continue;
        }
        if (((ApiComboBoxItem)item).getData().equals(value)) {
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
    myParamFields.put(paramName, textField);
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
    field.getTextField().addFocusListener(this);
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

    myIgnoreUpdates = true;

    // Create a list of parameters that have been modified this round
    myIdsWithNewValues = new ConcurrentLinkedQueue<String>();

    // First we load the updated values into our model
    updateParams();

    // The first time update() is called, refresh the "suggest" attrs by ensuring *all* parameters are set.
    if (myFirstUpdate) {
      myIdsWithNewValues = new ConcurrentLinkedQueue<String>(myTemplateState.getParameters().keySet());
    }

    // Then we calculate any values that we need to
    deriveValues();
    // Finally we make sure these new values are valid
    myIsValid = validate();
    if (myUpdateListener != null) {
      myUpdateListener.update();
    }

    myFirstUpdate = false;
    myIgnoreUpdates = false;
  }

  protected boolean updateDerivedValue(@NotNull String attrName, @NotNull JTextField textField, @NotNull Callable<String> valueDeriver) {
    boolean updated = false;
    try {
      myIgnoreUpdates = true;
      if (!myTemplateState.myModified.contains(attrName)) {
        String s = valueDeriver.call();
        if (s != null && !s.equals(myTemplateState.get(attrName))) {
          myTemplateState.put(attrName, s);
          textField.setText(s);
          myTemplateState.myModified.remove(attrName);
          updated = true;
        }
      }
    }
    catch (Exception e) {
    }
    finally {
      myIgnoreUpdates = false;
    }
    return updated;
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
