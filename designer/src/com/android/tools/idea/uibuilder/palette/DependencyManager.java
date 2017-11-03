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
import com.android.tools.adtui.ImageUtils;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;

/**
 * Keeps track of which of the dependencies for all the components on a palette are currently
 * missing from a project. DependencyManager does this by caching dependency information available
 * through the instance of {@link com.android.tools.idea.projectsystem.AndroidModuleSystem} associated
 * with the module of the palette. This allows callers to quickly check if a particular palette item has
 * a missing dependency via the {@link #needsLibraryLoad(Palette.Item)} method.
 *
 * The set of missing dependencies is recomputed each time the project is synced (in case new dependencies have
 * been added to the palette's module) and each time the associated palette changes (see {@link #setPalette(Palette, Module)}).
 */
public class DependencyManager {
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

  @NotNull
  public Icon createItemIcon(@NotNull Palette.Item item, @NotNull Component componentContext) {
    return createItemIcon(item, item.getIcon(), StudioIcons.LayoutEditor.Extras.DOWNLOAD_OVERLAY_LEGACY, componentContext);
  }

  @NotNull
  public Icon createLargeItemIcon(@NotNull Palette.Item item, @NotNull Component componentContext) {
    return createItemIcon(item, item.getLargeIcon(), StudioIcons.LayoutEditor.Extras.DOWNLOAD_OVERLAY_LEGACY_LARGE, componentContext);
  }

  @NotNull
  private Icon createItemIcon(@NotNull Palette.Item item,
                              @NotNull Icon icon,
                              @NotNull Icon downloadIcon,
                              @NotNull Component componentContext) {
    if (!needsLibraryLoad(item)) {
      return icon;
    }
    int width = icon.getIconWidth();
    int height = icon.getIconHeight();
    int offset = JBUI.scale(1);

    Polygon triangle = new Polygon();
    triangle.addPoint(width / 3, height);
    triangle.addPoint(width, height);
    triangle.addPoint(width, height / 3);

    BufferedImage image = UIUtil.createImage(width + offset, height + offset, BufferedImage.TYPE_INT_ARGB);
    Area clip = new Area(new Rectangle(0, 0, width, height));
    clip.subtract(new Area(triangle));

    Graphics2D g2 = (Graphics2D)image.getGraphics();
    g2.setClip(clip);
    icon.paintIcon(componentContext, g2, 0, 0);
    g2.setClip(null);
    downloadIcon.paintIcon(componentContext, g2, offset, offset);
    g2.dispose();
    return new JBImageIcon(UIUtil.isRetina() ? ImageUtils.convertToRetinaIgnoringFailures(image) : image);
  }

  private boolean checkForNewMissingDependencies() {
    Set<String> missing = Collections.emptySet();

    if (myModule != null && !myModule.isDisposed()) {
      missing = myPalette.getGradleCoordinateIds().stream()
        .map(id -> GradleCoordinate.parseCoordinateString(id + ":+"))
        .filter(Objects::nonNull)
        .map(GoogleMavenArtifactId::forCoordinate)
        .filter(artifactId -> artifactId != null && !DependencyManagementUtil.dependsOn(myModule, artifactId))
        .map(GoogleMavenArtifactId::toString)
        .collect(Collectors.toSet());

      if (myMissingLibraries.equals(missing)) {
        return false;
      }
    }

    myMissingLibraries.clear();
    myMissingLibraries.addAll(missing);
    return true;
  }

  private void registerDependencyUpdates(@NotNull JComponent paletteUI, @NotNull Disposable parentDisposable) {
    myProject.getMessageBus().connect(parentDisposable).subscribe(PROJECT_SYSTEM_SYNC_TOPIC, result -> {
      if (result == SyncResult.SUCCESS && checkForNewMissingDependencies()) {
          paletteUI.repaint();
      }
    });
  }
}
