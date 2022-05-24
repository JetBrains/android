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
package com.android.tools.idea.common.util;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.google.common.base.MoreObjects;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class NlTreeDumper {
  private final Map<NlComponent, Integer> myComponentIds;

  private final boolean myIncludeIdentity;
  private final boolean myShowBoundaries;

  public NlTreeDumper() {
    this(true, true);
  }

  public NlTreeDumper(boolean includeIdentity, boolean showBoundaries) {
    myComponentIds = includeIdentity ? new HashMap<>() : Collections.emptyMap();
    myIncludeIdentity = includeIdentity;
    myShowBoundaries = showBoundaries;
  }

  /**
   * Dumps out the component tree, recursively
   *
   * @param roots set of root components
   * @return a string representation of the component tree
   */
  @NotNull
  public static String dumpTree(@NotNull List<NlComponent> roots) {
    return new NlTreeDumper(false, true).toTree(roots);
  }

  /**
   * Recursively dumps out the component tree and includes an instance ID next to each component.
   * The {@link NlComponent}s will be represented by the same ID if this method is used multiple times.
   *
   * @param roots set of root components
   * @return a string representation of the component tree
   */
  @NotNull
  public String toTree(@NotNull List<NlComponent> roots) {
    StringBuilder sb = new StringBuilder(200);
    for (NlComponent root : roots) {
      describe(sb, root, 0, new HashSet<>());
    }
    return sb.toString().trim();
  }

  private void describe(@NotNull StringBuilder sb, @NotNull NlComponent component, int depth, @NotNull HashSet<NlComponent> visited) {
    boolean isLoop = !visited.add(component);
    for (int i = 0; i < depth; i++) {
      sb.append("    ");
    }
    if (isLoop) {
      // This is an error in the model. Mark it to easily be able to trace it.
      sb.append("!!LOOP!! ");
    }
    sb.append(describe(component))
      .append('\n');

    if (isLoop) {
      // Avoid infinite loop
      return;
    }
    for (NlComponent child : component.getChildren()) {
      describe(sb, child, depth + 1, visited);
    }
  }

  @NotNull
  private String describe(@NotNull NlComponent root) {

    MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(root).omitNullValues()
      .add("tag", "<" + root.getTagName() + ">");
    if (myShowBoundaries && NlComponentHelperKt.getHasNlComponentInfo(root)) {
      helper.add("bounds", "[" +
                           NlComponentHelperKt.getX(root) +
                           "," +
                           NlComponentHelperKt.getY(root) +
                           ":" +
                           NlComponentHelperKt.getW(root) +
                           "x" +
                           NlComponentHelperKt.getH(root));
    }
    if (myIncludeIdentity) {
      helper.add("instance", getInstanceId(root));
    }
    return helper.toString();
  }

  private int getInstanceId(@NotNull NlComponent root) {
    Integer id = myComponentIds.get(root);
    if (id == null) {
      id = myComponentIds.size();
      myComponentIds.put(root, id);
    }
    return id;
  }
}
