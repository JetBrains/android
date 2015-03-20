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
import com.android.tools.idea.editors.navigation.model.ModelDimension;
import com.android.tools.idea.editors.navigation.model.ModelPoint;
import com.android.tools.idea.rendering.*;
import com.intellij.android.designer.AndroidDesignerEditorProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBColor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class AndroidRootComponent extends JComponent {
  public static final boolean DEBUG = false;

  private final RenderingParameters myRenderingParameters;
  private final PsiFile myLayoutFile;
  private final @Nullable String myMenuName;

  @NotNull Transform transform = new Transform(1f);
  private Image myScaledImage;
  private RenderResult myRenderResult = null;
  private boolean myRenderPending = false;
  private boolean myCachedMenuValid = false;
  private RenderedView myCachedMenu;

  public AndroidRootComponent(@NotNull final RenderingParameters renderingParameters, @Nullable final PsiFile psiFile, @Nullable String menuName) {
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
    if (isMenu()) {
      myCachedMenuValid = false;
      revalidate();
    }
    // once we have finished rendering we know where our internal views are and our parent needs to repaint (arrows etc.)
    revalidate(); // invalidate parent (NavigationView)
    parent.repaint();
  }

  public float getScale() {
    return transform.myScale;
  }

  private void invalidate2() {
    myScaledImage = null;
  }

  public void setScale(float scale) {
    transform = new Transform(scale);
    invalidate2();
  }

  @Nullable
  private RenderedView getCachedMenu() {
    if (!myCachedMenuValid) {
      myCachedMenu = getMenu(myRenderResult);
      myCachedMenuValid = true;
    }
    return myCachedMenu;
  }

  @Nullable
  private static RenderedView findMenu(List<RenderedView> children) {
    for (RenderedView child : children) {
      ViewInfo view = child.view;
      if (view != null && view.getViewType() == ViewType.ACTION_BAR_OVERFLOW_MENU) {
        return child.getParent();
      }
      RenderedView menu = findMenu(child.getChildren());
      if (menu != null) {
        return menu;
      }
    }
    return null;
  }

  @Nullable
  private static RenderedView getMenu(RenderResult renderResult) {
    if (renderResult == null) {
      return null;
    }
    RenderedViewHierarchy hierarchy = renderResult.getHierarchy();
    if (hierarchy == null) {
      return null;
    }
    return findMenu(hierarchy.getRoots());
  }

  private static ModelDimension size(@Nullable RenderedView view) {
    if (view == null) {
      //return com.android.navigation.Dimension.ZERO;
      return new ModelDimension(100, 100); // width/height 0 and 1 is too small to cause an invalidate, for some reason
    }
    return new ModelDimension(view.w, view.h);
  }

  @Override
  public Dimension getPreferredSize() {
    return transform.modelToView(myRenderingParameters.getDeviceScreenSize());
  }

  @Nullable
  private Image getScaledImage() {
    if (myScaledImage == null || myScaledImage.getWidth(null) != getWidth() || myScaledImage.getHeight(null) != getHeight()) {
      RenderedImage renderedImage = (myRenderResult == null) ? null : myRenderResult.getImage();
      BufferedImage image = (renderedImage == null) ? null : renderedImage.getOriginalImage();
      myScaledImage = (image == null) ? null : ImageUtils.scale(image, transform.myScale, transform.myScale, 0, 0);
    }
    return myScaledImage;
  }

  private void center(Graphics g, String message, Font font, int height) {
    int messageWidth = getFontMetrics(font).stringWidth(message);
    g.drawString(message, (getWidth() - messageWidth) / 2, height);
  }

  @Override
  public void paintComponent(Graphics g) {
    Image scaledImage = getScaledImage();
    if (scaledImage != null) {
      g.drawImage(scaledImage, 0, 0, null);
    }
    else {
      g.setColor(JBColor.WHITE);
      g.fillRect(0, 0, getWidth(), getHeight());
      g.setColor(JBColor.GRAY);
      Font font = g.getFont();
      int vCenter = getHeight() / 2;
      //center(g, "Initialising...", font, vCenter);
      String message = "[" + (myLayoutFile == null ? "no xml resource" : myLayoutFile.getName()) + "]";
      center(g, message, font, vCenter);
      //center(g, message, font, vCenter + font.getSize() * 2);
      render();
    }
  }

  private void render() {
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
          RenderResult renderedResult = task.render();
          if (renderedResult != null) {
            RenderSession session = renderedResult.getSession();
            if (session != null) {
              Result result = session.getResult();
              if (result.isSuccess()) {
                setRenderResult(renderedResult);
                task.dispose();
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
