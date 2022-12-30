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
package com.android.tools.swingp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * A side-channel per-thread local storage to store fragments of Swing hierarchy information for Swing hierarchy tree serialization.
 * <p>
 * Render passes over a Swing tree are sparse, skipping some elements, so this class provides the path between the current
 * node and the most recently rendered parent.
 */
final class JComponentTreeManager {
  // It's theoretically possible to have more than one Swing EDTs.
  private static final ThreadLocal<Stack<JComponent>> ourStack = ThreadLocal.withInitial(() -> new Stack<>());
  private static boolean ourIsEnabled;

  /**
   * Pushes a {@link JComponent} to the top of the stack for the current thread. This basically means the given {@link JComponent} is the
   * object that is being processed by Swing.
   *
   * @return the ancestor-descendant path of the given {@link JComponent} to its root + null terminator, or last pushed {@link JComponent}.
   */
  @NotNull
  static List<Container> pushJComponent(@NotNull JComponent jComponent) {
    Stack<JComponent> jComponentStack = ourStack.get();
    List<Container> pathFragment = new ArrayList<>();
    Container top = jComponentStack.empty() ? null : jComponentStack.peek();

    if (!ourIsEnabled && top == null) {
      // Only truly disable when the stack is empty, otherwise logic for popping becomes messy.
      return Collections.emptyList();
    }

    jComponentStack.push(jComponent);

    // Append the hierarchy path from the current JComponent up to, but not including, the last pushed JComponent.
    // If this reaches past the root, then record the null parent as a "terminator" to differentiate between a partial vs complete path.
    Container currentContainer = jComponent;
    while (currentContainer != top) {
      // It's possible we attempt to render JComponent children that are not part of the same tree (like InstructionsRenderer).
      pathFragment.add(currentContainer);
      currentContainer = currentContainer.getParent();
      if (currentContainer == null) {
        pathFragment.add(null);
        break;
      }
    }

    return Lists.reverse(pathFragment);
  }

  /**
   * Removes the given {@link JComponent} from the top of the stack for the current thread, indicating that it as finished being
   * rendered/processed.
   */
  static void popJComponent(@NotNull JComponent component) {
    Stack<JComponent> jComponentStack = ourStack.get();
    if (!ourIsEnabled && jComponentStack.empty()) {
      return;
    }

    if (jComponentStack.isEmpty() || jComponentStack.pop() != component) {
      throw new RuntimeException("Popping top of stack with top element not matching expected element.");
    }
  }

  static void setEnabled(boolean isEnabled) {
    ourIsEnabled = isEnabled;
  }

  @VisibleForTesting
  static void clear() {
    // Leaving out clearing all stacks across all EDT threads. First this class is for internal+debug-only. Second, there should only be
    // at most a couple of EDT threads. Therefore, having one or two extra empty stacks sitting around is most likely not an issue.
    Stack<JComponent> jComponentStack = ourStack.get();
    jComponentStack.clear();
  }
}
