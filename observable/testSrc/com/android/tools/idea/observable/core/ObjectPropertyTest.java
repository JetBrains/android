/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.observable.core;

import com.android.tools.idea.observable.CountListener;
import com.google.common.base.Objects;
import org.junit.Assert;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public final class ObjectPropertyTest {

  @Test
  public void testInitialization() {
    Person person = new Person("John Doe", 25);
    ObjectProperty<Person> personProperty = new ObjectValueProperty<>(person);
    assertThat(personProperty.get()).isSameAs(person);
  }

  @Test
  public void testSetValue() {
    Person personA = new Person("John Doe", 25);
    Person personB = new Person("Jane Doe", 21);
    ObjectProperty<Person> personProperty = new ObjectValueProperty<>(personA);
    personProperty.set(personB);
    assertThat(personProperty.get()).isSameAs(personB);
  }

  @Test
  public void testInvalidationListenerFiredOnValueChange() {
    Person personA = new Person("John Doe", 25);
    Person personB = new Person("Jane Doe", 21);
    Person cloneB = new Person("Jane Doe", 21);

    CountListener listener = new CountListener();
    ObjectProperty<Person> personProperty = new ObjectValueProperty<>(personA);

    personProperty.addListener(listener);

    assertThat(listener.getCount()).isEqualTo(0);
    personProperty.set(personB);
    assertThat(listener.getCount()).isEqualTo(1);
    personProperty.set(cloneB);
    assertThat(listener.getCount()).isEqualTo(1);
  }

  @Test
  public void testObjectPropertyCanWrapOptionalProperty() {
    Person person1 = new Person("John Doe", 25);
    Person person2 = new Person("Jane Doe", 21);

    OptionalProperty<Person> optionalPersonProperty = new OptionalValueProperty<>(person1);

    ObjectProperty<Person> personProperty = ObjectProperty.wrap(optionalPersonProperty);
    assertThat(personProperty.get()).isEqualTo(person1);

    personProperty.set(person2);

    assertThat(optionalPersonProperty.getValue()).isEqualTo(person2);

    optionalPersonProperty.clear();

    try {
      personProperty.get();
      Assert.fail();
    }
    catch (Exception expected) {
    }
  }

  private static class Person {
    private String myName;
    private int myAge;

    public Person(String name, int age) {
      myName = name;
      myAge = age;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Person person = (Person)o;
      return myAge == person.myAge && Objects.equal(myName, person.myName);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myName, myAge);
    }
  }
}

