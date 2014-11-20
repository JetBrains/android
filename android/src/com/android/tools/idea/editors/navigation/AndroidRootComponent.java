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

import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.rendering.api.ViewType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.navigation.model.ModelDimension;
import com.android.tools.idea.editors.navigation.model.ModelPoint;
import com.android.tools.idea.rendering.*;
import com.intellij.android.designer.AndroidDesignerEditorProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
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
  public final boolean isMenu;

  @NotNull Transform transform = createTransform(1);
  private Image myScaledImage;
  private RenderResult myRenderResult = null;
  private boolean myRenderPending = false;
  private boolean myCachedMenuValid = false;
  private RenderedView myCachedMenu;

  public AndroidRootComponent(@NotNull final RenderingParameters renderingParameters, @Nullable final PsiFile psiFile, boolean isMenu) {
    myRenderingParameters = renderingParameters;
    myLayoutFile = psiFile;
    this.isMenu = isMenu;
  }

  public static void launchEditor(RenderingParameters renderingParameters, @Nullable PsiFile file, boolean layoutFile) {
    if (file != null) {
      Project project = renderingParameters.myProject;
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
    if (isMenu) {
      myCachedMenuValid = false;
      revalidate();
    }
    // once we have finished rendering we know where our internal views are and our parent needs to repaint (arrows etc.)
    //repaint();
    parent.repaint();
  }

  public float getScale() {
    return transform.myScale;
  }

  private void invalidate2() {
    myScaledImage = null;
  }

  public void setScale(float scale) {
    transform = createTransform(scale);
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

  private Transform createTransform(float scale) {
    if (isMenu) {
      return new Transform(scale) {
        private int getDx() {
          RenderedView menu = getCachedMenu();
          return (menu == null) ? 0 : menu.x;
        }

        private int getDy() {
          RenderedView menu = getCachedMenu();
          return (menu == null) ? 0 : menu.y;
        }

        @Override
        public int modelToViewX(int x) {
          return super.modelToViewX(x - getDx());
        }

        @Override
        public int modelToViewY(int y) {
          return super.modelToViewY(y - getDy());
        }

        @Override
        public int viewToModelX(int x) {
          return super.viewToModelX(x) + getDx();
        }

        @Override
        public int viewToModelY(int y) {
          return super.viewToModelY(y) + getDy();
        }
      };
    }
    return new Transform(scale);
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
    return transform.modelToView(isMenu ? size(getMenu(myRenderResult)) : myRenderingParameters.getDeviceScreenSize());
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
      if (isMenu) {
        Point point = transform.modelToView(ModelPoint.ORIGIN);
        g.drawImage(scaledImage, point.x, point.y, null);
      }
      else {
        g.drawImage(scaledImage, 0, 0, null);
      }
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
    Project project = myRenderingParameters.myProject;
    final AndroidFacet facet = myRenderingParameters.myFacet;
    final Configuration configuration = myRenderingParameters.myConfiguration;

    if (project.isDisposed()) {
      return;
    }
    if (myRenderPending) { // already rendering
      return;
    }
    myRenderPending = true;

    // The rendering service takes long enough to initialise that we don't want to do this from the EDT.
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        Module module = facet.getModule();
        RenderLogger logger = new RenderLogger(myLayoutFile.getName(), module);
        final RenderService service = RenderService.create(facet, module, myLayoutFile, configuration, logger, null);
        if (service != null) {
          if (!isMenu) {
            // Don't show menus in the layout view
            service.getLayoutlibCallback().getActionBarHandler().setMenuIdNames(Collections.<String>emptyList());
          }
          RenderResult renderedResult = service.render();
          if (renderedResult != null) {
            RenderSession session = renderedResult.getSession();
            if (session != null) {
              Result result = session.getResult();
              if (result.isSuccess()) {
                setRenderResult(renderedResult);
                service.dispose();
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
