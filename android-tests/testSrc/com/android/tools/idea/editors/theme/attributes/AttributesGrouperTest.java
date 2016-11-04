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
package com.android.tools.idea.editors.theme.attributes;

import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AttributesGrouperTest {
  /**
   * Creates a list of {@link EditedStyleItem} mocks from the given list of pairs (attribute name, attribute group)
   */
  @NotNull
  private static List<EditedStyleItem> fromList(String... args) {
    assert args.length % 2 == 0;

    ImmutableList.Builder<EditedStyleItem> builder = ImmutableList.builder();
    for (int i = 0; i < args.length; i += 2) {
      String attributeName = args[i];
      String attributeGroup = args[i + 1];

      EditedStyleItem styleItem = mock(EditedStyleItem.class);
      when(styleItem.getName()).thenReturn(attributeName);
      when(styleItem.getAttrGroup()).thenReturn(attributeGroup);
      builder.add(styleItem);
    }

    return builder.build();
  }

  /**
   * Extracts the table label strings from the list of {@link TableLabel} objects
   */
  @NotNull
  private static List<String> getTableNames(@NotNull List<TableLabel> tableLabels) {
    return Lists.transform(tableLabels, new Function<TableLabel, String>() {
      @Nullable
      @Override
      public String apply(@Nullable TableLabel input) {
        assertNotNull(input);
        return input.getLabelName();
      }
    });
  }

  @Test
  public void testEmptySource() {
    ArrayList<EditedStyleItem> sink = Lists.newArrayList();
    List<TableLabel> tableLabels = AttributesGrouper.generateLabels(AttributesGrouper.GroupBy.GROUP, Collections.<EditedStyleItem>emptyList(), sink);
    assertThat(sink).isEmpty();
    assertThat(tableLabels).isEmpty();

    tableLabels = AttributesGrouper.generateLabels(AttributesGrouper.GroupBy.TYPE, Collections.<EditedStyleItem>emptyList(), sink);
    assertThat(sink).isEmpty();
    assertThat(tableLabels).isEmpty();
  }

  @Test
  public void testGenerateLabels() {
    ArrayList<EditedStyleItem> sink = Lists.newArrayList();
    List<EditedStyleItem> source = fromList(
      "colorItem", "Colors",
      "itemColor", "Colors",
      "theDrawableThing", "Drawables",
      "drawableStyle", "Styles",
      "otherThing", "Styles"
    );

    // Group by Attribute Group
    List<TableLabel> tableLabels = AttributesGrouper.generateLabels(AttributesGrouper.GroupBy.GROUP, source, sink);
    // Labels must be alphabetically sorted
    assertThat(getTableNames(tableLabels)).containsExactly("Colors", "Drawables", "Styles").inOrder();
    assertThat(tableLabels.get(0).getRowPosition()).isEqualTo(0);
    assertThat(tableLabels.get(1).getRowPosition()).isEqualTo(2); // Only 1 drawable
    assertThat(tableLabels.get(2).getRowPosition()).isEqualTo(3);
    sink.clear();

    // Group by Type
    tableLabels = AttributesGrouper.generateLabels(AttributesGrouper.GroupBy.TYPE, source, sink);
    // Labels are sorted with an arbitrary order
    assertThat(getTableNames(tableLabels)).containsExactly("Styles", "Colors", "Drawables", "Everything Else").inOrder();
    assertThat(tableLabels.get(0).getRowPosition()).isEqualTo(0);
    assertThat(tableLabels.get(1).getRowPosition()).isEqualTo(1);
    assertThat(tableLabels.get(2).getRowPosition()).isEqualTo(3);
    assertThat(tableLabels.get(3).getRowPosition()).isEqualTo(4);
  }

  @Test
  public void testEverythingElse() {
    ArrayList<EditedStyleItem> sink = Lists.newArrayList();
    List<EditedStyleItem> source = fromList(
      "item1", "Colors",
      "item2", "Colors",
      "item3", "Drawables",
      "otherThing", "Styles"
    );

    List<TableLabel> tableLabels = AttributesGrouper.generateLabels(AttributesGrouper.GroupBy.TYPE, source, sink);
    // Everything is in the "Everything Else" category so we just do not have headers
    assertThat(tableLabels).isEmpty();
    assertThat(sink).hasSize(4);
    sink.clear();

    source = fromList(
      "itemColor1", "Colors",
      "item2", "Colors",
      "item3", "Drawables",
      "otherThing", "Styles"
    );
    tableLabels = AttributesGrouper.generateLabels(AttributesGrouper.GroupBy.TYPE, source, sink);
    // Now we have one color so two labels should be generated
    assertThat(getTableNames(tableLabels)).containsExactly("Colors", "Everything Else").inOrder();
    assertThat(sink).hasSize(4);
  }
}