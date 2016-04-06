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
package com.android.tools.idea.ui.properties;

import com.android.tools.idea.ui.properties.collections.ObservableList;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.IntValueProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import com.android.tools.idea.ui.properties.expressions.list.MapExpression;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Arrays;

import static com.android.tools.idea.ui.properties.BatchInvoker.INVOKE_IMMEDIATELY_STRATEGY;
import static org.fest.assertions.Assertions.assertThat;

public final class BindingsManagerTest {

  @Test
  public void oneWayBindingAffectedByTarget() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    IntValueProperty property1 = new IntValueProperty(10);
    IntValueProperty property2 = new IntValueProperty(20);

    bindings.bind(property1, property2);
    assertThat(property1.get()).isEqualTo(20);

    property1.set(30);
    assertThat(property1.get()).isEqualTo(30);
    assertThat(property2.get()).isEqualTo(20);

    property2.set(40);
    assertThat(property1.get()).isEqualTo(40);
  }

  @Test
  public void twoWayBindingsAffectEachOther() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    IntValueProperty property1 = new IntValueProperty(10);
    IntValueProperty property2 = new IntValueProperty(20);

    bindings.bindTwoWay(property1, property2);
    assertThat(property1.get()).isEqualTo(20);

    property1.set(30);
    assertThat(property2.get()).isEqualTo(30);

    property2.set(40);
    assertThat(property1.get()).isEqualTo(40);
  }

  @Test
  public void mapBindingsUpdateDestinationList() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    ObservableList<Integer> numericList = new ObservableList<>();
    for (int i = 1; i <= 5; i++) {
      numericList.add(i);
    }
    ObservableList<String> stringList = new ObservableList<>();
    CountListener listener = new CountListener();
    stringList.addListener(listener);

    bindings.bind(stringList, new MapExpression<Integer, String>(numericList) {
      @NotNull
      @Override
      protected String transform(@NotNull Integer srcElement) {
        return srcElement.toString();
      }
    });

    assertThat(stringList).containsExactly("1", "2", "3", "4", "5");
    assertThat(listener.getCount()).isEqualTo(1);

    numericList.addAll(Arrays.asList(6, 7));

    assertThat(stringList).containsExactly("1", "2", "3", "4", "5", "6", "7");
    assertThat(listener.getCount()).isEqualTo(2);

    numericList.clear();

    assertThat(stringList).isEmpty();
    assertThat(listener.getCount()).isEqualTo(3);
  }

  @Test
  public void releaseDisconnectsOneWayBindings() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    StringValueProperty property1 = new StringValueProperty("A");
    StringValueProperty property2 = new StringValueProperty("B");

    bindings.bind(property1, property2);
    assertThat(property1.get()).isEqualTo("B");

    bindings.release(property1);

    property2.set("Property2");
    assertThat(property1.get()).isEqualTo("B");
  }

  @Test
  public void releaseTwoWayDisconnectsTwoWayBindings() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    StringValueProperty property1 = new StringValueProperty("First");
    StringValueProperty property2 = new StringValueProperty("Second");

    bindings.bindTwoWay(property1, property2);
    assertThat(property1.get()).isEqualTo("Second");

    bindings.releaseTwoWay(property1, property2);

    property1.set("Property1");
    assertThat(property2.get()).isEqualTo("Second");

    property2.set("Property2");
    assertThat(property1.get()).isEqualTo("Property1");
  }

  @Test
  public void releaseTwoWayWithOneArgDisconnectsAllMatchingBindings() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    StringValueProperty property1 = new StringValueProperty("First");
    StringValueProperty property2 = new StringValueProperty("Second");
    StringValueProperty property3 = new StringValueProperty("Third");

    bindings.bindTwoWay(property1, property2);
    bindings.bindTwoWay(property3, property2);
    assertThat(property1.get()).isEqualTo("Second");
    assertThat(property3.get()).isEqualTo("Second");

    bindings.releaseTwoWay(property2);

    property1.set("Property1");
    assertThat(property2.get()).isEqualTo("Second");

    property3.set("Property3");
    assertThat(property2.get()).isEqualTo("Second");
  }

  @Test
  public void releaseDisconnectsListBindings() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    ObservableList<String> dest = new ObservableList<>();
    ObservableList<Integer> src = new ObservableList<>();

    bindings.bind(dest, new MapExpression<Integer, String>(src) {
      @NotNull
      @Override
      protected String transform(@NotNull Integer srcElement) {
        return srcElement.toString();
      }
    });

    src.add(5);
    assertThat(dest).containsExactly("5");

    bindings.release(dest);

    src.add(10);
    assertThat(dest).containsExactly("5");
  }

  @Test
  public void releaseAllDisconnectsOneWayBindings() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    StringValueProperty property1 = new StringValueProperty("A");
    StringValueProperty property2 = new StringValueProperty("B");

    bindings.bind(property1, property2);
    assertThat(property1.get()).isEqualTo("B");

    bindings.releaseAll();

    property2.set("Property2");
    assertThat(property1.get()).isEqualTo("B");
  }

  @Test
  public void releaseAllDisconnectsTwoWayBindings() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    StringValueProperty property1 = new StringValueProperty("First");
    StringValueProperty property2 = new StringValueProperty("Second");

    bindings.bindTwoWay(property1, property2);
    assertThat(property1.get()).isEqualTo("Second");

    bindings.releaseAll();

    property1.set("Property1");
    assertThat(property2.get()).isEqualTo("Second");

    property2.set("Property2");
    assertThat(property1.get()).isEqualTo("Property1");
  }

  @Test
  public void releaseAllDisconnectsListBindings() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    ObservableList<String> dest = new ObservableList<>();
    ObservableList<Integer> src = new ObservableList<>();

    bindings.bind(dest, new MapExpression<Integer, String>(src) {
      @NotNull
      @Override
      protected String transform(@NotNull Integer srcElement) {
        return srcElement.toString();
      }
    });

    src.add(5);
    assertThat(dest).containsExactly("5");

    bindings.releaseAll();

    src.add(10);
    assertThat(dest).containsExactly("5");
  }

  @Test
  public void twoWayBindingsCanBeChained() {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    IntValueProperty a = new IntValueProperty();
    IntValueProperty b = new IntValueProperty();
    IntValueProperty c = new IntValueProperty();

    bindings.bindTwoWay(a, b);
    bindings.bindTwoWay(b, c);

    c.set(30);
    assertThat(a.get()).isEqualTo(30);
    assertThat(b.get()).isEqualTo(30);
    assertThat(c.get()).isEqualTo(30);

    b.set(-100);
    assertThat(a.get()).isEqualTo(-100);
    assertThat(b.get()).isEqualTo(-100);
    assertThat(c.get()).isEqualTo(-100);

    a.set(9);
    assertThat(a.get()).isEqualTo(9);
    assertThat(b.get()).isEqualTo(9);
    assertThat(c.get()).isEqualTo(9);
  }

  @Test
  public void oneWayBindingsCanBeEnabledConditionally() {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    IntValueProperty srcProperty = new IntValueProperty(10);
    IntValueProperty destProperty = new IntValueProperty(-5);
    BoolValueProperty bindingEnabled = new BoolValueProperty(true);

    bindings.bind(destProperty, srcProperty, bindingEnabled);
    assertThat(destProperty.get()).isEqualTo(10);

    srcProperty.set(20);
    assertThat(destProperty.get()).isEqualTo(20);

    bindingEnabled.set(false);
    assertThat(destProperty.get()).isEqualTo(20);

    srcProperty.set(30);
    assertThat(destProperty.get()).isEqualTo(20);

    srcProperty.set(40);
    assertThat(destProperty.get()).isEqualTo(20);

    bindingEnabled.set(true);
    assertThat(destProperty.get()).isEqualTo(40);

    srcProperty.set(50);
    assertThat(destProperty.get()).isEqualTo(50);
  }
}