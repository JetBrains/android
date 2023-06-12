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
package com.android.tools.idea.devicemanager;

import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Status {
  private final @Nullable Icon myIcon;
  private final @NotNull String myText;
  private final @Nullable String myTooltipText;

  Status(@NotNull String text) {
    this(text, null, null);
  }

  Status(@NotNull String text, @Nullable Icon icon, @Nullable String tooltipText) {
    myIcon = icon;
    myText = text;
    myTooltipText = tooltipText;
  }

  @Nullable Icon getIcon() {
    return myIcon;
  }

  @NotNull String getText() {
    return myText;
  }

  @Nullable String getTooltipText() {
    return myTooltipText;
  }

  @Override
  public int hashCode() {
    int hashCode = Objects.hashCode(myIcon);

    hashCode = 31 * hashCode + myText.hashCode();
    hashCode = 31 * hashCode + Objects.hashCode(myTooltipText);

    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof Status)) {
      return false;
    }

    Status status = (Status)object;
    return Objects.equals(myIcon, status.myIcon) && myText.equals(status.myText) && Objects.equals(myTooltipText, status.myTooltipText);
  }
}
