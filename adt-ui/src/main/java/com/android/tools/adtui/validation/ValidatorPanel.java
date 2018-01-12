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

import com.android.tools.adtui.validation.validators.TrueValidator;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.xml.util.XmlStringUtil.escapeString;
import static com.intellij.xml.util.XmlStringUtil.isWrappedInHtml;
import static com.intellij.xml.util.XmlStringUtil.wrapInHtml;

/**
 * A panel that wraps some inner content and allows registering {@link Validator}s, which, if any
 * of them are invalid, causes a warning or error message to display across the bottom of the
 * panel.
 */
public final class ValidatorPanel extends JPanel implements Disposable {
  /** Used to set empty text on a label. If completely empty, the height calculations are off. */
  private static final String BLANK = " ";

  private final ListenerManager myListeners = new ListenerManager();
  private final List<Validator.Result> myResults = new ArrayList<>();
  private final BoolProperty myHasErrors = new BoolValueProperty();

  private JPanel myRootPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
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
   * If multiple validators produce non-OK results, the maximum severity wins. If there are
   * multiple messages with the same severity, the message produced by the validator that
   * was registered first wins.
   *
   * See also {@link #hasErrors()}, which will be true if any validator has returned an
   * {@link Validator.Severity#ERROR} result.
   */
  public <T> void registerValidator(@NotNull ObservableValue<T> value, @NotNull Validator<T> validator) {
    int index = myResults.size();
    myResults.add(Validator.Result.OK);
    myListeners.listenAndFire(value, sender -> {
      Validator.Result oldValue = myResults.get(index);
      Validator.Result newValue = validator.validate(value.get());
      if (!newValue.equals(oldValue)) {
        myResults.set(index, newValue);
        updateValidationLabel();
      }
    });
  }

  /**
   * Registers a target observable boolean as a simple test which, if {@code false}, means
   * the {@code message} should be shown with the specified {@code severity}.
   */
  public void registerTest(@NotNull ObservableValue<Boolean> value, @NotNull Validator.Severity severity, @NotNull String message) {
    registerValidator(value, new TrueValidator(severity, message));
  }

  /**
   * Calls {@link #registerTest(ObservableValue, Validator.Severity, String)} with an error
   * severity.
   */
  public void registerTest(@NotNull ObservableValue<Boolean> value, @NotNull String message) {
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
   * This is a useful property for UIs to listen to, as they can bind various components (such as
   * a next button) as appropriate, disabling functionality until all errors are resolved.
   */
  @NotNull
  public ObservableBool hasErrors() {
    return myHasErrors;
  }

  private void updateValidationLabel() {
    Validator.Result mostSevereResult = Validator.Result.OK;
    for (Validator.Result result : myResults) {
      if (result.getSeverity().compareTo(mostSevereResult.getSeverity()) > 0) {
        mostSevereResult = result;
        if (mostSevereResult.getSeverity() == Validator.Severity.ERROR) {
          break;
        }
      }
    }
    if (mostSevereResult.getSeverity() == Validator.Severity.OK) {
      myValidationLabel.setIcon(null);
      myValidationLabel.setText(BLANK);
    }
    else {
      myValidationLabel.setIcon(mostSevereResult.getSeverity().getIcon());
      String message = mostSevereResult.getMessage().trim();
      // A multiline message has to be wrapped to HTML to be displayed properly by JBLabel.
      if (message.indexOf('\n') >= 0 && !isWrappedInHtml(message)) {
        message = wrapInHtml(StringUtil.replace(escapeString(message), "\n", "<br>"));
      }
      myValidationLabel.setText(message);
    }

    myHasErrors.set(mostSevereResult.getSeverity() == Validator.Severity.ERROR);
  }

  @Override
  public void dispose() {
    myListeners.releaseAll();
  }

  @VisibleForTesting
  @NotNull
  public JLabel getValidationLabel() {
    return myValidationLabel;
  }
}
