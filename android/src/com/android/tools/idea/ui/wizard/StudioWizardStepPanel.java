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
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.NotNullProducer;
import org.jetbrains.annotations.NotNull;

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
  private static String BLANK = " ";

  private final ListenerManager myListeners = new ListenerManager();
  private final Map<ObservableValue<?>, String> myErrors = Maps.newLinkedHashMap();
  private final Map<ObservableValue<?>, String> myWarnings = Maps.newLinkedHashMap();
  private final Map<ObservableValue<?>, ValidationResult> myValidators = Maps.newLinkedHashMap();
  private final BoolProperty myHasErrors = new BoolValueProperty();

  private JPanel myRootPanel;
  private JPanel mySouthPanel;
  private JBLabel myValidationLabel;
  private JBLabel myDescriptionLabel;

  public StudioWizardStepPanel(@NotNull ModelWizardStep<?> parentStep, @NotNull JPanel innerPanel, @NotNull String description) {
    super(new BorderLayout());

    add(myRootPanel);
    myRootPanel.add(innerPanel);

    myDescriptionLabel.setText(description);
    updateValidationLabel();

    Disposer.register(parentStep, this);
  }

  /**
   * Register a validation handler for the current property. The errorProducer callback should
   * return a readable error string if there's a problem worth blocking on, or {@code ""} to
   * indicate everything is fine.
   * <p/>
   * Registration order matters - the first error reported takes precedence over later errors if
   * more than one error occurs at the same time.
   * <p/>
   * See also {@link #hasErrors()}, which will be true if at least one error is returned.
   */
  public <T> void registerErrorValidator(@NotNull final ObservableValue<T> value, @NotNull final NotNullProducer<String> errorProducer) {
    myListeners.listenAndFire(value, new InvalidationListener() {
      @Override
      protected void onInvalidated(@NotNull ObservableValue<?> sender) {
        myErrors.put(value, errorProducer.produce());
        updateValidationLabel();
      }
    });
  }

  /**
   * Register a validation handler for the current property. The warningProducer callback should
   * return a readable warning string if there's something that should be brought to the user's
   * attention (but not worth blocking progress over), or {@code ""} to indicate everything is
   * fine.
   * <p/>
   * Registration order matters - the first warning reported takes precedence over later warnings
   * if more than one warning occurs at the same time. Any error will trump any warning.
   */
  public <T> void registerWarningValidator(@NotNull final ObservableValue<T> value,
                                           @NotNull final NotNullProducer<String> warningProducer) {
    myListeners.listenAndFire(value, new InvalidationListener() {
      @Override
      protected void onInvalidated(@NotNull ObservableValue<?> sender) {
        myWarnings.put(value, warningProducer.produce());
        updateValidationLabel();
      }
    });
  }

  /**
   * Register a validation handler for the current property. See {@link ValidationResult} for more
   * information about the sort of conditions it can validate for.
   * <p/>
   * Registration order matters - the first error reported takes precedence over later errors
   * if more than one error occurs at the same time; the same is true for warnings. Any error will
   * trump any warning.
   * <p/>
   * See also {@link #hasErrors()}, which will be true if a {@link ValidationResult.Status#ERROR}
   * is returned.
   */
  public <T> void registerValidator(@NotNull final ObservableValue<T> value,
                                    @NotNull final NotNullProducer<ValidationResult> validationProducer) {
    myListeners.listenAndFire(value, new InvalidationListener() {
      @Override
      protected void onInvalidated(@NotNull ObservableValue<?> sender) {
        ValidationResult result = validationProducer.produce();
        if (result.getStatus() == ValidationResult.Status.ERROR) {
          myWarnings.put(value, "");
          myErrors.put(value, result.getMessage());
        }
        else if (result.getStatus() == ValidationResult.Status.WARN) {
          myErrors.put(value, "");
          myWarnings.put(value, result.getMessage());
        }
        else {
          myErrors.put(value, "");
          myWarnings.put(value, "");
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
        myValidationLabel.setForeground(JBColor.RED);
        hasErrors = true;
        break;
      }
    }

    if (!hasErrors) {
      for (String warning : myWarnings.values()) {
        if (!warning.isEmpty()) {
          myValidationLabel.setText(warning);
          myValidationLabel.setForeground(JBColor.YELLOW.darker());
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
