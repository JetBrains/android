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
package com.android.tools.idea.ui.properties.collections;

import com.android.tools.idea.ui.properties.CountListener;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import static org.fest.assertions.Assertions.assertThat;

@SuppressWarnings({"MismatchedQueryAndUpdateOfCollection", "WhileLoopReplaceableByForEach"})
public final class ObservableListTest {
  @Test
  public void testCopyConstructor() {
    List<String> srcList = Lists.newArrayList("A", "B", "C");
    ObservableList<String> cloneList = new ObservableList<>(srcList);

    assertThat(cloneList).containsExactly("A", "B", "C");
  }

  @Test
  public void testSizeQueries() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    list.add("A");
    list.add("B");
    list.add("C");

    assertThat(list.size()).isEqualTo(3);
    assertThat(list.isEmpty()).isFalse();
  }

  @Test
  public void testContains() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    list.add("A");
    list.add("B");
    list.add("C");

    assertThat(list.contains("B")).isTrue();
    assertThat(list.contains("X")).isFalse();
  }

  @Test
  public void testContainsAll() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    list.add("A");
    list.add("B");
    list.add("C");

    assertThat(list.containsAll(ImmutableSet.of("A", "B", "C"))).isTrue();
    assertThat(list.containsAll(ImmutableSet.of("X", "Y", "Z"))).isFalse();
  }

  @Test
  public void testIndexOf() {
    ObservableList<String> list = new ObservableList<>();
    list.add("A");
    list.add("B");
    list.add("C");
    list.add("B");

    assertThat(list.indexOf("B")).isEqualTo(1);
    assertThat(list.get(2)).isEqualTo("C");
    assertThat(list.lastIndexOf("B")).isEqualTo(3);
  }

  @Test
  public void testToArray() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    list.add("A");
    list.add("B");
    list.add("C");

    assertThat(list.toArray()).isEqualTo(new String[]{"A", "B", "C"});
  }

  @Test
  public void testToArray2() throws Exception {
    String[] testArray = new String[3];
    ObservableList<String> list = new ObservableList<>();
    list.add("A");
    list.add("B");
    list.add("C");
    list.toArray(testArray);

    assertThat(testArray).isEqualTo(new String[]{"A", "B", "C"});
  }

  @Test
  public void listIsIterable() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    list.add("A");
    list.add("B");
    list.add("C");

    String result = "";
    for (String s : list) {
      result += s;
    }

    assertThat(result).isEqualTo("ABC");
  }

  @Test
  public void listIsListIterable() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    list.add("A");
    list.add("B");
    list.add("C");

    String result = "";
    ListIterator<String> listIterator = list.listIterator();
    while (listIterator.hasNext()) {
      result += listIterator.next();
    }

    assertThat(result).isEqualTo("ABC");
  }

  @Test
  public void listIsListIterableAtIndex() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    list.add("A");
    list.add("B");
    list.add("C");

    String result = "";
    ListIterator<String> listIterator = list.listIterator(3);
    while (listIterator.hasPrevious()) {
      result += listIterator.previous();
    }

    assertThat(result).isEqualTo("CBA");
  }

  @Test
  public void testSublist() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    list.add("A");
    list.add("B");
    list.add("C");

    List<String> sublist = list.subList(1, 3);
    assertThat(sublist).containsExactly("B", "C");
  }

  @Test
  public void listInvalidatedOnAdd() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    assertThat(listener.getCount()).isEqualTo(0);
    list.add("A");
    assertThat(listener.getCount()).isEqualTo(1);
  }

  @Test
  public void listInvalidatedOnAddAtIndex() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    list.add("A");
    list.add("C");
    assertThat(listener.getCount()).isEqualTo(2);

    list.add(1, "B");
    assertThat(listener.getCount()).isEqualTo(3);
    assertThat(list).containsExactly("A", "B", "C");
  }

  @Test
  public void listInvalidatedOnSet() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    list.add("A");
    list.add("X");
    list.add("C");
    assertThat(listener.getCount()).isEqualTo(3);

    list.set(1, "B");
    assertThat(listener.getCount()).isEqualTo(4);
    assertThat(list).containsExactly("A", "B", "C");
  }

  @Test
  public void listInvalidatedForEachElementAdded() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    assertThat(listener.getCount()).isEqualTo(0);
    list.add("A");
    list.add("B");
    list.add("C");
    assertThat(listener.getCount()).isEqualTo(3);
  }

  @Test
  public void listInvalidatedOnRemove() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    assertThat(listener.getCount()).isEqualTo(0);
    list.add("A");
    list.add("B");
    assertThat(listener.getCount()).isEqualTo(2);
    list.remove(0);
    assertThat(listener.getCount()).isEqualTo(3);
    list.remove("B");
    assertThat(listener.getCount()).isEqualTo(4);
  }

  @Test
  public void listInvalidatedOnClear() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    list.add("A");

    assertThat(listener.getCount()).isEqualTo(1);
    list.clear();
    assertThat(listener.getCount()).isEqualTo(2);
  }

  @Test
  public void clearOnEmptyListDoesntFireInvalidation() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    list.clear();
    assertThat(listener.getCount()).isEqualTo(0);
  }

  @Test
  public void listInvalidatedOnRemoveAll() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    list.add("A");
    list.add("B");
    list.add("C");

    assertThat(listener.getCount()).isEqualTo(3);
    list.removeAll(ImmutableSet.of("A", "C"));
    assertThat(listener.getCount()).isEqualTo(4);
    assertThat(list).containsExactly("B");
  }

  @Test
  public void removeAllWithoutChangeDoesntFireInvalidation() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    list.add("A");
    list.add("B");
    list.add("C");

    assertThat(listener.getCount()).isEqualTo(3);

    list.removeAll(ImmutableSet.of("X", "Y", "Z"));
    assertThat(listener.getCount()).isEqualTo(3);
  }

  @Test
  public void listInvalidatedOnRetainAll() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    list.add("A");
    list.add("B");
    list.add("C");

    assertThat(listener.getCount()).isEqualTo(3);
    list.retainAll(ImmutableSet.of("A", "C"));
    assertThat(listener.getCount()).isEqualTo(4);
    assertThat(list).containsExactly("A", "C");
  }

  @Test
  public void retainAllWithoutChangeDoesntFireInvalidation() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    list.add("A");
    list.add("B");
    list.add("C");

    assertThat(listener.getCount()).isEqualTo(3);

    list.retainAll(ImmutableSet.of("A", "B", "C"));
    assertThat(listener.getCount()).isEqualTo(3);
  }

  @Test
  public void listInvalidatedOnAddAll() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    list.addAll(ImmutableSet.of("A", "B", "C"));

    assertThat(listener.getCount()).isEqualTo(1);
    assertThat(list).containsExactly("A", "B", "C");
  }

  @Test
  public void listInvalidatedOnAddAllAtIndex() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    list.add("A");
    list.add("D");
    assertThat(listener.getCount()).isEqualTo(2);

    list.addAll(1, ImmutableSet.of("B", "C"));
    assertThat(listener.getCount()).isEqualTo(3);

    assertThat(list).containsExactly("A", "B", "C", "D");
  }

  @Test
  public void listInvalidatedOnIteratorRemove() {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    list.add("A");
    list.add("B");
    list.add("C");
    assertThat(listener.getCount()).isEqualTo(3);

    Iterator<String> iterator = list.iterator();
    iterator.next();
    iterator.remove();
    assertThat(list).containsExactly("B", "C");
    assertThat(listener.getCount()).isEqualTo(4);
  }

  @Test
  public void listInvalidatedOnListIteratorRemove() {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    list.add("A");
    list.add("B");
    list.add("C");
    assertThat(listener.getCount()).isEqualTo(3);

    ListIterator<String> iterator = list.listIterator();
    iterator.next();
    iterator.remove();
    assertThat(list).containsExactly("B", "C");
    assertThat(listener.getCount()).isEqualTo(4);
  }
  @Test
  public void listInvalidatedOnListIteratorSet() {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    list.add("A");
    list.add("B");
    list.add("C");
    assertThat(listener.getCount()).isEqualTo(3);

    ListIterator<String> iterator = list.listIterator();
    iterator.next();
    iterator.next();
    iterator.set("X");
    assertThat(list).containsExactly("A", "X", "C");
    assertThat(listener.getCount()).isEqualTo(4);
  }
  @Test
  public void listInvalidatedOnListIteratorAdd() {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    list.add("A");
    list.add("B");
    list.add("D");
    assertThat(listener.getCount()).isEqualTo(3);

    ListIterator<String> iterator = list.listIterator(2);
    iterator.add("C");
    assertThat(list).containsExactly("A", "B", "C", "D");
    assertThat(listener.getCount()).isEqualTo(4);
  }

  @Test
  public void listInvalidationDeferredIfUsingBeginEndUpdate() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    list.beginUpdate();
    assertThat(listener.getCount()).isEqualTo(0);
    list.add("A");
    list.add("B");
    list.add("C");
    assertThat(listener.getCount()).isEqualTo(0);
    list.endUpdate();
    assertThat(listener.getCount()).isEqualTo(1);
  }

  @Test
  public void beginUpdateCanBeCalledMultipleTimes() throws Exception {
    ObservableList<String> list = new ObservableList<>();
    CountListener listener = new CountListener();
    list.addListener(listener);

    list.beginUpdate();
    list.beginUpdate();
    list.beginUpdate();
    assertThat(listener.getCount()).isEqualTo(0);
    list.add("A");
    list.add("B");
    list.add("C");
    assertThat(listener.getCount()).isEqualTo(0);

    list.endUpdate();
    assertThat(listener.getCount()).isEqualTo(0);
    list.endUpdate();
    assertThat(listener.getCount()).isEqualTo(0);
    list.endUpdate();
    assertThat(listener.getCount()).isEqualTo(1);
  }

  @Test
  public void setAllReplacesTheCurrentList()  throws Exception {
    ObservableList<Integer> numericList = new ObservableList<>();
    numericList.add(1);
    numericList.add(2);
    numericList.add(3);

    CountListener listener = new CountListener();
    numericList.addListener(listener);

    numericList.setAll(Arrays.asList(10, 9, 8));

    assertThat(numericList).containsExactly(10, 9, 8);
    assertThat(listener.getCount()).isEqualTo(1);
  }

  @Test
  public void setAllWithEmptyCollectionCanClearTheCurrentList()  throws Exception {
    ObservableList<Integer> numericList = new ObservableList<>();
    numericList.add(1);
    numericList.add(2);
    numericList.add(3);

    CountListener listener = new CountListener();
    numericList.addListener(listener);

    numericList.setAll(ImmutableSet.<Integer>of());

    assertThat(numericList).isEmpty();
    assertThat(listener.getCount()).isEqualTo(1);
  }

  @Test(expected = IllegalStateException.class)
  public void listThrowsExceptionOnEndUpdateWithoutBeginUpdate() {
    ObservableList<String> list = new ObservableList<>();
    list.endUpdate();
  }
}