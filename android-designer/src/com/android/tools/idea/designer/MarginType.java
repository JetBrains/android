/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.designer;

/**
 * A {@link MarginType} indicates whether a {@link Segment} corresponds to the visual edge
 * of the node, or whether it is offset by a margin in the edge's direction, or whether
 * it's both (which is the case when the margin is 0).
 * <p/>
 * We need to keep track of the distinction because different constraints apply
 * differently w.r.t. margins. Let's say you have a target node with a 50 dp margin in all
 * directions. If you layout_alignTop with this node, the match will be on the visual
 * bounds of the target node (ignoring the margin). If you layout_above this node, you
 * will be offset by the margin on the target node. Therefore, we have to add <b>both</b>
 * edges (the bounds of the target node with and without edges) and check for matches on
 * each edge depending on the constraint being considered.
 */
public enum MarginType {
  /**
   * This margin type is used for nodes that have margins, and this segment includes the
   * margin distance
   */
  WITH_MARGIN,

  /**
   * This margin type is used for nodes that have margins, and this segment does not
   * include the margin distance
   */
  WITHOUT_MARGIN,

  /**
   * This margin type is used for nodes that do not have margins, so margin edges and
   * non-margin edges are the same
   */
  NO_MARGIN
}
