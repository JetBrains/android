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
package com.android.tools.idea.uibuilder.palette;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.rendering.ImageUtils;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class DependencyManager {
  private static final double DOWNLOAD_SCALE = 0.7;
  private static final double DOWNLOAD_RELATIVE_OFFSET = (1.0 - DOWNLOAD_SCALE) / DOWNLOAD_SCALE;

  private final Project myProject;
  private final Set<String> myMissingLibraries;
  private Module myModule;
  private Palette myPalette;

  public DependencyManager(@NotNull Project project, @NotNull JComponent paletteUI, @NotNull Disposable parentDisposable) {
    myProject = project;
    myMissingLibraries = new HashSet<>();
    registerDependencyUpdates(paletteUI, parentDisposable);
  }


  @NotNull
  public Project getProject() {
    return myProject;
  }

  public void setPalette(@NotNull Palette palette, @NotNull Module module) {
    myPalette = palette;
    myModule = module;
    checkForNewMissingDependencies();
  }

  public boolean needsLibraryLoad(@NotNull Palette.Item item) {
    return myMissingLibraries.contains(item.getGradleCoordinateId());
  }

  public boolean ensureLibraryIsIncluded(@NotNull Palette.Item item) {
    String coordinateId = item.getGradleCoordinateId();
    assert coordinateId != null;
    assert myModule != null;
    GradleDependencyManager manager = GradleDependencyManager.getInstance(myProject);
    return manager.ensureLibraryIsIncluded(myModule, toGradleCoordinatesFromIds(Collections.singletonList(coordinateId)), null);
  }

  @NotNull
  public Icon createItemIcon(@NotNull Palette.Item item, @NotNull Component componentContext) {
    return createItemIcon(item, item.getIcon(), componentContext);
  }

  @NotNull
  public Icon createLargeItemIcon(@NotNull Palette.Item item, @NotNull Component componentContext) {
    return createItemIcon(item, item.getLargeIcon(), componentContext);
  }

  @NotNull
  private Icon createItemIcon(@NotNull Palette.Item item, @NotNull Icon icon, @NotNull Component componentContext) {
    if (!needsLibraryLoad(item)) {
      return icon;
    }
    Icon download = AllIcons.Actions.Download;
    BufferedImage image = UIUtil.createImage(icon.getIconWidth(),
                                             icon.getIconHeight(),
                                             BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = (Graphics2D)image.getGraphics();
    icon.paintIcon(componentContext, g2, 0, 0);
    int x = icon.getIconWidth() - download.getIconWidth();
    int y = icon.getIconHeight() - download.getIconHeight();
    if (x == 0 && y == 0) {
      g2.scale(DOWNLOAD_SCALE, DOWNLOAD_SCALE);
      x = (int)(icon.getIconWidth() * DOWNLOAD_RELATIVE_OFFSET);
      y = (int)(icon.getIconHeight() * DOWNLOAD_RELATIVE_OFFSET);
    }
    download.paintIcon(componentContext, g2, x, y);
    g2.dispose();
    return new JBImageIcon(ImageUtils.convertToRetinaIgnoringFailures(image));
  }

  private boolean checkForNewMissingDependencies() {
    Set<String> missing = Collections.emptySet();
    if (myModule != null) {
      GradleDependencyManager manager = GradleDependencyManager.getInstance(myProject);
      List<GradleCoordinate> coordinates = toGradleCoordinatesFromIds(myPalette.getGradleCoordinateIds());
      missing = fromGradleCoordinatesToIds(manager.findMissingDependencies(myModule, coordinates));
      if (myMissingLibraries.equals(missing)) {
        return false;
      }
    }
    myMissingLibraries.clear();
    myMissingLibraries.addAll(missing);
    return true;
  }

  private void registerDependencyUpdates(@NotNull JComponent paletteUI, @NotNull Disposable parentDisposable) {
    GradleSyncState.subscribe(myProject, new GradleSyncListener.Adapter() {
      @Override
      public void syncSucceeded(@NotNull Project project) {
        if (checkForNewMissingDependencies()) {
          paletteUI.repaint();
        }
      }
    }, parentDisposable);
  }

  @NotNull
  private static Set<String> fromGradleCoordinatesToIds(@NotNull Collection<GradleCoordinate> coordinates) {
    return coordinates.stream()
      .map(GradleCoordinate::getId)
      .filter(dependency -> dependency != null)
      .collect(Collectors.toSet());
  }

  @NotNull
  private static List<GradleCoordinate> toGradleCoordinatesFromIds(@NotNull Collection<String> dependencies) {
    return dependencies.stream()
      .map(dependency -> GradleCoordinate.parseCoordinateString(dependency + ":+"))
      .filter(coordinate -> coordinate != null)
      .collect(Collectors.toList());
  }
}
