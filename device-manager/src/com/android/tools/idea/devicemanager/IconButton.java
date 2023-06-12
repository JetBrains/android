/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import com.intellij.util.ui.JBDimension;
import java.awt.Dimension;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.JButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IconButton extends JButton implements IconTableCell {
  private @Nullable Icon myDefaultIcon;

  IconButton(@Nullable Icon defaultIcon) {
    super(defaultIcon);
    Dimension size = new JBDimension(22, 22);

    setBorder(null);
    setContentAreaFilled(false);
    setMaximumSize(size);
    setMinimumSize(size);
    setPreferredSize(size);

    myDefaultIcon = defaultIcon;
  }

  @Override
  public @NotNull Optional<Icon> getDefaultIcon() {
    return Optional.ofNullable(myDefaultIcon);
  }

  public void setDefaultIcon(@NotNull Icon defaultIcon) {
    myDefaultIcon = defaultIcon;
  }
}
