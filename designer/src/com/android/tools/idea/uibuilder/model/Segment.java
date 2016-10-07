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
package com.android.tools.idea.uibuilder.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A segment is a straight horizontal or vertical line between two points, typically an
 * edge of a node but also possibly some internal segment like a baseline or a center
 * line, and it can be offset by a margin from the node's visible bounds.
 */
public class Segment {
  /**
   * For horizontal lines, the y coordinate; for vertical lines the x
   */
  public final int at;

  /**
   * The starting coordinate along the line
   */
  public final int from;

  /**
   * The ending coordinate along the line
   */
  public final int to;

  /**
   * Whether the edge is a top edge, a baseline edge, a left edge, etc
   */
  @NotNull
  public final SegmentType edgeType;

  /**
   * Whether the edge is offset from the node by a margin or not, or whether it has no
   * margin
   */
  @NotNull
  public final MarginType marginType;

  /**
   * The node that contains this edge
   */
  @Nullable
  public final NlComponent component;

  /**
   * The id of the node. May be null (in which case id should be generated when
   * move/resize is completed
   */
  @Nullable
  public final String id;

  public Segment(int at,
                 int from,
                 int to,
                 @Nullable NlComponent component,
                 @Nullable String id,
                 @NotNull SegmentType edgeType,
                 @NotNull MarginType marginType) {
    this.at = at;
    this.from = from;
    this.to = to;
    this.component = component;
    this.id = id;
    this.edgeType = edgeType;
    this.marginType = marginType;
  }

  @NotNull
  @Override
  public String toString() {
    String componentStr = component == null ? "null" : component.getTagName().substring(
      component.getTagName().lastIndexOf(('.')) + 1);
    return "Segment [edgeType=" + edgeType + ", component=" + componentStr + ", at=" + at + ", id=" + id
           + ", from=" + from + ", to=" + to + ", marginType=" + marginType + "]";
  }
}
