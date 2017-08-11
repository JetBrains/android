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
package com.android.tools.adtui.validation;

import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.adtui.validation.validators.TrueValidator;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Iterator;

/**
 * A panel that wraps some inner content and allows registering {@link Validator}s, which, if any
 * of them are invalid, causes a warning or error message to display across the bottom of the
 * panel.
 */
public final class ValidatorPanel extends JPanel implements Disposable {

  /**
   * Used to set empty text on a label. If completely empty, the height calculations are off.
   */
  private static final String BLANK = " ";

  private final ListenerManager myListeners = new ListenerManager();
  private final Table<Validator.Severity, ObservableValue<?>, String> myMessages = HashBasedTable.create();
  private final BoolProperty myHasErrors = new BoolValueProperty();

  private JPanel myRootPanel;
  private JPanel mySouthPanel;
  private JBLabel myValidationLabel;

  public ValidatorPanel(@NotNull Disposable parentDisposable, @NotNull JComponent innerPanel) {
    super(new BorderLayout());

    add(myRootPanel);
    myRootPanel.add(innerPanel);

    myValidationLabel.setName("ValidationLabel");
    myValidationLabel.setText(BLANK);

    Disposer.register(parentDisposable, this);
  }

  /**
   * Register a {@link Validator} linked to a target property. Whenever the target property
   * changes, the validator will be tested with its value.
   *
   * Registration order of validators doesn't matter - if multiple errors happen at the same time
   * (or warnings, etc.), the one which shows up is random. However, a message of higher severity
   * will always trump a message of lower severity.
   *
   * See also {@link #hasErrors()}, which will be true if any validator has returned an
   * {@link Validator.Severity#ERROR} result.
   */
  public <T> void registerValidator(@NotNull final ObservableValue<T> value,
                                    @NotNull final Validator<T> validator) {
    myListeners.listenAndFire(value, sender -> {
      Validator.Result result = validator.validate(value.get());
      myMessages.column(value).clear();
      if (result.getSeverity() != Validator.Severity.OK) {
        myMessages.put(result.getSeverity(), value, result.getMessage());
      }

      updateValidationLabel();
    });
  }

  /**
   * Registers a target observable boolean as a simple test which, if {@code true}, means the
   * {@code message} should be shown with the specified {@code severity}.
   */
  public void registerTest(@NotNull ObservableValue<Boolean> value,
                           @NotNull Validator.Severity severity,
                           @NotNull String message) {
    registerValidator(value, new TrueValidator(severity, message));
  }

  /**
   * Calls {@link #registerTest(ObservableValue, Validator.Severity, String)} with an error
   * severity.
   */
  public void registerTest(@NotNull ObservableValue<Boolean> value,
                           @NotNull String message) {
    registerTest(value, Validator.Severity.ERROR, message);
  }

  /**
   * Registers a target observable string which represents an invalidation message. If the string
   * is set to some value, this panel will display it with the specified {@code severity}.
   **/
  public void registerMessageSource(@NotNull ObservableValue<String> message, @NotNull Validator.Severity severity) {
    registerValidator(message, value -> {
      if (value.isEmpty()) {
        return Validator.Result.OK;
      }
      return new Validator.Result(severity, value);
    });
  }

  /**
   * Calls {@link #registerMessageSource(ObservableValue, Validator.Severity)} with an error
   * severity.
   */
  public void registerMessageSource(@NotNull ObservableValue<String> message) {
    registerMessageSource(message, Validator.Severity.ERROR);
  }

  /**
   * Returns a property which indicates if any of the components in this panel are invalid.
   *
   * This is a useful property for UIs to listen to, as they can bind various components (such as a
   * next button) as appropriate, disabling functionality until all errors are resolved.
   */
  @NotNull
  public ObservableBool hasErrors() {
    return myHasErrors;
  }

  private void updateValidationLabel() {
    myValidationLabel.setIcon(null);
    myValidationLabel.setText(BLANK);

    boolean hasErrors = false;
    // The loop assumes that less serious issues are visited first (ie more serious problems are displayed first)
    for (Validator.Severity severity : Validator.Severity.values()) {
      Iterator<String> messages = myMessages.row(severity).values().iterator();
      if (messages.hasNext()) {
        myValidationLabel.setText(messages.next());
        myValidationLabel.setIcon(severity.getIcon());

        if (severity == Validator.Severity.ERROR) {
          hasErrors = true;
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
