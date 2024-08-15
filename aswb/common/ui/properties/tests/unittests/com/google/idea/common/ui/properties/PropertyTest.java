/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.common.ui.properties;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link Property}. */
@RunWith(JUnit4.class)
public class PropertyTest {

  @Test
  public void defaultValueCanBeSet() {
    Property<String> property = new Property<>("MyValue");

    assertThat(property.getValue()).isEqualTo("MyValue");
  }

  @Test
  public void defaultValueMayBeUnset() {
    Property<String> property = new Property<>();

    assertThat(property.getValue()).isNull();
  }

  @Test
  public void defaultValueMayBeNull() {
    Property<String> property = new Property<>(null);

    assertThat(property.getValue()).isNull();
  }

  @Test
  public void valueCanBeSet() {
    Property<String> property = new Property<>();
    property.setValue("MyValue");

    assertThat(property.getValue()).isEqualTo("MyValue");
  }

  @Test
  public void valueCanBeUnset() {
    Property<String> property = new Property<>("MyValue");
    property.setValue(null);

    assertThat(property.getValue()).isNull();
  }

  @Test
  public void additionOfInvalidationListenerDoesNotTriggerIt() {
    Property<String> property = new Property<>("InitialValue");
    List<String> observedValues = new ArrayList<>();
    property.addListener(observable -> observedValues.add(observable.getValue()));

    assertThat(observedValues).isEmpty();
  }

  @Test
  public void newValueTriggersInvalidationListenerOnce() {
    Property<String> property = new Property<>("InitialValue");
    List<String> observedValues = new ArrayList<>();
    property.addListener(observable -> observedValues.add(observable.getValue()));

    property.setValue("DifferentValue");

    assertThat(observedValues).containsExactly("DifferentValue");
  }

  @Test
  public void unchangedButNewlySetValueTriggersInvalidationListener() {
    Property<String> property = new Property<>("InitialValue");
    List<String> observedValues = new ArrayList<>();
    property.addListener(observable -> observedValues.add(observable.getValue()));

    property.setValue("InitialValue");

    assertThat(observedValues).contains("InitialValue");
  }

  @Test
  public void newValueDoesNotTriggerRemovedInvalidationListener() {
    Property<String> property = new Property<>("InitialValue");
    List<String> observedValues = new ArrayList<>();
    InvalidationListener<String> listener = observable -> observedValues.add(observable.getValue());
    property.addListener(listener);
    property.removeListener(listener);

    property.setValue("DifferentValue");

    assertThat(observedValues).isEmpty();
  }

  @Test
  public void newValueTriggersInvalidationListenerTwiceIfAddedTwice() {
    Property<String> property = new Property<>("InitialValue");
    List<String> observedValues = new ArrayList<>();
    InvalidationListener<String> listener = observable -> observedValues.add(observable.getValue());
    property.addListener(listener);
    property.addListener(listener);

    property.setValue("DifferentValue");

    assertThat(observedValues).containsExactly("DifferentValue", "DifferentValue");
  }

  @Test
  public void invalidationListenerMayBeAttachedToDifferentProperties() {
    Property<String> property1 = new Property<>("Value A");
    Property<String> property2 = new Property<>("Value B");
    List<String> observedValues = new ArrayList<>();
    InvalidationListener<String> listener = observable -> observedValues.add(observable.getValue());
    property1.addListener(listener);
    property2.addListener(listener);

    property1.setValue("Value A.2");
    property2.setValue("Value B.2");

    assertThat(observedValues).containsExactly("Value A.2", "Value B.2").inOrder();
  }

  @Test
  public void removingNotSubscribedInvalidationListenerDoesNotThrowAnError() {
    Property<String> property = new Property<>("InitialValue");
    InvalidationListener<String> listener = observable -> {};
    property.removeListener(listener);
  }

  @Test
  public void additionOfChangeListenerDoesNotTriggerIt() {
    Property<String> property = new Property<>("InitialValue");
    List<String> observedValues = new ArrayList<>();
    property.addListener((observable, oldValue, newValue) -> observedValues.add(newValue));

    assertThat(observedValues).isEmpty();
  }

