/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property2;

import static com.android.tools.adtui.model.stdui.EditingSupportKt.EDITOR_NO_ERROR;
import static com.android.tools.adtui.stdui.CommonTextFieldKt.OUTLINE_PROPERTY;
import static com.android.tools.idea.uibuilder.handlers.motion.property2.MotionLayoutPropertyProvider.mapFromCustomType;

import com.android.tools.adtui.model.stdui.CommonTextFieldModel;
import com.android.tools.adtui.model.stdui.EditingErrorCategory;
import com.android.tools.adtui.model.stdui.EditingSupport;
import com.android.tools.adtui.model.stdui.ValueChangedListener;
import com.android.tools.adtui.stdui.CommonTextField;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes;
import com.android.tools.idea.uibuilder.property2.NelePropertyType;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.EditorComboBox;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import kotlin.Pair;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewCustomAttributePanel extends DialogWrapper {
  private JTextField myAttributeNameEditor;
  private JTextField myInitialValueEditor;
  private JPanel myContentPanel;
  private EditorComboBox myDataType;
  private ValidatorPanel myValidatorPanel;
  private JCheckBox myAcceptAnyway;
  @SuppressWarnings("unused")
  private JPanel myErrorPanel;
  private AttributeNameModel myNewAttributeNameModel;
  private final DefaultComboBoxModel<CustomAttributeType> myModel;
  private final NlComponent myComponent;
  private final StringValueProperty myNewAttributeName = new StringValueProperty();
  private final StringValueProperty myNewAttributeValue = new StringValueProperty();
  private String myLastLookupType;
  private Set<String> myLastLookup = Collections.emptySet();

  public NewCustomAttributePanel(@NotNull NlComponent component) {
    super(false);
    myModel = new DefaultComboBoxModel<>();
    myComponent = component;
    Arrays.stream(CustomAttributeType.values()).forEach(type -> myModel.addElement(type));
    myModel.setSelectedItem(CustomAttributeType.CUSTOM_STRING);
    //noinspection unchecked
    myDataType.setModel(myModel);
    myDataType.setEditable(false);
    getLookup();
    bindComponents();
    addValidators();
    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myAttributeNameEditor;
  }

  @NotNull
  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  @NotNull
  public String getAttributeName() {
    return myAttributeNameEditor.getText();
  }

  @NotNull
  public String getInitialValue() {
    return myInitialValueEditor.getText();
  }

  @Nullable
  public CustomAttributeType getType() {
    return (CustomAttributeType)myDataType.getSelectedItem();
  }

  private void bindComponents() {
    BindingsManager bindings = new BindingsManager();
    bindings.bindTwoWay(new TextProperty(myAttributeNameEditor), myNewAttributeName);
    bindings.bindTwoWay(new TextProperty(myInitialValueEditor), myNewAttributeValue);
    myDataType.addActionListener(event -> myValidatorPanel.validateAll());
    myAcceptAnyway.addActionListener(event -> myValidatorPanel.validateAll());
  }

  private void addValidators() {
    myValidatorPanel.registerValidator(myNewAttributeName, this::checkAttributeName);
    myValidatorPanel.registerValidator(myNewAttributeValue, this::checkAttributeValue);
    myValidatorPanel.registerValidator(myValidatorPanel.hasErrors(), this::updateOkButton);
    myValidatorPanel.registerValidator(myValidatorPanel.isOK(), this::updateAcceptAnyway);
    myDataType.addActionListener(event -> myValidatorPanel.revalidate());
    myAcceptAnyway.addActionListener(event -> myValidatorPanel.revalidate());
  }

  @NotNull
  private Validator.Result checkAttributeName(@NotNull String attributeName) {
    if (attributeName.isEmpty()) {
      return new Validator.Result(Validator.Severity.ERROR,
                                  "Please supply an attribute name");
    }
    Set<String> lookup = getLookup();
    String message = lookup.contains(attributeName) ? null : "Method not found: " + getMethodName(attributeName) + ";  check arguments";
    return createResult(myAttributeNameEditor, message);
  }

  @NotNull
  private Validator.Result checkAttributeValue(@SuppressWarnings("unused") @NotNull String value) {
    CustomAttributeType customType = getType();
    String tagName = customType != null ? customType.getTagName() : CustomAttributeType.CUSTOM_STRING.getTagName();
    NelePropertyType type = mapFromCustomType(tagName);
    String message = type.validateLiteral(value);
    return createResult(myInitialValueEditor, message);
  }

  @NotNull
  private Validator.Severity getErrorSeverity() {
    return myAcceptAnyway.isSelected() ? Validator.Severity.WARNING : Validator.Severity.ERROR;
  }

  @NotNull
  private Validator.Result createResult(@NotNull JComponent component, @Nullable String message) {
    String outline = message == null ? null : myAcceptAnyway.isSelected() ? "warning" : "error";
    component.putClientProperty(OUTLINE_PROPERTY, outline);
    component.repaint();
    return message == null ? Validator.Result.OK : new Validator.Result(getErrorSeverity(), message);
  }

  @NotNull
  private static String getMethodName(@NotNull String attributeName) {
    return "set" + Character.toUpperCase(attributeName.charAt(0)) + attributeName.substring(1);
  }

  @NotNull
  private Set<String> getLookup() {
    CustomAttributeType type = getType();
    String tagName = type != null ? type.getTagName() : null;
    if (!Objects.equals(tagName, myLastLookupType)) {
      myLastLookup = MotionAttributes.getCustomAttributesFor(myComponent, tagName);
      myLastLookupType = tagName;
      List<String> completions = new ArrayList<>(myLastLookup);
      completions.sort(String::compareTo);
      myNewAttributeNameModel.setCompletions(completions);
    }
    return myLastLookup;
  }

  @NotNull
  private Validator.Result updateAcceptAnyway(@NotNull Boolean isOK) {
    myAcceptAnyway.setVisible(!isOK);
    return Validator.Result.OK;
  }

  @NotNull
  private Validator.Result updateOkButton(@NotNull Boolean hasErrors) {
    setOKActionEnabled(!hasErrors);
    return Validator.Result.OK;
  }

  private void createUIComponents() {
    myNewAttributeNameModel = new AttributeNameModel();
    myAttributeNameEditor = new CommonTextField<>(myNewAttributeNameModel);
    myValidatorPanel = new ValidatorPanel(myDisposable, new JPanel());
    myErrorPanel = (JPanel)myValidatorPanel.getComponent(0);
    myDataType = new EditorComboBox(CustomAttributeType.CUSTOM_STRING.getTagName());
  }

  private static class AttributeNameModel implements CommonTextFieldModel {
    private String myText = "";
    private List<String> myCompletions = Collections.emptyList();

    public void setCompletions(@NotNull List<String> completions) {
      myCompletions = completions;
    }

    @NotNull
    @Override
    public String getValue() {
      return "";
    }

    @Override
    public boolean getEnabled() {
      return true;
    }

    @Override
    public boolean getEditable() {
      return true;
    }

    @NotNull
    @Override
    public String getPlaceHolderValue() {
      return "";
    }

    @NotNull
    @Override
    public String getText() {
      return myText;
    }

    @Override
    public void setText(@NotNull String text) {
      myText = text;
    }

    @NotNull
    @Override
    public EditingSupport getEditingSupport() {
      return new EditingSupport() {
        @NotNull
        @Override
        public Function1<Runnable, Unit> getUiExecution() {
          return (runnable) -> {
            runnable.run();
            return Unit.INSTANCE;
          };
        }

        @NotNull
        @Override
        public Function1<Runnable, Future<?>> getExecution() {
          return (runnable) -> {
            runnable.run();
            return Futures.immediateFuture(Unit.INSTANCE);
          };
        }

        @NotNull
        @Override
        public Function1<String, Pair<EditingErrorCategory, String>> getValidation() {
          return (value) -> EDITOR_NO_ERROR;
        }

        @NotNull
        @Override
        public Function0<List<String>> getCompletion() {
          return () -> myCompletions;
        }
      };
    }

    @Override
    public void addListener(@NotNull ValueChangedListener listener) {
    }

    @Override
    public void removeListener(@NotNull ValueChangedListener listener) {
    }
  }
}
