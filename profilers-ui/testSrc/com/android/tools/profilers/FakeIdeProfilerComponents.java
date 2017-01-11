/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers;

import com.android.tools.profilers.common.CodeLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FakeIdeProfilerComponents implements IdeProfilerComponents {
  @NotNull
  private Map<JComponent, ComponentNavigations> myComponents = new HashMap<>();

  @Nullable
  @Override
  public JComponent getFileViewer(@Nullable File file) {
    return null;
  }

  @Override
  public void installNavigationContextMenu(@NotNull JComponent component,
                                           @NotNull Supplier<CodeLocation> codeLocationSupplier,
                                           @Nullable Runnable preNavigate) {
    assertFalse(myComponents.containsKey(component));
    myComponents.put(component, new ComponentNavigations(codeLocationSupplier, preNavigate));
  }

  @Nullable
  public Supplier<CodeLocation> getCodeLocationSupplier(@NotNull JComponent component) {
    assertTrue(myComponents.containsKey(component));
    return myComponents.get(component).myCodeLocationSupplier;
  }

  @Nullable
  public Runnable getPreNavigate(@NotNull JComponent component) {
    assertTrue(myComponents.containsKey(component));
    return myComponents.get(component).myPreNavigate;
  }

  private static class ComponentNavigations {
    @Nullable
    private Supplier<CodeLocation> myCodeLocationSupplier;

    @Nullable
    private Runnable myPreNavigate;

    private ComponentNavigations(@Nullable Supplier<CodeLocation> supplier, @Nullable Runnable navigate) {
      myCodeLocationSupplier = supplier;
      myPreNavigate = navigate;
    }
  }
}
