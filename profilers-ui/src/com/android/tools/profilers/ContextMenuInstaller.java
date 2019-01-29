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
package com.android.tools.profilers;

import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

public interface ContextMenuInstaller {
  /**
   * Simplified version of {@link #installGenericContextMenu(JComponent, ContextMenuItem, IntPredicate, IntConsumer)} that delegates the
   * implementation of the callback and enabled status to the {@link ContextMenuItem} itself.
   */
  default void installGenericContextMenu(@NotNull JComponent component, @NotNull ContextMenuItem contextMenuItem) {
    installGenericContextMenu(component, contextMenuItem, x -> contextMenuItem.isEnabled(), x -> contextMenuItem.run());
  }

  /**
   * Installs a generic IntelliJ context menu item on a popup menu that will be displayed when clicking a target component.
   *
   * @param component       Target {@link JComponent} that triggers the popup menu when is clicked.
   * @param contextMenuItem {@link ContextMenuItem} to be added to the popup menu.
   * @param itemEnabled     {@link IntPredicate} that receives the mouse X coordinate within {@code component} when the popup is triggered
                            and decides whether {@code contextMenuItem} should be enabled.
   * @param callback        {@link IntConsumer} that runs an action depending on the mouse X coordinate when the popup is triggered.
   */
  void installGenericContextMenu(@NotNull JComponent component, @NotNull ContextMenuItem contextMenuItem, @NotNull IntPredicate itemEnabled,
                                 @NotNull IntConsumer callback);

  /**
   * Installs an IntelliJ context menu on a {@link JComponent} which, when clicked, will navigate
   * to a code location provided by the {@code codeLocationSupplier}.
   *
   * @param component            The target {@link JComponent} that the context menu is to be installed on.
   * @param navigator            A {@link CodeNavigator} that should ultimately handle the navigation, allowing the profiler to respond to
   * @param codeLocationSupplier A {@link Supplier} of the desired code to navigate to. When the supplier is resolved, the system is not
   *                             necessarily ready to conduct the navigation (i.e. displaying the menu popup, awaiting user input).
   */
  void installNavigationContextMenu(@NotNull JComponent component,
                                    @NotNull CodeNavigator navigator,
                                    @NotNull Supplier<CodeLocation> codeLocationSupplier);

}
