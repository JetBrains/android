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
package com.android.tools.idea.observable.collections;

import com.android.tools.idea.observable.SettableValue;
import com.android.tools.idea.observable.AbstractObservableValue;
import com.google.common.collect.ForwardingIterator;
import com.google.common.collect.ForwardingListIterator;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * A {@link List} which notifies any listeners anytime its collection is modified.
 * <p/>
 * You may use {@link #beginUpdate()} and {@link #endUpdate()} to batch many separate changes
 * together.
 */
public final class ObservableList<E> extends AbstractObservableValue<List<? extends E>> implements List<E>,
                                                                                                   SettableValue<List<? extends E>> {
  @NotNull private List<E> myInnerList;
  private int myUpdateCount;
  private boolean myInvalidatedWhileUpdating;

  public ObservableList() {
    myInnerList = new ArrayList<>();
  }

  public ObservableList(@NotNull Iterable<? extends E> otherCollection) {
    myInnerList = Lists.newArrayList(otherCollection);
  }

  /**
   * Begin batching changes. While an observable list is mid-update, invalidation events won't
   * fire - instead, when {@link #endUpdate()} is finally called, one accumulative event will fire.
   * <p/>
   * You may call {@link #beginUpdate()} multiple times in a row, as long as you also call the same
   * number of matching {@link #endUpdate()} calls later.
   */
  public void beginUpdate() {
    ++myUpdateCount;
  }

  /**
   * Finish a batch update started by {@link #beginUpdate()}.
   */
  public void endUpdate() {
    if (myUpdateCount == 0) {
      throw new IllegalStateException("Can't call ObservableList.endUpdate without matching beginUpdate");
    }

    --myUpdateCount;

    if (myUpdateCount == 0 && myInvalidatedWhileUpdating) {
      myInvalidatedWhileUpdating = false;
      notifyInvalidated();
    }
  }

  private void notifyContentsChanged() {
    if (myUpdateCount > 0) {
      myInvalidatedWhileUpdating = true;
      return;
    }

    notifyInvalidated();
  }

  @Override
  public int size() {
    return myInnerList.size();
  }

  @Override
  public boolean isEmpty() {
    return myInnerList.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return myInnerList.contains(o);
  }

  @NotNull
  @Override
  public Iterator<E> iterator() {
    return new ObservableIterator(myInnerList.iterator());
  }

  @NotNull
  @Override
  public Object[] toArray() {
    return myInnerList.toArray();
  }

  @NotNull
  @Override
  public <T> T[] toArray(T[] a) {
    return myInnerList.toArray(a);
  }

  @Override
  public boolean add(E e) {

    boolean added = myInnerList.add(e);
    if (added) {
      notifyContentsChanged();
    }
    return added;
  }

  @Override
  public boolean remove(Object o) {
    boolean removed = myInnerList.remove(o);
    if (removed) {
      notifyContentsChanged();
    }
    return removed;
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return myInnerList.containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    boolean added = myInnerList.addAll(c);
    if (added) {
      notifyContentsChanged();
    }
    return added;
  }

  /**
   * Convenience method - same as {@link #clear()} followed by {@link #addAll(Collection)} but only
   * triggers a single invalidation.
   */
  public boolean setAll(@NotNull Collection<? extends E> c) {
    beginUpdate();

    boolean cleared = false;
    if (!myInnerList.isEmpty()) {
      myInnerList.clear();
      cleared = true;
    }

    boolean added = myInnerList.addAll(c);
    boolean changed = cleared || added;
    if (changed) {
      notifyContentsChanged();
    }
    endUpdate();

    return changed;
  }

  @Override
  public boolean addAll(int index, Collection<? extends E> c) {
    boolean added = myInnerList.addAll(index, c);
    if (added) {
      notifyContentsChanged();
    }
    return added;
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    boolean removed = myInnerList.removeAll(c);
    if (removed) {
      notifyContentsChanged();
    }
    return removed;
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    boolean retained = myInnerList.retainAll(c);
    if (retained) {
      notifyContentsChanged();
    }
    return retained;
  }

  @Override
  public void clear() {
    if (myInnerList.isEmpty()) {
      return;
    }

    myInnerList.clear();
    notifyContentsChanged();
  }

  @Nullable
  @Override
  public E get(int index) {
    return myInnerList.get(index);
  }

  @Nullable
  @Override
  public E set(int index, E element) {
    E result = myInnerList.set(index, element);
    notifyContentsChanged();
    return result;
  }

  @Override
  public void add(int index, E element) {
    myInnerList.add(index, element);
    notifyContentsChanged();
  }

  @Nullable
  @Override
  public E remove(int index) {
    E result = myInnerList.remove(index);
    notifyContentsChanged();
    return result;
  }

  @Override
  public int indexOf(Object o) {
    return myInnerList.indexOf(o);
  }

  @Override
  public int lastIndexOf(Object o) {
    return myInnerList.lastIndexOf(o);
  }

  @NotNull
  @Override
  public ListIterator<E> listIterator() {
    return new ObservableListIterator(myInnerList.listIterator());
  }

  @NotNull
  @Override
  public ListIterator<E> listIterator(int index) {
    return new ObservableListIterator(myInnerList.listIterator(index));
  }

  @NotNull
  @Override
  public List<E> subList(int fromIndex, int toIndex) {
    return myInnerList.subList(fromIndex, toIndex);
  }

  @Override
  public int hashCode() {
    return myInnerList.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (myInnerList.equals(o)) return true;

    if (o != null && o instanceof ObservableList) {
      return ((ObservableList)o).myInnerList.equals(myInnerList);
    }

    return false;
  }

  @Override
  public String toString() {
    return myInnerList.toString();
  }

  @NotNull
  @Override
  public List<E> get() {
    return myInnerList;
  }

  @Override
  public void set(@NotNull List<? extends E> value) {
    setAll(value);
  }

  private class ObservableIterator extends ForwardingIterator<E> {
    private final Iterator<E> myInner;

    public ObservableIterator(@NotNull Iterator<E> inner) {
      myInner = inner;
    }

    @Override
    protected Iterator<E> delegate() {
      return myInner;
    }

    @Override
    public void remove() {
      myInner.remove();
      notifyContentsChanged();
    }
  }

  private class ObservableListIterator extends ForwardingListIterator<E> {
    @NotNull private final ListIterator<E> myInner;

    public ObservableListIterator(@NotNull ListIterator<E> inner) {
      myInner = inner;
    }

    @Override
    protected ListIterator<E> delegate() {
      return myInner;
    }

    @Override
    public void remove() {
      myInner.remove();
      notifyContentsChanged();
    }

    @Override
    public void set(@NotNull E e) {
      myInner.set(e);
      notifyContentsChanged();
    }

    @Override
    public void add(@NotNull E e) {
      myInner.add(e);
      notifyContentsChanged();
    }
  }
}
