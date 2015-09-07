/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation;

import com.android.ide.common.rendering.api.*;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.*;
import com.android.tools.swing.layoutlib.FakeImageFactory;
import com.intellij.android.designer.AndroidDesignerEditorProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class AndroidRootComponent extends JComponent {
  public static final boolean DEBUG = false;
  public static final int PADDING = JBUI.scale(3);
  private static final int ARC_SIZE = JBUI.scale(10);
  private static final Font FONT = UIUtil.getLabelFont();
  private static final FontMetrics FONT_METRICS = new Canvas().getFontMetrics(FONT);

  public static int getTopShift() {
    return PADDING + FONT_METRICS.getHeight();
  }

  public static Point relativePoint(final Point p) {
    return new Point(p.x - PADDING, p.y - getTopShift());
  }

  private final RenderingParameters myRenderingParameters;
  private final PsiFile myLayoutFile;
  private final @Nullable String myMenuName;
  private final String myActivityName;

  @NotNull Transform transform = new Transform(1f);
  private BufferedImage myScaledImage;
  private RenderResult myRenderResult = null;
  private boolean myRenderPending = false;
  private boolean mySelected = false;

  public AndroidRootComponent(@NotNull final String className,
                              @NotNull final RenderingParameters renderingParameters,
                              @Nullable final PsiFile psiFile,
                              @Nullable String menuName) {
    final String activityName;

    // Extracting the last component from fully qualified class name.
    int dotIndex = className.lastIndexOf('.');
    if (dotIndex == -1) {
      activityName = className;
    } else {
      activityName = className.substring(dotIndex + 1);
    }

    // Indicate in the title whether current state is a menu state.
    myActivityName = menuName == null ? activityName : activityName + " [menu]";

    myRenderingParameters = renderingParameters;
    myLayoutFile = psiFile;
    myMenuName = menuName;
  }

  public boolean isMenu() {
    return myMenuName != null;
  }

  public static void launchEditor(RenderingParameters renderingParameters, @Nullable PsiFile file, boolean layoutFile) {
    if (file != null) {
      Project project = renderingParameters.project;
      VirtualFile virtualFile = file.getVirtualFile();
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, 0);
      FileEditorManager manager = FileEditorManager.getInstance(project);
      manager.openEditor(descriptor, true);
      if (layoutFile) {
        manager.setSelectedEditor(virtualFile, AndroidDesignerEditorProvider.ANDROID_DESIGNER_ID);
      }
    }
  }

  public void launchLayoutEditor() {
    launchEditor(myRenderingParameters, myLayoutFile, true);
  }

  @Nullable
  public RenderResult getRenderResult() {
    return myRenderResult;
  }

  private void setRenderResult(@Nullable RenderResult renderResult) {
    Container parent = getParent();
    if (parent == null) { // this is coming in of a different thread - we may have been detached form the view hierarchy in the meantime
      return;
    }
    myRenderResult = renderResult;
    // once we have finished rendering we know where our internal views are and our parent needs to repaint (arrows etc.)
    revalidate(); // invalidate parent (NavigationView)
    parent.repaint();
  }

  private void invalidate2() {
    myScaledImage = null;
  }

  public void setScale(float scale) {
    transform = new Transform(scale);
    invalidate2();
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension d = transform.modelToView(myRenderingParameters.getDeviceScreenSize());
    return new Dimension(d.width + 2 * PADDING, d.height + 2 * PADDING + FONT_METRICS.getHeight());
  }

  @Nullable
  private BufferedImage getScaledImage() {
    return myScaledImage;
  }

  private void center(Graphics g, String message, Font font, int height) {
    int messageWidth = getFontMetrics(font).stringWidth(message);
    g.drawString(message, (getWidth() - messageWidth) / 2, height);
  }

  private Color getBackgroundColor() {
    if (mySelected) {
      return JBColor.BLUE;
    } else {
      return Gray._30;
    }
  }

  public void setSelected(boolean selected) {
    mySelected = selected;
  }

  @Override
  public void paintComponent(Graphics g) {
    GraphicsUtil.setupAAPainting(g);

    g.setColor(getBackgroundColor());
    Dimension size = getSize();
    g.fillRoundRect(0, 0, size.width, size.height, ARC_SIZE, ARC_SIZE);

    g.setColor(JBColor.WHITE);

    Image scaledImage = getScaledImage();
    if (scaledImage != null) {
      g.setFont(FONT);
      g.drawString(myActivityName, PADDING, PADDING + FONT_METRICS.getAscent());
      g.drawImage(scaledImage, PADDING, getTopShift(), null);
    }
    else {
      Font font = g.getFont();
      int vCenter = getHeight() / 2;
      String message = "[" + (myLayoutFile == null ? "no xml resource" : myLayoutFile.getName()) + "]";
      center(g, message, font, vCenter);
      render(transform.myScale);
    }
  }

  private void render(final float scale) {
    if (myLayoutFile == null) {
      return;
    }
    Project project = myRenderingParameters.project;
    final AndroidFacet facet = myRenderingParameters.facet;
    final Configuration configuration = myRenderingParameters.configuration;

    if (project.isDisposed()) {
      return;
    }
    if (myRenderPending) { // already rendering
      return;
    }
    myRenderPending = true;

    // We're showing overflow menus in menu states. This isn't affecting drawing of activity states,
    // because we're setting their menu list to be empty later in the code.
    ActionBarHandler.showMenu(true, null, false);

    // The rendering service takes long enough to initialise that we don't want to do this from the EDT.
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        RenderService renderService = RenderService.get(facet);
        final RenderTask task = renderService.createTask(myLayoutFile, configuration, renderService.createLogger(), null);
        if (task != null) {
          task.setProvideCookiesForIncludedViews(true);
          final ActionBarHandler actionBarHandler = task.getLayoutlibCallback().getActionBarHandler();
          if (actionBarHandler != null) {
            final List<String> menuList = isMenu() ? Collections.singletonList(myMenuName) : Collections.<String>emptyList();
            actionBarHandler.setMenuIdNames(menuList);
          }

          // Setting up FakeImageFactory to draw directly to scaled-down image.
          // This allows us to drastically reduce memory used by Navigation Editor.
          final FakeImageFactory factory = new FakeImageFactory();
          final Dimension size = AndroidRootComponent.this.getSize();
          final BufferedImage image = UIUtil.createImage(size.width - 2 * PADDING, size.height - 2 * PADDING, BufferedImage.TYPE_INT_ARGB);
          final Graphics2D graphics = (Graphics2D) image.getGraphics();
          graphics.setTransform(AffineTransform.getScaleInstance(scale, scale));
          factory.setGraphics(graphics);

          RenderResult renderedResult = task.render(factory);
          if (renderedResult != null) {
            RenderSession session = renderedResult.getSession();
            if (session != null) {
              Result result = session.getResult();
              if (result.isSuccess()) {
                setRenderResult(renderedResult);
                myScaledImage = image;
                task.dispose();
                myRenderPending = false;
                return;
              }
            }
          }
          if (DEBUG) System.out.println("AndroidRootComponent: rendering failed ");
        }
      }
    });
  }

  @Nullable
  public RenderedView getRenderedView(Point p) {
    RenderResult renderResult = getRenderResult();
    if (renderResult == null) {
      return null;
    }
    RenderedViewHierarchy hierarchy = renderResult.getHierarchy();
    if (hierarchy == null) {
      return null;
    }
    return hierarchy.findLeafAt(transform.viewToModelX(p.x), transform.viewToModelY(p.y));
  }
}
