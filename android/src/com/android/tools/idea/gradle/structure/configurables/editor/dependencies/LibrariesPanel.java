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
package com.android.tools.idea.gradle.structure.configurables.editor.dependencies;

import com.android.tools.idea.structure.dialog.HeaderPanel;
import com.google.common.collect.Lists;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.PlatformIcons.LIBRARY_ICON;
import static com.intellij.util.ui.UIUtil.getHTMLEditorKit;
import static javax.swing.BorderFactory.createEmptyBorder;

class LibrariesPanel extends AddDependencyPanel {
  private static final List<PopularLibrary> POPULAR_LIBRARIES = Lists.newArrayList();
  static {
    POPULAR_LIBRARIES.add(new PopularLibrary("appcompat-v7", "com.android.support:appcompat-v7:23.0.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("cardview-v7", "com.android.support:cardview-v7:23.0.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("dagger", "com.google.dagger:dagger:2.0.2"));
    POPULAR_LIBRARIES.add(new PopularLibrary("design", "com.android.support:design:23.0.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("guava", "com.google.guava:guava:18.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("gridlayout-v7", "com.android.support:gridlayout-v7:23.0.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("gson", "org.immutables:gson:2.1.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("play-services", "com.google.android.gms:play-services:7.8.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("recyclerview-v7", "com.android.support:recyclerview-v7:23.0.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("support-annotations", "com.android.support:support-annotations:23.0.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("support-v13", "com.android.support:support-v13:23.0.0"));
  }

  LibrariesPanel(@NotNull DependenciesPanel dependenciesPanel, @NotNull List<ArtifactRepositorySearch> searches) {
    super("Library", LIBRARY_ICON);
    JBSplitter splitter = new OnePixelSplitter(false, "psd.dependencies.libraries.splitter.proportion", 0.80f);
    JPanel librarySearchPanel = new JPanel(new BorderLayout());
    librarySearchPanel.add(new HeaderPanel("Library Search"), BorderLayout.NORTH);
    librarySearchPanel.add(new LibrarySearch(dependenciesPanel, searches).getPanel(), BorderLayout.CENTER);

    splitter.setFirstComponent(librarySearchPanel);
    splitter.setSecondComponent(new PopularLibrariesPanel(dependenciesPanel));

    add(splitter, BorderLayout.CENTER);
  }

  private static class PopularLibrariesPanel extends JPanel {
    PopularLibrariesPanel(@NotNull final DependenciesPanel dependenciesPanel) {
      super(new BorderLayout());
      JTextPane textPane = new JTextPane();
      textPane.setEditable(false);

      textPane.setEditorKit(getHTMLEditorKit());

      StringBuilder buffer = new StringBuilder(860);
      for (PopularLibrary library : POPULAR_LIBRARIES) {
        buffer.append(String.format("<a href='%1$s'>", library.coordinate)).append(library.name).append("</a><br/>");
      }
      textPane.setText(buffer.toString());
      textPane.setBorder(createEmptyBorder(5, 5, 5, 5));

      textPane.addHyperlinkListener(new HyperlinkAdapter() {
        @Override
        protected void hyperlinkActivated(HyperlinkEvent e) {
          String coordinate = e.getDescription();
          assert isNotEmpty(coordinate);
          dependenciesPanel.addLibraryDependency(coordinate);
        }
      });

      add(new HeaderPanel("Popular Libraries"), BorderLayout.NORTH);
      add(new JBScrollPane(textPane), BorderLayout.CENTER);
      setBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT));
    }
  }

  private static class PopularLibrary {
    @NotNull final String name;
    @NotNull final String coordinate;

    PopularLibrary(@NotNull String name, @NotNull String coordinate) {
      this.name = name;
      this.coordinate = coordinate;
    }
  }
}
