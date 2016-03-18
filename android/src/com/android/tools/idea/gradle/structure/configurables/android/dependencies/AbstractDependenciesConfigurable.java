/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.tools.idea.gradle.structure.configurables.BaseNamedConfigurable;
import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsModule;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class AbstractDependenciesConfigurable<T extends PsModule> extends BaseNamedConfigurable<T> {
  @NotNull private final PsContext myContext;
  @NotNull private final List<PsModule> myExtraTopModules;

  protected AbstractDependenciesConfigurable(@NotNull T module, @NotNull PsContext context, @NotNull List<PsModule> extraTopModules) {
    super(module);
    myContext = context;
    myExtraTopModules = extraTopModules;
  }

  @NotNull
  protected PsContext getContext() {
    return myContext;
  }

  @NotNull
  protected List<PsModule> getExtraTopModules() {
    return myExtraTopModules;
  }
}