  @Test
  public void newValueTriggersChangeListenerOnce() {
    Property<String> property = new Property<>("InitialValue");
    List<String> observedValues = new ArrayList<>();
    property.addListener((observable, oldValue, newValue) -> observedValues.add(newValue));

    property.setValue("DifferentValue");

    assertThat(observedValues).containsExactly("DifferentValue");
  }

  @Test
  public void unchangedButNewlySetValueDoesNotTriggerChangeListener() {
    Property<String> property = new Property<>("InitialValue");
    List<String> observedValues = new ArrayList<>();
    property.addListener((observable, oldValue, newValue) -> observedValues.add(newValue));

    property.setValue("InitialValue");

    assertThat(observedValues).isEmpty();
  }

  @Test
  public void newValueDoesNotTriggerRemovedChangeListener() {
    Property<String> property = new Property<>("InitialValue");
    List<String> observedValues = new ArrayList<>();
    ChangeListener<String> listener =
        (observable, oldValue, newValue) -> observedValues.add(newValue);
    property.addListener(listener);
    property.removeListener(listener);

    property.setValue("DifferentValue");

    assertThat(observedValues).isEmpty();
  }

  @Test
  public void newValueTriggersChangeListenerTwiceIfAddedTwice() {
    Property<String> property = new Property<>("InitialValue");
    List<String> observedValues = new ArrayList<>();
    ChangeListener<String> listener =
        (observable, oldValue, newValue) -> observedValues.add(newValue);
    property.addListener(listener);
    property.addListener(listener);

    property.setValue("DifferentValue");

    assertThat(observedValues).containsExactly("DifferentValue", "DifferentValue");
  }

  @Test
  public void changeListenerMayBeAttachedToDifferentProperties() {
    Property<String> property1 = new Property<>("Value A");
    Property<String> property2 = new Property<>("Value B");
    List<String> observedValues = new ArrayList<>();
    ChangeListener<String> listener =
        (observable, oldValue, newValue) -> observedValues.add(newValue);
    property1.addListener(listener);
    property2.addListener(listener);

    property1.setValue("Value A.2");
    property2.setValue("Value B.2");

    assertThat(observedValues).containsExactly("Value A.2", "Value B.2").inOrder();
  }

  @Test
  public void removingNotSubscribedChangeListenerDoesNotThrowAnError() {
    Property<String> property = new Property<>("InitialValue");
    ChangeListener<String> listener = (observable, oldValue, newValue) -> {};
    property.removeListener(listener);
  }

  @Test
  public void changeListenerGetsOldValue() {
    Property<String> property = new Property<>("InitialValue");
    List<String> observedOldValues = new ArrayList<>();
    property.addListener((observable, oldValue, newValue) -> observedOldValues.add(oldValue));

    property.setValue("DifferentValue");

    assertThat(observedOldValues).containsExactly("InitialValue");
  }

  @Test
  public void changeListenerGetsNullForOldValueIfPreviouslyUnset() {
    Property<String> property = new Property<>();
    List<String> observedOldValues = new ArrayList<>();
    property.addListener((observable, oldValue, newValue) -> observedOldValues.add(oldValue));

    property.setValue("DifferentValue");

    assertThat(observedOldValues).containsExactly((Object) null);
  }

  @Test
  public void changeListenerGetsObservableWithUpdatedValue() {
    Property<String> property = new Property<>();
    List<String> observedValues = new ArrayList<>();
    property.addListener(
        (observable, oldValue, newValue) -> observedValues.add(observable.getValue()));

    property.setValue("DifferentValue");

    assertThat(observedValues).containsExactly("DifferentValue");
  }

  @Test
  public void newValueTriggersAllSubscribedListeners() {
    Property<String> property = new Property<>("InitialValue");
    List<String> observedValues = new ArrayList<>();
    property.addListener(observable -> observedValues.add("InvalidationListener 1"));
    property.addListener(observable -> observedValues.add("InvalidationListener 2"));
    property.addListener(
        (observable, oldValue, newValue) -> observedValues.add("ChangeListener 1"));
    property.addListener(
        (observable, oldValue, newValue) -> observedValues.add("ChangeListener 2"));

    property.setValue("DifferentValue");

    assertThat(observedValues)
        .containsExactly(
            "InvalidationListener 1",
            "InvalidationListener 2",
            "ChangeListener 1",
            "ChangeListener 2");
  }
}
