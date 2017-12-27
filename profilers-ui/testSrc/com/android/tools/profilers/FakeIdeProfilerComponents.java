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

import com.android.tools.profilers.stacktrace.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FakeIdeProfilerComponents implements IdeProfilerComponents {
  @NotNull private Map<JComponent, Supplier<CodeLocation>> myComponentNavigations = new HashMap<>();
  @NotNull private Map<JComponent, List<ContextMenuItem>> myComponentContextMenus = new HashMap<>();

  @NotNull
  @Override
  public LoadingPanel createLoadingPanel(int delayMs) {
    return new LoadingPanel() {
      @NotNull
      @Override
      public JComponent getComponent() {
        return new JPanel(new BorderLayout());
      }

      @Override
      public void setLoadingText(@NotNull String loadingText) {
      }

      @Override
      public void setChildComponent(@Nullable Component comp) {
      }

      @Override
      public void startLoading() {
      }

      @Override
      public void stopLoading() {
      }
    };
  }

  @NotNull
  @Override
  public StackTraceView createStackView(@NotNull StackTraceModel model) {
    return new StackTraceViewStub(model);
  }

  @Override
  public void installNavigationContextMenu(@NotNull JComponent component,
                                           @NotNull CodeNavigator navigator,
                                           @NotNull Supplier<CodeLocation> codeLocationSupplier) {
    assertFalse(myComponentNavigations.containsKey(component));
    myComponentNavigations.put(component, codeLocationSupplier);
  }

  @Override
  public void installContextMenu(@NotNull JComponent component, @NotNull ContextMenuItem contextMenuItem) {
    List<ContextMenuItem> menus = myComponentContextMenus.computeIfAbsent(component, k -> new ArrayList<>());
    menus.add(contextMenuItem);
  }

  @Override
  public void openExportDialog(@NotNull Supplier<String> dialogTitleSupplier,
                               @NotNull Supplier<String> extensionSupplier,
                               @NotNull Consumer<File> saveToFile) {
  }

  @Nullable
  public Supplier<CodeLocation> getCodeLocationSupplier(@NotNull JComponent component) {
    assertTrue(myComponentNavigations.containsKey(component));
    return myComponentNavigations.get(component);
  }

  @Nullable
  public List<ContextMenuItem> getComponentContextMenus(@NotNull JComponent component) {
    return myComponentContextMenus.get(component);
  }

  @NotNull
  @Override
  public DataViewer createFileViewer(@NotNull File file) {
    return new DataViewer() {
      private final JComponent DUMMY_COMPONENT = new JPanel();

      @NotNull
      @Override
      public JComponent getComponent() {
        return DUMMY_COMPONENT;
      }

      @Nullable
      @Override
      public Dimension getDimension() {
        return null;
      }
    };
  }

  @NotNull
  @Override
  public JComponent createResizableImageComponent(@NotNull BufferedImage image) {
    return new JPanel();
  }

  public static final class StackTraceViewStub implements StackTraceView {
    private StackTraceModel myModel;

    public StackTraceViewStub(@NotNull StackTraceModel model) {
      myModel = model;
    }

    @NotNull
    @Override
    public StackTraceModel getModel() {
      return myModel;
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return new JPanel();
    }
  }
}
