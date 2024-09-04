/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.ui.problems;

import com.intellij.ide.errorTreeView.ErrorTreeElementKind;
import com.intellij.ide.errorTreeView.GroupingElement;
import com.intellij.ide.errorTreeView.NavigatableMessageElement;
import com.intellij.pom.Navigatable;
import javax.annotation.Nullable;

/** A problems view entry which supports navigating back to the blaze console view. */
public class ProblemsViewMessageElement extends NavigatableMessageElement {

  private final Navigatable consoleNavigatable;

  /**
   * @param navigatable 'navigate to source' (or double-clicking) uses this
   * @param consoleNavigatable an alternative navigatable, used to focus the console view at this
   *     problem.
   */
  public ProblemsViewMessageElement(
      ErrorTreeElementKind kind,
      @Nullable GroupingElement parent,
      String[] message,
      Navigatable navigatable,
      Navigatable consoleNavigatable,
      String exportText,
      String rendererTextPrefix) {
    super(kind, parent, message, navigatable, exportText, rendererTextPrefix);
    this.consoleNavigatable = consoleNavigatable;
  }

  Navigatable getBlazeConsoleNavigatable() {
    return consoleNavigatable;
  }
}
