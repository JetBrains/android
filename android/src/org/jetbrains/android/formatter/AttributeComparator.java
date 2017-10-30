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
package org.jetbrains.android.formatter;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.function.Function;

/**
 * An Android XML attribute comparator.
 *
 * @param <A> an arbitrary attribute type. The local parts of the attribute names are retrieved with the Function argument passed to the
 *            constructor.
 */
final class AttributeComparator<A> implements Comparator<A> {
  /**
   * A group of attributes. The sub-values of a group have their own sort order within the group.
   */
  private enum Group implements Comparator<String> {
    LAYOUT_PARAM {
      @NotNull
      @Override
      Object[] subValues() {
        return LayoutParam.values();
      }

      @Override
      public int compare(@NotNull String attribute1, @NotNull String attribute2) {
        return LayoutParam.valueOf(attribute1).compareTo(LayoutParam.valueOf(attribute2));
      }
    },
    MARGIN_LAYOUT_PARAM {
      @NotNull
      @Override
      Object[] subValues() {
        return MarginLayoutParam.values();
      }

      @Override
      public int compare(@NotNull String attribute1, @NotNull String attribute2) {
        return MarginLayoutParam.valueOf(attribute1).compareTo(MarginLayoutParam.valueOf(attribute2));
      }
    },
    GRID_LAYOUT_PARAM {
      @NotNull
      @Override
      Object[] subValues() {
        return GridLayoutParam.values();
      }

      @Override
      public int compare(@NotNull String attribute1, @NotNull String attribute2) {
        return GridLayoutParam.valueOf(attribute1).compareTo(GridLayoutParam.valueOf(attribute2));
      }
    },
    RELATIVE_LAYOUT_PARAM {
      @NotNull
      @Override
      Object[] subValues() {
        return RelativeLayoutParam.values();
      }

      @Override
      public int compare(@NotNull String attribute1, @NotNull String attribute2) {
        return RelativeLayoutParam.valueOf(attribute1).compareTo(RelativeLayoutParam.valueOf(attribute2));
      }
    },
    OTHER {
      @NotNull
      @Override
      Object[] subValues() {
        return new Object[0];
      }

      @Override
      public int compare(@NotNull String attribute1, String attribute2) {
        return attribute1.compareTo(attribute2);
      }
    };

    private static ImmutableMap<String, Group> ourAttributeToGroupMap;

    @NotNull
    private static ImmutableMap<String, Group> initAttributeToGroupMap() {
      ImmutableMap.Builder<String, Group> builder = new ImmutableMap.Builder<>();

      for (Group group : values()) {
        for (Object subValue : group.subValues()) {
          builder.put(subValue.toString(), group);
        }
      }

      return builder.build();
    }

    @NotNull
    abstract Object[] subValues();

    @NotNull
    private static Group getGroup(@NotNull String attribute) {
      if (ourAttributeToGroupMap == null) {
        ourAttributeToGroupMap = initAttributeToGroupMap();
      }

      return ourAttributeToGroupMap.getOrDefault(attribute, OTHER);
    }
  }

  private enum LayoutParam {
    layout_width,
    layout_height
  }

  private enum MarginLayoutParam {
    layout_margin,
    layout_marginHorizontal,
    layout_marginVertical,
    layout_marginStart,
    layout_marginLeft,
    layout_marginTop,
    layout_marginEnd,
    layout_marginRight,
    layout_marginBottom
  }

  private enum GridLayoutParam {
    layout_row,
    layout_rowSpan,
    layout_rowWeight,
    layout_column,
    layout_columnSpan,
    layout_columnWeight,
    layout_gravity
  }

  private enum RelativeLayoutParam {
    layout_alignWithParentIfMissing,
    layout_toStartOf,
    layout_toLeftOf,
    layout_toEndOf,
    layout_toRightOf,
    layout_above,
    layout_below,
    layout_alignBaseline,
    layout_alignStart,
    layout_alignLeft,
    layout_alignTop,
    layout_alignEnd,
    layout_alignRight,
    layout_alignBottom,
    layout_alignParentStart,
    layout_alignParentLeft,
    layout_alignParentTop,
    layout_alignParentEnd,
    layout_alignParentRight,
    layout_alignParentBottom,
    layout_centerInParent,
    layout_centerHorizontal,
    layout_centerVertical
  }

  private final Function<A, String> myGetLocalPart;

  /**
   * @param getLocalPart returns the local part of a qualified attribute name
   */
  AttributeComparator(@NotNull Function<A, String> getLocalPart) {
    myGetLocalPart = getLocalPart;
  }

  @Override
  public int compare(A attribute1, A attribute2) {
    String part1 = myGetLocalPart.apply(attribute1);
    Group group1 = Group.getGroup(part1);

    String part2 = myGetLocalPart.apply(attribute2);
    Group group2 = Group.getGroup(part2);

    int result = group1.compareTo(group2);
    return result == 0 ? group1.compare(part1, part2) : result;
  }
}
