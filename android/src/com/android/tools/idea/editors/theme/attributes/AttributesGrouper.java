/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.attributes;

import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.google.common.base.Supplier;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.intellij.openapi.util.text.StringUtil;

import java.util.*;

import org.jetbrains.annotations.NotNull;

public class AttributesGrouper {
  private AttributesGrouper() { }

  public enum GroupBy {
    GROUP("By Group"),
    TYPE("By Type");

    final private String myText;

    GroupBy(String text) {
      myText = text;
    }

    @Override
    public String toString() {
      return myText;
    }
  }

  /**
   * Helper data structure to hold information for (temporary) algorithm for splitting attributes
   * to labelled group.
   */
  private enum Group {
    STYLES("Styles", ImmutableList.of("style", "theme")),
    COLORS("Colors", ImmutableList.of("color")),
    DRAWABLES("Drawables", ImmutableList.of("drawable")),
    METRICS("Metrics", ImmutableList.of("size", "width", "height")),
    OTHER("Everything Else", Collections.<String>emptyList());

    /**
     * Group name, as appears on properties panel
     */
    public final String name;
    /**
     * To determine a group for attribute, every marker from this list is sought in
     * attribute name (case-insensitively)
     */
    public final List<String> markers;

    Group(@NotNull String name, @NotNull List<String> markers) {
      this.name = name;
      this.markers = markers;
    }

    private static Group getGroupFromName(String name) {
      for (Group group : Group.values()) {
        for (final String marker : group.markers) {
          if (StringUtil.containsIgnoreCase(name, marker)) {
            return group;
          }
        }
      }
      return OTHER;
    }
  }


  @NotNull
  private static List<TableLabel> generateLabelsForType(@NotNull final List<EditedStyleItem> source, @NotNull final List<EditedStyleItem> sink) {
    // ArrayListMultimap is used to ensure the elements stay sorted
    final Multimap<Group, EditedStyleItem> classes = ArrayListMultimap.create();

    for (final EditedStyleItem item : source) {
      final String name = item.getAttrName();
      classes.put(Group.getGroupFromName(name), item);
    }

    final List<TableLabel> labels = new ArrayList<TableLabel>();
    int offset = 0;
    for (Group group : Group.values()) {
      Collection<EditedStyleItem> elements = classes.get(group);

      boolean addHeader = !elements.isEmpty();
      if (addHeader && group == Group.OTHER) {
        // Adding "Everything else" label only in case when there are at least one other label,
        // because having "Everything else" as the only label present looks quite silly
        addHeader = offset != 0;
      }
      if (addHeader) {
        labels.add(new TableLabel(group.name, offset));
      }

      sink.addAll(elements);

      offset += elements.size();
    }

    return labels;
  }

  @NotNull
  private static List<TableLabel> generateLabelsForGroup(final List<EditedStyleItem> source, final List<EditedStyleItem> sink) {
    // A TreeMap is used to ensure the keys are sorted in alphabetical order
    // ArrayLists are used for values to ensure that they stay in the same order they came in
    Multimap<String, EditedStyleItem> classes =
      Multimaps.newListMultimap(new TreeMap<String, Collection<EditedStyleItem>>(), new Supplier<List<EditedStyleItem>>() {
        @Override
        public List<EditedStyleItem> get() {
          return new ArrayList<EditedStyleItem>();
        }
      });
    for (EditedStyleItem item : source){
      String group = item.getAttrGroup();
      classes.put(group, item);
    }

    final List<TableLabel> labels = new ArrayList<TableLabel>();
    int offset = 0;
    sink.clear();
    for (String group : classes.keySet()) {
      final int size = classes.get(group).size();
      sink.addAll(classes.get(group));
      if (size != 0) {
        labels.add(new TableLabel(group, offset));
      }
      offset += size;
    }
    return labels;
  }

  @NotNull
  public static List<TableLabel> generateLabels(@NotNull GroupBy group, final List<EditedStyleItem> source, final List<EditedStyleItem> sink) {
    switch(group) {
      case TYPE:
        return generateLabelsForType(source, sink);
      case GROUP:
        return generateLabelsForGroup(source, sink);

      default:
        throw new IllegalArgumentException();
    }
  }
}
