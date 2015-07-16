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
package com.android.tools.idea.npw;

import com.android.tools.idea.npw.FormFactorUtils;
import com.android.tools.idea.npw.ModuleTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Default implementation for constructing a {@link ModuleTemplate}.
 */
public abstract class AbstractModuleTemplate implements ModuleTemplate {
  private final Icon myIcon;
  private final String myName;
  private final String myDescription;
  private final FormFactorUtils.FormFactor myFormFactor;

  public AbstractModuleTemplate(@NotNull String name,
                                @Nullable String description,
                                @Nullable FormFactorUtils.FormFactor formFactor,
                                @Nullable Icon icon) {
    myIcon = icon;
    myName = name;
    myDescription = description;
    myFormFactor = formFactor;
  }

  @Nullable
  @Override
  public final Icon getIcon() {
    return myIcon;
  }

  @NotNull
  @Override
  public final String getName() {
    return myName;
  }

  @Nullable
  @Override
  public final String getDescription() {
    return myDescription;
  }

  @Nullable
  @Override
  public final FormFactorUtils.FormFactor getFormFactor() {
    return myFormFactor;
  }
}
