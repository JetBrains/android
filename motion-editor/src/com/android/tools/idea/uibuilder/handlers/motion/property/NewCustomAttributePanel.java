/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property;

import com.android.tools.adtui.model.stdui.CommonTextFieldModel;
import com.android.tools.adtui.model.stdui.EditingErrorCategory;
import com.android.tools.adtui.model.stdui.EditingSupport;
import com.android.tools.adtui.model.stdui.EditingSupportKt;
import com.android.tools.adtui.model.stdui.ValueChangedListener;
import com.android.tools.adtui.stdui.CommonTextField;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.property.NlPropertyType;
import com.android.tools.property.panel.api.TableLineModel;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import icons.StudioIcons;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import kotlin.Pair;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class NewCustomAttributePanel extends DialogWrapper {
  private JTextField myAttributeNameEditor;
  private JTextField myInitialValueEditor;
  private JPanel myContentPanel;
  private ComboBox<CustomAttributeType> myDataType;
  private JCheckBox myAcceptAnyway;
  private JLabel myError;
  private MyTextFieldModel myNewAttributeNameModel;
  private MyTextFieldModel myInitialValueModel;
  private final MotionLayoutAttributesModel myPropertiesModel;
  private final MotionSelection mySelection;
  private final TableLineModel myLineModel;
  private final Supplier<CustomAttributeType> myTypeSupplier;
  private final DefaultComboBoxModel<CustomAttributeType> myModel;
  private final WindowFocusListener myWindowFocusListener;
  private NlPropertyItem myProperty;

  public NewCustomAttributePanel(@NotNull MotionLayoutAttributesModel propertiesModel,
                                 @NotNull MotionSelection selection,
                                 @NotNull TableLineModel lineModel) {
    super(propertiesModel.getProject(), false, IdeModalityType.MODELESS);
    myPropertiesModel = propertiesModel;
    mySelection = selection;
    myLineModel = lineModel;
    myModel = new DefaultComboBoxModel<>();
    myTypeSupplier = () -> {
      Object selectedType = myDataType.getSelectedItem();
      if (selectedType instanceof CustomAttributeType) {
        return (CustomAttributeType)selectedType;
      }
      return CustomAttributeType.CUSTOM_STRING;
    };
    NlComponent component = selection.getComponentForCustomAttributeCompletions();
    if (component != null) {
      myNewAttributeNameModel.setEditingSupport(new AttributeNameEditingSupport(component, myTypeSupplier));
    }
    Arrays.stream(CustomAttributeType.values()).forEach(type -> myModel.addElement(type));
    myModel.setSelectedItem(CustomAttributeType.CUSTOM_STRING);
    myDataType.setModel(myModel);
    myDataType.setEditable(false);
    initValidations();
    init();
    myWindowFocusListener = new WindowFocusListener() {
      private boolean myActivated;

      @Override
      public void windowGainedFocus(WindowEvent event) {
        // After we gain focus, wait a second (since focus may bounce back and forth a couple of times),
        // mark the dialog activated if it still has focus after a second. Otherwise close it.
        Runnable setActive = () -> {
          if (getWindow().isFocused()) {
            myActivated = true;
          }
          else {
            close(0);
          }
        };
        JobScheduler.getScheduler().schedule(setActive, 1, TimeUnit.SECONDS);
      }

      @Override
      public void windowLostFocus(WindowEvent event) {
        if (myActivated) {
          close(0);
        }
      }
    };
    getWindow().addWindowFocusListener(myWindowFocusListener);
  }

  @Override
  protected void dispose() {
    getWindow().removeWindowFocusListener(myWindowFocusListener);
    super.dispose();
  }

  @TestOnly
  public JTextField getAttributeNameEditor() {
    return myAttributeNameEditor;
  }

  @TestOnly
  public JTextField getInitialValueEditor() {
    return myInitialValueEditor;
  }

  @TestOnly
  public ComboBox<CustomAttributeType> getDataTypeComboBox() {
    return myDataType;
  }

  @TestOnly
  public JCheckBox getAcceptAnyway() {
    return myAcceptAnyway;
  }

  @TestOnly
  public JLabel getErrorLabel() {
    return myError;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDataType;
  }

  @NotNull
  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  @NotNull
  @Override
  protected ActionListener createCancelAction() {
    return e -> doCancelAction(e);
  }

  @Override
  protected void doOKAction() {
    String attributeName = getAttributeName();
    String value = getInitialValue();
    CustomAttributeType type = getType();
    myPropertiesModel.addCustomProperty(attributeName, value, type, mySelection, myLineModel);

    super.doOKAction();
  }

  @NotNull
  public String getAttributeName() {
    return myAttributeNameEditor.getText();
  }

  @NotNull
  public CustomAttributeType getType() {
    return myTypeSupplier.get();
  }

  @NotNull
  public String getInitialValue() {
    return myInitialValueEditor.getText();
  }

  private void initValidations() {
    myDataType.addActionListener((event) -> updateAfterTypeChange());
    myAcceptAnyway.addActionListener((event) -> updateErrorStatus());
    updateAfterTypeChange();
  }

  private void updateAfterTypeChange() {
    String typeTag = getType().getTagName();
    NlPropertyType propertyType = MotionLayoutPropertyProvider.mapFromCustomType(typeTag);
    if (myProperty == null || propertyType != myProperty.getType()) {
      myProperty = MotionLayoutPropertyProvider.createCustomProperty("property", typeTag, mySelection, myPropertiesModel);
      myInitialValueModel.setEditingSupport(myProperty.getEditingSupport());
    }
    myNewAttributeNameModel.updateNow();
    myInitialValueModel.updateNow();
    updateErrorStatus();
  }

  private void updateErrorStatus() {
    Pair<EditingErrorCategory, String> status = myNewAttributeNameModel.validate();
    if (status == EditingSupportKt.EDITOR_NO_ERROR) {
      status = myInitialValueModel.validate();
    }
    myAcceptAnyway.setVisible(status.getFirst() == EditingErrorCategory.ERROR);
    setOKActionEnabled(status.getFirst() != EditingErrorCategory.ERROR ||
                       myAcceptAnyway.isSelected() && !myNewAttributeNameModel.getText().isEmpty());
    switch (status.getFirst()) {
      case ERROR:
        myError.setVisible(true);
        myError.setText(status.getSecond());
        myError.setIcon(StudioIcons.Common.ERROR_INLINE);
        break;
      case WARNING:
        myError.setVisible(true);
        myError.setText(status.getSecond());
        myError.setIcon(StudioIcons.Common.WARNING_INLINE);
        break;
      default:
        myError.setVisible(false);
        break;
    }
  }

  private void createUIComponents() {
    myNewAttributeNameModel = new MyTextFieldModel(this::updateErrorStatus);
    myAttributeNameEditor = new CommonTextField<>(myNewAttributeNameModel);
    myInitialValueModel = new MyTextFieldModel(this::updateErrorStatus);
    myInitialValueEditor = new CommonTextField<>(myInitialValueModel);
    myDataType = new ComboBox<>();
  }

  private static class MyTextFieldModel implements CommonTextFieldModel {
    private final List<ValueChangedListener> myListeners;
    private final Runnable myTextChanged;
    private String myText = "";
    private EditingSupport myEditingSupport;

    private MyTextFieldModel(@NotNull Runnable textChanged) {
      myListeners = new ArrayList<>();
      myEditingSupport = new DefaultEditingSupport();
      myTextChanged = textChanged;
    }

    private void setEditingSupport(@NotNull EditingSupport editingSupport) {
      // Avoid running on the pooled thread:
      myEditingSupport = new DefaultEditingSupport() {
        @NotNull
        @Override
        public Function1<String, Pair<EditingErrorCategory, String>> getValidation() {
          return editingSupport.getValidation();
        }

        @NotNull
        @Override
        public Function1<String, List<String>> getCompletion() {
          return editingSupport.getCompletion();
        }
      };
    }

    private Pair<EditingErrorCategory, String> validate() {
      return myEditingSupport.getValidation().invoke(myText);
    }

    @NotNull
    @Override
    public String getValue() {
      return myText;
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
      myTextChanged.run();
    }

    public void updateNow() {
      myListeners.forEach((listener) -> listener.valueChanged());
    }

    @NotNull
    @Override
    public EditingSupport getEditingSupport() {
      return myEditingSupport;
    }

    @Override
    public void addListener(@NotNull ValueChangedListener listener) {
      myListeners.add(listener);
    }

    @Override
    public void removeListener(@NotNull ValueChangedListener listener) {
      myListeners.remove(listener);
    }
  }

  private static class DefaultEditingSupport implements EditingSupport {
    @NotNull
    @Override
    public Function1<Runnable, Unit> getUiExecution() {
      return (runnable) -> { runnable.run(); return Unit.INSTANCE; };
    }

    @NotNull
    @Override
    public Function1<Runnable, Future<?>> getExecution() {
      return EditingSupportKt.EDITOR_IMMEDIATE_EXECUTION;
    }

    @NotNull
    @Override
    public Function1<String, Pair<EditingErrorCategory, String>> getValidation() {
      return (value) -> EditingSupportKt.EDITOR_NO_ERROR;
    }

    @NotNull
    @Override
    public Function1<String, List<String>> getCompletion() {
      return (forText) -> Collections.emptyList();
    }

    @Override
    public boolean getAllowCustomValues() {
      return true;
    }

    @Override
    public boolean getAlwaysRefreshCompletions() {
      return false;
    }
  }

  private static class AttributeNameEditingSupport implements EditingSupport {
    private final NlComponent myComponent;
    private final Supplier<CustomAttributeType> myType;
    private CustomAttributeType myLastLookupType;
    private Set<String> myLastLookup = Collections.emptySet();
    private List<String> myCompletions = Collections.emptyList();

    private AttributeNameEditingSupport(@NotNull NlComponent component, @NotNull Supplier<CustomAttributeType> type) {
      myComponent = component;
      myType = type;
    }

    @NotNull
    @Override
    public Function1<Runnable, Unit> getUiExecution() {
      return (runnable) -> { runnable.run(); return Unit.INSTANCE; };
    }

    @NotNull
    @Override
    public Function1<Runnable, Future<?>> getExecution() {
      return EditingSupportKt.EDITOR_IMMEDIATE_EXECUTION;
    }

    @NotNull
    @Override
    public Function1<String, Pair<EditingErrorCategory, String>> getValidation() {
      return (value) -> validate(value);
    }

    @NotNull
    @Override
    public Function1<String, List<String>> getCompletion() {
      return (forText) -> getCompletions();
    }

    @Override
    public boolean getAllowCustomValues() {
      return true;
    }

    private Pair<EditingErrorCategory, String> validate(@Nullable String value) {
      if (value == null || value.isEmpty()) {
        return new Pair<>(EditingErrorCategory.ERROR, "Please supply an attribute name");
      }
      updateLookups();
      if (myLastLookup.contains(value)) {
        return EditingSupportKt.EDITOR_NO_ERROR;
      }
      String message = "Method not found: " + getMethodName(value) + ";  check arguments";
      return new Pair<>(EditingErrorCategory.ERROR, message);
    }

    @NotNull
    private static String getMethodName(@NotNull String attributeName) {
      return "set" + StringUtil.capitalize(attributeName);
    }

    private List<String> getCompletions() {
      updateLookups();
      return myCompletions;
    }

    private void updateLookups() {
      CustomAttributeType type = myType.get();
      if (type != myLastLookupType) {
        myLastLookup = MotionAttributes.getCustomAttributesFor(myComponent, type.getTagName());
        myLastLookupType = type;
        myCompletions = new ArrayList<>(myLastLookup);
        myCompletions.sort(String::compareTo);
      }
    }
  }
}
