/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public final class AttributeComparatorTest {
  @Test
  public void marginLayout() {
    Collection<String> expected = ImmutableList.of(
      "layout_width",
      "layout_height",
      "layout_margin",
      "layout_marginHorizontal",
      "layout_marginVertical",
      "layout_marginStart",
      "layout_marginLeft",
      "layout_marginTop",
      "layout_marginEnd",
      "layout_marginRight",
      "layout_marginBottom",
      "width",
      "height",
      "layerType",
      "longClickable",
      "minWidth",
      "minHeight",
      "nextClusterForward",
      "nextFocusLeft",
      "nextFocusRight",
      "nextFocusUp",
      "nextFocusDown",
      "onClick",
      "outlineSpotShadowColor",
      "padding",
      "paddingHorizontal",
      "paddingVertical",
      "paddingStart",
      "paddingLeft",
      "paddingTop",
      "paddingEnd",
      "paddingRight",
      "paddingBottom",
      "requiresFadingEdge"
    );

    List<String> actual = new ArrayList<>(expected.size());

    actual.add("height");
    actual.add("layout_marginTop");
    actual.add("nextFocusLeft");
    actual.add("nextFocusUp");
    actual.add("paddingEnd");
    actual.add("nextFocusDown");
    actual.add("paddingLeft");
    actual.add("layout_marginVertical");
    actual.add("paddingTop");
    actual.add("onClick");
    actual.add("width");
    actual.add("requiresFadingEdge");
    actual.add("paddingBottom");
    actual.add("layout_width");
    actual.add("paddingHorizontal");
    actual.add("layout_height");
    actual.add("minWidth");
    actual.add("minHeight");
    actual.add("paddingRight");
    actual.add("paddingStart");
    actual.add("layout_marginHorizontal");
    actual.add("layout_marginStart");
    actual.add("layout_marginRight");
    actual.add("layerType");
    actual.add("paddingVertical");
    actual.add("layout_marginLeft");
    actual.add("longClickable");
    actual.add("padding");
    actual.add("layout_margin");
    actual.add("layout_marginBottom");
    actual.add("nextFocusRight");
    actual.add("nextClusterForward");
    actual.add("outlineSpotShadowColor");
    actual.add("layout_marginEnd");

    actual.sort(new AttributeComparator<>(Function.identity()));
    assertEquals(expected, actual);
  }

  @Test
  public void gridLayout() {
    Collection<String> expected = ImmutableList.of(
      "layout_width",
      "layout_height",
      "layout_row",
      "layout_rowSpan",
      "layout_rowWeight",
      "layout_column",
      "layout_columnSpan",
      "layout_columnWeight",
      "layout_gravity",
      "width",
      "height",
      "layerType",
      "longClickable",
      "minWidth",
      "minHeight",
      "nextClusterForward",
      "nextFocusLeft",
      "nextFocusRight",
      "nextFocusUp",
      "nextFocusDown",
      "onClick",
      "outlineSpotShadowColor",
      "padding",
      "paddingHorizontal",
      "paddingVertical",
      "paddingStart",
      "paddingLeft",
      "paddingTop",
      "paddingEnd",
      "paddingRight",
      "paddingBottom",
      "requiresFadingEdge"
    );

    List<String> actual = new ArrayList<>(expected.size());

    actual.add("layerType");
    actual.add("paddingStart");
    actual.add("layout_rowWeight");
    actual.add("nextFocusUp");
    actual.add("paddingHorizontal");
    actual.add("layout_height");
    actual.add("layout_width");
    actual.add("nextFocusRight");
    actual.add("paddingBottom");
    actual.add("width");
    actual.add("onClick");
    actual.add("layout_rowSpan");
    actual.add("paddingVertical");
    actual.add("minHeight");
    actual.add("minWidth");
    actual.add("padding");
    actual.add("height");
    actual.add("paddingTop");
    actual.add("nextFocusLeft");
    actual.add("paddingRight");
    actual.add("paddingLeft");
    actual.add("layout_columnWeight");
    actual.add("layout_column");
    actual.add("layout_row");
    actual.add("outlineSpotShadowColor");
    actual.add("nextFocusDown");
    actual.add("longClickable");
    actual.add("layout_columnSpan");
    actual.add("layout_gravity");
    actual.add("paddingEnd");
    actual.add("nextClusterForward");
    actual.add("requiresFadingEdge");

    actual.sort(new AttributeComparator<>(Function.identity()));
    assertEquals(expected, actual);
  }

  @Test
  public void relativeLayout() {
    Collection<String> expected = ImmutableList.of(
      "layout_width",
      "layout_height",
      "layout_alignWithParentIfMissing",
      "layout_above",
      "layout_below",
      "layout_alignBaseline",
      "layout_alignStart",
      "layout_alignLeft",
      "layout_alignTop",
      "layout_alignEnd",
      "layout_alignRight",
      "layout_alignBottom",
      "layout_alignParentStart",
      "layout_alignParentLeft",
      "layout_alignParentTop",
      "layout_alignParentEnd",
      "layout_alignParentRight",
      "layout_alignParentBottom",
      "layout_centerInParent",
      "layout_centerHorizontal",
      "layout_centerVertical",
      "layout_toStartOf",
      "layout_toLeftOf",
      "layout_toEndOf",
      "layout_toRightOf",
      "width",
      "height",
      "layerType",
      "longClickable",
      "minWidth",
      "minHeight",
      "nextClusterForward",
      "nextFocusLeft",
      "nextFocusRight",
      "nextFocusUp",
      "nextFocusDown",
      "onClick",
      "outlineSpotShadowColor",
      "padding",
      "paddingHorizontal",
      "paddingVertical",
      "paddingStart",
      "paddingLeft",
      "paddingTop",
      "paddingEnd",
      "paddingRight",
      "paddingBottom",
      "requiresFadingEdge"
    );

    List<String> actual = new ArrayList<>(expected.size());

    actual.add("layout_centerHorizontal");
    actual.add("layout_alignTop");
    actual.add("layout_centerVertical");
    actual.add("nextFocusUp");
    actual.add("layout_alignParentTop");
    actual.add("height");
    actual.add("padding");
    actual.add("layout_alignParentEnd");
    actual.add("paddingLeft");
    actual.add("layout_alignRight");
    actual.add("longClickable");
    actual.add("layout_alignParentRight");
    actual.add("layout_toStartOf");
    actual.add("layout_above");
    actual.add("layout_alignParentStart");
    actual.add("paddingHorizontal");
    actual.add("layout_alignParentLeft");
    actual.add("layout_below");
    actual.add("paddingRight");
    actual.add("onClick");
    actual.add("layout_centerInParent");
    actual.add("layout_alignWithParentIfMissing");
    actual.add("layout_alignStart");
    actual.add("layout_alignBaseline");
    actual.add("layout_height");
    actual.add("layout_alignEnd");
    actual.add("nextClusterForward");
    actual.add("paddingBottom");
    actual.add("layout_toEndOf");
    actual.add("paddingVertical");
    actual.add("nextFocusRight");
    actual.add("layout_toRightOf");
    actual.add("layout_width");
    actual.add("layout_alignBottom");
    actual.add("nextFocusLeft");
    actual.add("paddingTop");
    actual.add("layout_alignParentBottom");
    actual.add("layout_alignLeft");
    actual.add("layerType");
    actual.add("outlineSpotShadowColor");
    actual.add("minWidth");
    actual.add("requiresFadingEdge");
    actual.add("width");
    actual.add("nextFocusDown");
    actual.add("layout_toLeftOf");
    actual.add("paddingStart");
    actual.add("paddingEnd");
    actual.add("minHeight");

    actual.sort(new AttributeComparator<>(Function.identity()));
    assertEquals(expected, actual);
  }
}
