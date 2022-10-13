/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files;

import javax.swing.tree.DefaultMutableTreeNode;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link DefaultMutableTreeNode} that shows an error message
 */
public class ErrorNode extends DefaultMutableTreeNode {
  private final @NotNull String myText;

  public ErrorNode(@NotNull String text) {
    myText = text;
  }

  @Override
  public String toString() {
    return myText;
  }

  @Override
  public boolean isLeaf() {
    return true;
  }

  @NotNull
  public String getText() {
    return myText;
  }
}
