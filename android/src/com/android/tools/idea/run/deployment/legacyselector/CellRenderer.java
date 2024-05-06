/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.legacyselector;

import com.android.tools.idea.run.deployment.Heading;
import com.intellij.ui.popup.PopupFactoryImpl.ActionItem;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.UIUtil.FontSize;
import javax.swing.JList;
import org.jetbrains.annotations.NotNull;

final class CellRenderer extends PopupListElementRenderer<ActionItem> {
  CellRenderer(@NotNull ListPopupImpl popup) {
    super(popup);
  }

  @Override
  protected void customizeComponent(@NotNull JList<? extends ActionItem> list, @NotNull ActionItem value, boolean selected) {
    super.customizeComponent(list, value, selected);

    if (value.getAction() instanceof Heading) {
      myTextLabel.setFont(UIUtil.getLabelFont(FontSize.MINI));
      return;
    }

    myTextLabel.setFont(StartupUiUtil.getLabelFont());
  }
}
