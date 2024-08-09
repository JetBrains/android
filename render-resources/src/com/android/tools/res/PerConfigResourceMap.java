/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.res;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.intellij.util.SmartList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Custom implementation of {@link ListMultimap} that may store multiple resource items for
 * the same folder configuration, but for readers exposes ot most one resource item per folder
 * configuration.
 *
 * <p>This ListMultimap implementation is not as robust as Guava multimaps but is sufficient
 * for MultiResourceRepository because the latter always copies data to immutable containers
 * before exposing it to callers.
 */
public class PerConfigResourceMap implements ListMultimap<String, ResourceItem> {
  private final Map<String, List<ResourceItem>> myMap = new LinkedHashMap<>();
  private int mySize = 0;
  @NonNull private final ResourceItemComparator myComparator;
  @Nullable private Values myValues;

  PerConfigResourceMap(@NonNull ResourceItemComparator comparator) {
    myComparator = comparator;
  }

  @Override
  public @NonNull List<ResourceItem> get(@Nullable String key) {
    List<ResourceItem> items = myMap.get(key);
    return items == null ? ImmutableList.of() : items;
  }

  @Override
  @NonNull
  public Set<String> keySet() {
    return myMap.keySet();
  }

  @Override
  @NonNull
  public Multiset<String> keys() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NonNull
  public Collection<ResourceItem> values() {
    Values values = myValues;
    if (values == null) {
      values = new Values();
      myValues = values;
    }
    return values;
  }

  @Override
  @NonNull
  public Collection<Map.Entry<String, ResourceItem>> entries() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull List<ResourceItem> removeAll(@Nullable Object key) {
    //noinspection SuspiciousMethodCalls
    List<ResourceItem> removed = myMap.remove(key);
    if (removed != null) {
      mySize -= removed.size();
    }
    return removed == null ? ImmutableList.of() : removed;
  }

  @SuppressWarnings("UnusedReturnValue")
  boolean removeIf(@NonNull String key, @NonNull Predicate<? super ResourceItem> filter) {
    List<ResourceItem> list = myMap.get(key);
    if (list == null) {
      return false;
    }
    int oldSize = list.size();
    boolean removed = list.removeIf(filter);
    mySize += list.size() - oldSize;
    if (list.isEmpty()) {
      myMap.remove(key);
    }
    return removed;
  }

  @Override
  public void clear() {
    myMap.clear();
    mySize = 0;
  }

  @Override
  public int size() {
    return mySize;
  }

  @Override
  public boolean isEmpty() {
    return mySize == 0;
  }

  @Override
  public boolean containsKey(@Nullable Object key) {
    //noinspection SuspiciousMethodCalls
    return myMap.containsKey(key);
  }

