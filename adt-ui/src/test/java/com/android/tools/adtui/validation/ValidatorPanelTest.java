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
package com.android.tools.adtui.validation;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.observable.BatchInvoker;
import com.android.tools.idea.observable.BatchInvokerStrategyRule;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.IntProperty;
import com.android.tools.idea.observable.core.IntValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import java.util.function.Consumer;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

public class ValidatorPanelTest {
  @Rule
  public BatchInvokerStrategyRule myStrategyRule = new BatchInvokerStrategyRule(BatchInvoker.INVOKE_IMMEDIATELY_STRATEGY);

  private static void createPanel(@NotNull Consumer<ValidatorPanel> panelConsumer) {
    Disposable disposable = Disposer.newDisposable();

    try {
      JPanel stubPanel = new JPanel();
      ValidatorPanel validatorPanel = new ValidatorPanel(disposable, stubPanel);
      panelConsumer.accept(validatorPanel);
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  /**
   * ValidationPanel sets its label to some whitespace instead of an empty string when there is
   * nothing to show, as this prevent its UI from jumping vertically every time a message appears
   * or disappears. Therefore, we can't test "text.isEmpty" directly.
   */
  private static void assertThatNoMessageIsVisible(@NotNull ValidatorPanel panel) {
    assertThat(StringUtil.isEmptyOrSpaces(getValidationText(panel))).isTrue();
  }

  private static String getValidationText(@NotNull ValidatorPanel validatorPanel) {
    return StringUtil.removeHtmlTags(validatorPanel.getValidationText().getText());
  }

  @Test
  public void newPanelHasNoErrors() {
    createPanel(panel -> {
      assertThat(panel.hasErrors().get()).isFalse();
      assertThat(panel.getValidationResult().get()).isEqualTo(Validator.Result.OK);
      assertThatNoMessageIsVisible(panel);
    });
  }

  @Test
  public void registerValidatorWorks() {
    createPanel(panel -> {
      IntProperty shouldBePositive = new IntValueProperty(0);

      panel.registerValidator(shouldBePositive, new Validator<Integer>() {
        @NotNull
        @Override
        public Result validate(@NotNull Integer value) {
          if (value >= 0) {
            return Result.OK;
          }
          else {
            return new Result(Severity.ERROR, "Negative value: " + value);
          }
        }
      });

      assertThat(panel.hasErrors().get()).isFalse();
      assertThatNoMessageIsVisible(panel);

      shouldBePositive.set(100);
      assertThat(panel.hasErrors().get()).isFalse();
      assertThatNoMessageIsVisible(panel);

      shouldBePositive.set(-100);
      assertThat(panel.hasErrors().get()).isTrue();
      assertThat(panel.getValidationResult().get().getSeverity()).isEqualTo(Validator.Severity.ERROR);
      assertThat(getValidationText(panel)).isEqualTo("Negative value: -100");

      shouldBePositive.set(100);
      assertThat(panel.hasErrors().get()).isFalse();
      assertThatNoMessageIsVisible(panel);
    });
  }

  @Test
  public void registerTestWorks() {
    createPanel(panel -> {
      BoolProperty shouldBeTrue = new BoolValueProperty(true);

      panel.registerTest(shouldBeTrue, "Value is false");
      assertThat(panel.hasErrors().get()).isFalse();
      assertThatNoMessageIsVisible(panel);

      shouldBeTrue.set(false);
      assertThat(panel.hasErrors().get()).isTrue();
      assertThat(getValidationText(panel)).isEqualTo("Value is false");

      shouldBeTrue.set(true);
      assertThat(panel.hasErrors().get()).isFalse();
      assertThatNoMessageIsVisible(panel);
    });
  }

  @Test
  public void registerMessageSourceWorks() {
    createPanel(panel -> {
      StringProperty message = new StringValueProperty("");

      panel.registerMessageSource(message);
      assertThat(panel.hasErrors().get()).isFalse();
      assertThatNoMessageIsVisible(panel);

      message.set("I can't let you do that Dave");
      assertThat(panel.hasErrors().get()).isTrue();
      assertThat(getValidationText(panel)).isEqualTo("I can't let you do that Dave");

      message.set("Error");
      assertThat(panel.hasErrors().get()).isTrue();
      assertThat(getValidationText(panel)).isEqualTo("Error");

      message.set("");
      assertThat(panel.hasErrors().get()).isFalse();
      assertThatNoMessageIsVisible(panel);
    });
  }

  @Test
  public void hasErrorsOnlyTrueIfErrorIsFound() {
    createPanel(panel -> {
      BoolProperty errorIfFalse = new BoolValueProperty(true);
      BoolProperty warningIfFalse = new BoolValueProperty(true);
      BoolProperty infoIfFalse = new BoolValueProperty(true);

      panel.registerTest(errorIfFalse, Validator.Severity.ERROR, "Error");
      panel.registerTest(warningIfFalse, Validator.Severity.WARNING, "Warning");
      panel.registerTest(infoIfFalse, Validator.Severity.INFO, "Info");

      assertThat(panel.hasErrors().get()).isFalse();
      assertThat(panel.getValidationResult().get().getSeverity()).isEqualTo(Validator.Severity.OK);
      assertThatNoMessageIsVisible(panel);

      infoIfFalse.set(false);
      assertThat(panel.hasErrors().get()).isFalse();
      assertThat(panel.getValidationResult().get().getSeverity()).isEqualTo(Validator.Severity.INFO);
      assertThat(getValidationText(panel)).isEqualTo("Info");

      infoIfFalse.set(true);
      warningIfFalse.set(false);
      assertThat(panel.hasErrors().get()).isFalse();
      assertThat(panel.getValidationResult().get().getSeverity()).isEqualTo(Validator.Severity.WARNING);
      assertThat(getValidationText(panel)).isEqualTo("Warning");

      warningIfFalse.set(true);
      errorIfFalse.set(false);
      assertThat(panel.hasErrors().get()).isTrue();
      assertThat(panel.getValidationResult().get().getSeverity()).isEqualTo(Validator.Severity.ERROR);
      assertThat(getValidationText(panel)).isEqualTo("Error");
    });
  }

  @Test
  public void errorsWarningsInfoOrderIsPrioritized() {
    createPanel(panel -> {
      BoolProperty errorIfFalse = new BoolValueProperty(true);
      BoolProperty warningIfFalse = new BoolValueProperty(true);
      BoolProperty infoIfFalse = new BoolValueProperty(true);

      panel.registerTest(errorIfFalse, Validator.Severity.ERROR, "Error");
      panel.registerTest(warningIfFalse, Validator.Severity.WARNING, "Warning");
      panel.registerTest(infoIfFalse, Validator.Severity.INFO, "Info");

      assertThat(panel.hasErrors().get()).isFalse();
      assertThatNoMessageIsVisible(panel);

      infoIfFalse.set(false);
      assertThat(getValidationText(panel)).isEqualTo("Info");

      warningIfFalse.set(false); // Warning > Info
      assertThat(getValidationText(panel)).isEqualTo("Warning");

      errorIfFalse.set(false); // Error > Warning
      assertThat(getValidationText(panel)).isEqualTo("Error");

      errorIfFalse.set(true); // Error is gone; Warning is highest left
      assertThat(getValidationText(panel)).isEqualTo("Warning");

      warningIfFalse.set(true); // Warning is gone; Info is highest left
      assertThat(getValidationText(panel)).isEqualTo("Info");

      errorIfFalse.set(false); // Error > Info
      assertThat(getValidationText(panel)).isEqualTo("Error");
    });
  }
}