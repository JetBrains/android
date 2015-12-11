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
package com.android.tools.idea.ui.wizard;

import com.android.tools.idea.ui.Colors;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.core.BoolProperty;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.collect.Maps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * A panel that provides a standard look and feel across wizard steps used in Android Studio.
 */
public final class StudioWizardStepPanel extends JPanel implements Disposable {

  /**
   * Used to set empty text on a label. If completely empty, the height calculations are off.
   */
  private static final String BLANK = " ";

  private final ListenerManager myListeners = new ListenerManager();
  private final Map<ObservableValue<?>, String> myErrors = Maps.newLinkedHashMap();
  private final Map<ObservableValue<?>, String> myWarnings = Maps.newLinkedHashMap();
  private final BoolProperty myHasErrors = new BoolValueProperty();

  private JPanel myRootPanel;
  private JPanel mySouthPanel;
  private JBLabel myValidationLabel;
  private JBLabel myDescriptionLabel;

  public StudioWizardStepPanel(@NotNull ModelWizardStep<?> parentStep, @NotNull JPanel innerPanel){
    this(parentStep, innerPanel, null);
  }

  public StudioWizardStepPanel(@NotNull ModelWizardStep<?> parentStep, @NotNull JPanel innerPanel, @Nullable String description) {
    super(new BorderLayout());

    add(myRootPanel);
    myRootPanel.add(innerPanel);

    myDescriptionLabel.setText(description != null ? description : "");
    updateValidationLabel();

    Disposer.register(parentStep, this);
  }

  /**
   * Register a {@link Validator} linked to a target property.
   *
   * Registration order matters - the first error reported takes precedence over later errors
   * if more than one error occurs at the same time; the same is true for warnings. Any error will
   * trump any warning.
   *
   * See also {@link #hasErrors()}, which will be true if any validator has returned an
   * {@link Validator.Severity#ERROR} result.
   */
  public <T> void registerValidator(@NotNull final ObservableValue<T> value,
                                    @NotNull final Validator<T> validator) {
    myListeners.listenAndFire(value, new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        Validator.Result result = validator.validate(value.get());
        switch (result.getSeverity()) {
          case ERROR:
            myWarnings.put(value, "");
            myErrors.put(value, result.getMessage());
            break;
          case WARNING:
            myErrors.put(value, "");
            myWarnings.put(value, result.getMessage());
            break;
          default:
            myErrors.put(value, "");
            myWarnings.put(value, "");
            break;
        }

        updateValidationLabel();
      }
    });
  }

  /**
   * Returns a property which indicates if any of the components in this panel are invalid.
   *
   * This is a useful property to listen to when overriding {@link ModelWizardStep#canGoForward()},
   * and you may even be able to just return it directly.
   */
  public ObservableBool hasErrors() {
    return myHasErrors;
  }

  private void updateValidationLabel() {
    boolean hasErrors = false;
    myValidationLabel.setText(BLANK);
    for (String error : myErrors.values()) {
      if (!error.isEmpty()) {
        myValidationLabel.setText(error);
        myValidationLabel.setForeground(Colors.ERROR);
        hasErrors = true;
        break;
      }
    }

    if (!hasErrors) {
      for (String warning : myWarnings.values()) {
        if (!warning.isEmpty()) {
          myValidationLabel.setText(warning);
          myValidationLabel.setForeground(Colors.WARNING);
          break;
        }
      }
    }

    myHasErrors.set(hasErrors);
  }

  @Override
  public void dispose() {
    myListeners.releaseAll();
  }
}
