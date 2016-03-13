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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview;

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractDependencyNode<T extends PsAndroidDependency> extends AbstractPsdNode<T> {
  protected AbstractDependencyNode(@NotNull AbstractPsdNode parent, @NotNull T dependency) {
    super(parent, dependency);
  }
}
