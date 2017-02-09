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

import com.android.tools.profilers.common.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FakeIdeProfilerComponents implements IdeProfilerComponents {
  @NotNull private Map<JComponent, ComponentNavigations> myComponentNavigations = new HashMap<>();
  @NotNull private Map<JComponent, List<ContextMenuItem>> myComponentContextMenus = new HashMap<>();

  @NotNull
  @Override
  public LoadingPanel createLoadingPanel() {
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
      public void startLoading() {
      }

      @Override
      public void stopLoading() {
      }
    };
  }

  @NotNull
  @Override
  public TabsPanel createTabsPanel() {
    return new TabsPanel() {
      private JTabbedPane myTabbedPane = new JTabbedPane();

      @NotNull
      @Override
      public JComponent getComponent() {
        return myTabbedPane;
      }

      @Override
      public void addTab(@NotNull String label, @NotNull JComponent content) {
        myTabbedPane.add(label, content);
      }

      @Override
      public void removeTab(@NotNull JComponent tab) {
        myTabbedPane.remove(tab);
      }

      @Override
      public void removeAll() {
        myTabbedPane.removeAll();
      }

      @Nullable
      @Override
      public JComponent getSelectedComponent() {
        return null;
      }
    };
  }

  /**
   * This creates a mocked version of a {@link StackTraceViewStub} such that code that uses {@link IdeProfilerComponents} can be tested.
   * @return a mocked {@link StackTraceViewStub}
   */
  @NotNull
  @Override
  public StackTraceView createStackView(@Nullable Runnable preNavigate) {
    return Mockito.spy(new StackTraceViewStub(preNavigate));
  }

  @Override
  public void installNavigationContextMenu(@NotNull JComponent component,
                                           @NotNull Supplier<CodeLocation> codeLocationSupplier,
                                           @Nullable Runnable preNavigate) {
    assertFalse(myComponentNavigations.containsKey(component));
    myComponentNavigations.put(component, new ComponentNavigations(codeLocationSupplier, preNavigate));
  }

  @Override
  public void installContextMenu(@NotNull JComponent component, @NotNull ContextMenuItem contextMenuItem) {
    List<ContextMenuItem> menus = myComponentContextMenus.computeIfAbsent(component, k -> new ArrayList<>());
    menus.add(contextMenuItem);
  }

  @NotNull
  @Override
  public JButton createExportButton(@Nullable String buttonText,
                                    @Nullable String tooltip,
                                    @NotNull Supplier<String> dialogTitleSupplier,
                                    @NotNull Supplier<String> extensionSupplier,
                                    @NotNull Consumer<File> saveToFile) {
    JButton button = new JButton(buttonText);
    button.setToolTipText(tooltip);
    return button;
  }

  @Nullable
  public Supplier<CodeLocation> getCodeLocationSupplier(@NotNull JComponent component) {
    assertTrue(myComponentNavigations.containsKey(component));
    return myComponentNavigations.get(component).myCodeLocationSupplier;
  }

  @Nullable
  public Runnable getPreNavigate(@NotNull JComponent component) {
    assertTrue(myComponentNavigations.containsKey(component));
    return myComponentNavigations.get(component).myPreNavigate;
  }

  @Nullable
  public List<ContextMenuItem> getComponentContextMenus(@NotNull JComponent component) {
    return myComponentContextMenus.get(component);
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

  @NotNull
  @Override
  public FileViewer createFileViewer(@NotNull File file) {
    return new FileViewer() {
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

  public static class StackTraceViewStub implements StackTraceView {
    private Runnable myPreNavigate;
    private CodeLocation myLocation;

    public StackTraceViewStub(Runnable preNavigate) {
      myPreNavigate = preNavigate;
    }

    @Override
    public void clearStackFrames() {

    }

    @Override
    public void setStackFrames(@NotNull String stackString) {

    }

    @Override
    public void setStackFrames(@NotNull ThreadId threadId, @NotNull List<CodeLocation> stackFrames) {

    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return new JPanel();
    }

    @Nullable
    @Override
    public CodeLocation getSelectedLocation() {
      return myLocation;
    }

    @Override
    public boolean selectCodeLocation(@Nullable CodeLocation location) {
      myLocation = location;
      myPreNavigate.run();
      return location != null;
    }

    @NotNull
    @Override
    public List<CodeLocation> getCodeLocations() {
      return new ArrayList<>();
    }
  }
}