  @Override
  public boolean containsValue(@Nullable Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsEntry(@Nullable Object key, @Nullable Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean put(@NonNull String key, @NonNull ResourceItem item) {
    List<ResourceItem> list = myMap.computeIfAbsent(key, k -> new PerConfigResourceList());
    int oldSize = list.size();
    list.add(item);
    mySize += list.size() - oldSize;
    return true;
  }

  @Override
  public boolean remove(@Nullable Object key, @Nullable Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean putAll(@NonNull String key, @NonNull Iterable<? extends ResourceItem> items) {
    if (items instanceof Collection) {
      if (((Collection<?>)items).isEmpty()) {
        return false;
      }
      List<ResourceItem> list = myMap.computeIfAbsent(key, k -> new PerConfigResourceList());
      int oldSize = list.size();
      //noinspection unchecked
      boolean added = list.addAll((Collection<? extends ResourceItem>)items);
      mySize += list.size() - oldSize;
      return added;
    }

    boolean added = false;
    List<ResourceItem> list = null;
    int oldSize = 0;
    for (ResourceItem item : items) {
      if (list == null) {
        list = myMap.computeIfAbsent(key, k -> new PerConfigResourceList());
        oldSize = list.size();
      }
      added = list.add(item);
    }
    if (list != null) {
      mySize += list.size() - oldSize;
    }
    return added;
  }

  @Override
  public boolean putAll(Multimap<? extends String, ? extends ResourceItem> multimap) {
    for (Map.Entry<? extends String, ? extends Collection<? extends ResourceItem>> entry : multimap.asMap().entrySet()) {
      String key = entry.getKey();
      Collection<? extends ResourceItem> items = entry.getValue();
      if (!items.isEmpty()) {
        List<ResourceItem> list = myMap.computeIfAbsent(key, k -> new PerConfigResourceList());
        int oldSize = list.size();
        list.addAll(items);
        mySize += list.size() - oldSize;
      }
    }

    return !multimap.isEmpty();
  }

  @Override
  public @NonNull List<ResourceItem> replaceValues(@Nullable String key, @NonNull Iterable<? extends ResourceItem> values) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull Map<String, Collection<ResourceItem>> asMap() {
    //noinspection unchecked
    return (Map<String, Collection<ResourceItem>>)(Map<String, ?>)myMap;
  }

  /**
   * This class has a split personality. The class may store multiple resource items for the same
   * folder configuration, but for callers of non-mutating methods ({@link #get(int)},
   * {@link #size()}, {@link Iterator#next()}, etc) it exposes at most one resource item per
   * folder configuration. Which of the resource items with the same folder configuration is
   * visible to non-mutating methods is determined by {@link ResourceItemComparator.ResourcePriorityComparator}.
   */
  private class PerConfigResourceList extends AbstractList<ResourceItem> {
    /**
     * Resource items sorted by folder configurations. Nested lists are sorted by repository priority.
     */
    private final List<List<ResourceItem>> myResourceItems = new ArrayList<>();

    @Override
    @NonNull
    public ResourceItem get(int index) {
      return myResourceItems.get(index).get(0);
    }

    @Override
    public int size() {
      return myResourceItems.size();
    }

    @Override
    public boolean add(@NonNull ResourceItem item) {
      add(item, 0);
      return true;
    }

    @Override
    public boolean addAll(@NonNull Collection<? extends ResourceItem> items) {
      if (items.isEmpty()) {
        return false;
      }
      if (items.size() == 1) {
        return add(items.iterator().next());
      }

      List<ResourceItem> sortedItems = sortedItems(items);
      int start = 0;
      for (ResourceItem item : sortedItems) {
        start = add(item, start);
      }
      return true;
    }

    private int add(ResourceItem item, int start) {
      int index = findConfigIndex(item, start, myResourceItems.size());
      if (index < 0) {
        index = ~index;
        myResourceItems.add(index, new SmartList<>(item));
      }
      else {
        List<ResourceItem> nested = myResourceItems.get(index);
        // Iterate backwards since it is likely to require fewer iterations.
        int i = nested.size();
        while (--i >= 0) {
          if (myComparator.myPriorityComparator.compare(item, nested.get(i)) > 0) {
            break;
          }
        }
        nested.add(i + 1, item);
      }
      return index;
    }

    @Override
    public void clear() {
      myResourceItems.clear();
    }

    @Override
    public boolean remove(@Nullable Object item) {
      assert item != null;
      int index = remove((ResourceItem)item, myResourceItems.size());
      return index >= 0;
    }

    @Override
    public boolean removeAll(@NonNull Collection<?> items) {
      if (items.isEmpty()) {
        return false;
      }
      if (items.size() == 1) {
        return remove(items.iterator().next());
      }

      @SuppressWarnings("unchecked")
      List<ResourceItem> itemsToDelete = sortedItems((Collection<? extends ResourceItem>)items);
      boolean modified = false;
      int end = myResourceItems.size();
      for (int i = itemsToDelete.size(); --i >= 0; ) {
        int index = remove(itemsToDelete.get(i), end);
        if (index > 0) {
          modified = true;
          end = index;
        }
        else {
          end = ~index;
        }
      }
      return modified;
    }

    @Override
    public boolean removeIf(@NonNull Predicate<? super ResourceItem> filter) {
      boolean removed = false;
      for (int i = myResourceItems.size(); --i >= 0; ) {
        List<ResourceItem> nested = myResourceItems.get(i);
        for (int j = nested.size(); --j >= 0; ) {
          ResourceItem item = nested.get(j);
          if (filter.test(item)) {
            nested.remove(j);
            removed = true;
          }
        }
        if (nested.isEmpty()) {
          myResourceItems.remove(i);
        }
      }
      return removed;
    }

    /**
     * Removes the given resource item from the first {@code end} elements of {@link #myResourceItems}.
     *
     * @param item the resource item to remove
     * @param end  the exclusive end of the range checked for existence of the item being deleted
     * @return if the item to be deleted was found, returns its index, otherwise returns
     * the binary complement of the index pointing to where the item would be inserted
     */
    private int remove(@NonNull ResourceItem item, int end) {
      int index = findConfigIndex(item, 0, end);
      if (index < 0) {
        return index;
      }

      List<ResourceItem> nested = myResourceItems.get(index);
      if (!nested.remove(item)) {
        return ~(index + 1);
      }

      if (nested.isEmpty()) {
        myResourceItems.remove(index);
        return index;
      }
      return index + 1;
    }

    @NonNull
    private List<ResourceItem> sortedItems(@NonNull Collection<? extends ResourceItem> items) {
      List<ResourceItem> sortedItems = new ArrayList<>(items);
      sortedItems.sort(myComparator);
      return sortedItems;
    }

    /**
     * Returns index in {@link #myResourceItems} of the existing resource item with the same
     * configuration as the {@code item} parameter. If {@link #myResourceItems} doesn't contains
     * resources with the same configuration, returns binary complement of the insertion point.
     */
    private int findConfigIndex(@NonNull ResourceItem item, int start, int end) {
      FolderConfiguration config = item.getConfiguration();
      int low = start;
      int high = end;

      while (low < high) {
        int mid = (low + high) >>> 1;
        FolderConfiguration value = myResourceItems.get(mid).get(0).getConfiguration();
        int c = value.compareTo(config);
        if (c < 0) {
          low = mid + 1;
        }
        else if (c > 0) {
          high = mid;
        }
        else {
          return mid;
        }
      }
      return ~low; // Not found.
    }
  }

  private class Values extends AbstractCollection<ResourceItem> {
    @Override
    public @NonNull Iterator<ResourceItem> iterator() {
      return new ValuesIterator();
    }

    @Override
    public int size() {
      return mySize;
    }

    private class ValuesIterator implements Iterator<ResourceItem> {
      private final Iterator<List<ResourceItem>> myOuterCursor = myMap.values().iterator();
      private List<ResourceItem> myCurrentList;
      private int myInnerCursor;

      @Override
      public boolean hasNext() {
        return myCurrentList != null || myOuterCursor.hasNext();
      }

      @Override
      public ResourceItem next() {
        if (myCurrentList == null) {
          myCurrentList = myOuterCursor.next();
          myInnerCursor = 0;
        }
        try {
          ResourceItem item = myCurrentList.get(myInnerCursor);
          if (++myInnerCursor >= myCurrentList.size()) {
            myCurrentList = null;
          }
          return item;
        }
        catch (IndexOutOfBoundsException e) {
          throw new NoSuchElementException();
        }
      }
    }
  }

  public static class ResourceItemComparator implements Comparator<ResourceItem> {
    private final Comparator<ResourceItem> myPriorityComparator;

    ResourceItemComparator(@NonNull Collection<SingleNamespaceResourceRepository> repositories) {
      myPriorityComparator = new ResourcePriorityComparator(repositories);
    }

    @Override
    public int compare(@NonNull ResourceItem item1, @NonNull ResourceItem item2) {
      int c = item1.getConfiguration().compareTo(item2.getConfiguration());
      if (c != 0) {
        return c;
      }
      return myPriorityComparator.compare(item1, item2);
    }

    private static class ResourcePriorityComparator implements Comparator<ResourceItem> {
      private final Object2IntMap<SingleNamespaceResourceRepository> repositoryOrdering;

      ResourcePriorityComparator(@NonNull Collection<SingleNamespaceResourceRepository> repositories) {
        repositoryOrdering = new Object2IntOpenHashMap<>(repositories.size());
        int i = 0;
        for (SingleNamespaceResourceRepository repository : repositories) {
          repositoryOrdering.put(repository, i++);
        }
      }

      @Override
      public int compare(@NonNull ResourceItem item1, @NonNull ResourceItem item2) {
        return Integer.compare(getOrdering(item1), getOrdering(item2));
      }

      private int getOrdering(@NonNull ResourceItem item) {
        int ordering = repositoryOrdering.getInt(item.getRepository());
        assert ordering >= 0;
        return ordering;
      }
    }
  }
}
