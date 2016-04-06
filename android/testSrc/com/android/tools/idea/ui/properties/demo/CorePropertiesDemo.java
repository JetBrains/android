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
package com.android.tools.idea.ui.properties.demo;

import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.ui.properties.expressions.Expression;
import com.android.tools.idea.ui.properties.expressions.value.TransformOptionalExpression;
import com.android.tools.idea.ui.properties.expressions.string.FormatExpression;
import com.android.tools.idea.ui.properties.expressions.string.StringExpression;
import com.android.tools.idea.ui.properties.swing.SelectedItemProperty;
import com.android.tools.idea.ui.properties.swing.SelectedProperty;
import com.android.tools.idea.ui.properties.swing.SliderValueProperty;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.google.common.base.CaseFormat;
import com.google.common.base.Optional;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Sample UI which allows modification of and bindings to a simple data model built with
 * properties.
 * <p/>
 * This panel presents UI fields for modifying details about a person, followed by a UI for
 * printing back additional stats based on what values are typed in.
 * <p/>
 * Demonstrates:
 * - Using properties in an <a href="https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93viewmodel">MVVM</a> UI design
 * - Many of the core properties (Int, String, Bool, Object, and Optional)
 */
public final class CorePropertiesDemo {

  private final BindingsManager myBindings = new BindingsManager();
  private JTextField myNameTextField;
  private JComboBox myGenderCombo;
  private JSlider myAgeSlider;
  private JCheckBox myCitizenshipCheckBox;
  private JTextField myEmployerTextField;
  private JBLabel myAgeLabel;
  private JBLabel myIsValidNameLabel;
  private JBLabel myCanVoteLabel;
  private JBLabel myHasEmployerLabel;
  private JPanel myRootPanel;
  private JBLabel myGenderLabel;

  public CorePropertiesDemo() {
    final Person person = new Person();

    for (Gender gender : Gender.values()) {
      myGenderCombo.addItem(gender);
    }
    myGenderCombo.setSelectedItem(person.myGender.get());

    /**
     * Hook up the first panel (which modifies the target person)
     */
    myBindings.bindTwoWay(new TextProperty(myNameTextField), person.myName);
    myBindings.bindTwoWay(new SliderValueProperty(myAgeSlider), person.myAge);
    myBindings.bind(new TextProperty(myAgeLabel), new FormatExpression("%d", person.myAge));
    myBindings.bindTwoWay(new SelectedProperty(myCitizenshipCheckBox), person.myIsCitizen);
    final TextProperty employerName = new TextProperty(myEmployerTextField);
    myBindings.bind(person.myEmployer, new Expression<Optional<String>>(employerName) {
      @NotNull
      @Override
      public Optional<String> get() {
        if (!employerName.get().trim().isEmpty()) {
          return Optional.of(employerName.get());
        }
        else {
          return Optional.absent();
        }
      }
    });
    SelectedItemProperty<Gender> selectedGender = new SelectedItemProperty<>(myGenderCombo);
    myBindings.bind(person.myGender, new TransformOptionalExpression<Gender, Gender>(Gender.OTHER, selectedGender) {
      @NotNull
      @Override
      protected Gender transform(@NotNull Gender gender) {
        return gender;
      }
    });

    /**
     * Hook up the second panel (which prints out useful information about the person)
     */
    myBindings.bind(new TextProperty(myIsValidNameLabel), new YesNoExpression(person.myName.isEmpty().not()));
    myBindings.bind(new TextProperty(myGenderLabel), new StringExpression(person.myGender) {
      @NotNull
      @Override
      public String get() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, person.myGender.toString());
      }
    });
    myBindings.bind(new TextProperty(myCanVoteLabel), new YesNoExpression(person.myIsCitizen.and(person.myAge.isGreaterThanEqualTo(16))));
    myBindings.bind(new TextProperty(myHasEmployerLabel), new TransformOptionalExpression<String, String>("No", person.myEmployer) {
      @NotNull
      @Override
      protected String transform(@NotNull String value) {
        return String.format("Yes (%s)", value.trim());
      }
    });
  }

  public static void main(String[] args) {
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        JFrame frame = new JFrame("CorePropertiesDemo");
        frame.setContentPane(new CorePropertiesDemo().myRootPanel);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
      }
    });
  }

  private enum Gender {
    MALE,
    FEMALE,
    OTHER,
  }

  private static final class YesNoExpression extends Expression<String> {
    @NotNull private final ObservableBool myBoolValue;

    public YesNoExpression(@NotNull ObservableBool boolValue) {
      super(boolValue);
      myBoolValue = boolValue;
    }

    @NotNull
    @Override
    public String get() {
      return myBoolValue.get() ? "Yes" : "No";
    }
  }

  private static final class Person {
    private StringProperty myName = new StringValueProperty("Jane Doe");
    private ObjectProperty<Gender> myGender = new ObjectValueProperty<>(Gender.FEMALE);
    private IntProperty myAge = new IntValueProperty(13);
    private BoolProperty myIsCitizen = new BoolValueProperty();
    private OptionalProperty<String> myEmployer = new OptionalValueProperty<>();
  }
}
