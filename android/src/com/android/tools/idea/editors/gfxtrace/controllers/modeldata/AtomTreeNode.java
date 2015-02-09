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
package com.android.tools.idea.editors.gfxtrace.controllers.modeldata;

import com.android.tools.idea.editors.gfxtrace.schema.AtomReader;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.List;

public abstract class AtomTreeNode {
  public abstract List<TextPiece> getTextPieces(@NotNull JTree tree,
                                                @NotNull TreeNode node,
                                                @NotNull EnumInfoCache enumInfoCache,
                                                @NotNull AtomReader atomReader);

  public static class TextPiece {
    @NotNull public String myString;
    @NotNull public SimpleTextAttributes myTextAttributes;

    public TextPiece(@NotNull String string, @NotNull SimpleTextAttributes attributes) {
      myString = string;
      myTextAttributes = attributes;
    }
  }
}
