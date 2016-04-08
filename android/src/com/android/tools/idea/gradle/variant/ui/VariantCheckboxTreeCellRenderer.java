/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.variant.ui;

import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.openapi.module.Module;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.ColoredTreeCellRenderer;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.ui.SimpleTextAttributes.*;

public abstract class VariantCheckboxTreeCellRenderer extends CheckboxTree.CheckboxTreeCellRenderer {
  protected void appendVariant(@NotNull String variant) {
    ColoredTreeCellRenderer textRenderer = getTextRenderer();
    textRenderer.append(variant, REGULAR_ITALIC_ATTRIBUTES);
    textRenderer.setIcon(AndroidIcons.Variant);
  }

  protected void appendModule(@NotNull Module module, @Nullable String variant) {
    ColoredTreeCellRenderer textRenderer = getTextRenderer();
    textRenderer.append(module.getName());
    textRenderer.setIcon(GradleUtil.getModuleIcon(module));
    if (isNotEmpty(variant)) {
      textRenderer.append(" ", REGULAR_ATTRIBUTES);
      textRenderer.append("(" + variant + ")", GRAY_ATTRIBUTES);
    }
  }
}
