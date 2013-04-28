/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.ProjectResources;
import com.android.tools.idea.rendering.ResourceHelper;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ColorChooser;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Annotator which puts colors in the editor gutter for both color files, as well
 * as any XML resource that references a color attribute (\@color) or color literal (#AARRGGBBB),
 * or references it from Java code (R.color.name)
 * <p>
 * TODO: Resolve resource references in Java files
 */
public class AndroidColorAnnotator implements Annotator {
  private static final int ICON_SIZE = 8;
  private static final String COLOR_PREFIX = "@color/";
  private static final String ANDROID_COLOR_PREFIX = "@android:color/";

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof XmlTag) {
      XmlTag tag = (XmlTag)element;
      if ((ResourceType.COLOR.getName().equals(tag.getName()) || ResourceType.DRAWABLE.getName().equals(tag.getName()))) {
        DomElement domElement = DomManager.getDomManager(element.getProject()).getDomElement(tag);
        if (domElement instanceof ResourceElement) {
          String value = tag.getValue().getText().trim();
          if (value.startsWith("#")) {
            Annotation annotation = holder.createInfoAnnotation(element, null);
            annotation.setGutterIconRenderer(new MyRenderer(element, null));
          } else if (value.startsWith(COLOR_PREFIX)) {
            annotateResourceReference(holder, element, value.substring(COLOR_PREFIX.length()), false);
          } else if (value.startsWith(ANDROID_COLOR_PREFIX)) {
            annotateResourceReference(holder, element, value.substring(ANDROID_COLOR_PREFIX.length()), true);
          }
        }
      }
    } else if (element instanceof XmlAttributeValue) {
      XmlAttributeValue v = (XmlAttributeValue)element;
      String value = v.getValue();
      if (value == null || value.isEmpty()) {
        return;
      }
      if (value.startsWith("#")) {
        Color color = ResourceHelper.parseColor(value);
        if (color != null) {
          Annotation annotation = holder.createInfoAnnotation(element, null);
          annotation.setGutterIconRenderer(new MyRenderer(element, null));
        }
      } else if (value.startsWith(COLOR_PREFIX)) {
        annotateResourceReference(holder, v, value.substring(COLOR_PREFIX.length()), false);
      } else if (value.startsWith(ANDROID_COLOR_PREFIX)) {
        annotateResourceReference(holder, v, value.substring(ANDROID_COLOR_PREFIX.length()), true);
      }
    }
  }

  private static void annotateResourceReference(@NotNull AnnotationHolder holder,
                                                @NotNull PsiElement element,
                                                @NotNull String name,
                                                boolean isFramework) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) {
      return;
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return;
    }

    PsiFile file = PsiTreeUtil.getParentOfType(element, PsiFile.class);
    if (file == null) {
      return;
    }
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return;
    }

    Configuration configuration = facet.getConfigurationManager().getConfiguration(virtualFile);
    ResourceItem item;
    if (isFramework) {
      ResourceRepository frameworkResources = configuration.getFrameworkResources();
      if (frameworkResources == null) {
        return;
      }
      if (!frameworkResources.hasResourceItem(ResourceType.COLOR, name)) {
        return;
      }
      item = frameworkResources.getResourceItem(ResourceType.COLOR, name);
    } else {
      ProjectResources projectResources = ProjectResources.get(module, true);
      if (projectResources == null) {
        return;
      }
      if (!projectResources.hasResourceItem(ResourceType.COLOR, name)) {
        return;
      }
      item = projectResources.getResourceItem(ResourceType.COLOR, name);
    }

    ResourceValue value = item.getResourceValue(ResourceType.COLOR, configuration.getFullConfig(), false);
    if (value == null) {
      return;
    }
    // TODO: Use a *shared* fallback resolver for this?
    ResourceResolver resourceResolver = configuration.getResourceResolver();
    if (resourceResolver == null) {
      return;
    }
    Color color = ResourceHelper.resolveColor(resourceResolver, value);
    if (color != null) {
      Annotation annotation = holder.createInfoAnnotation(element, null);
      annotation.setGutterIconRenderer(new MyRenderer(element, color));
    }
  }

  private static class MyRenderer extends GutterIconRenderer {
    private final PsiElement myElement;
    private final Color myColor;

    private MyRenderer(@NotNull PsiElement element, @Nullable Color color) {
      assert element instanceof XmlTag || element instanceof XmlAttributeValue;
      myElement = element;
      myColor = color;
    }

    @NotNull
    @Override
    public Icon getIcon() {
      final Color color = getCurrentColor();
      return color == null ? EmptyIcon.create(ICON_SIZE) : new ColorIcon(ICON_SIZE, color);
    }

    // see android.graphics.Color#parseColor in android.jar library
    @Nullable
    private Color getCurrentColor() {
      if (myColor != null) {
        return myColor;
      } else if (myElement instanceof XmlTag) {
        return ResourceHelper.parseColor(((XmlTag)myElement).getValue().getText());
      } else if (myElement instanceof XmlAttributeValue) {
        return ResourceHelper.parseColor(((XmlAttributeValue)myElement).getValue());
      } else {
        return null;
      }
    }

    // see see android.graphics.Color in android.jar library
    private static String colorToString(Color color) {
      int intColor = (color.getAlpha() << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
      return '#' + Integer.toHexString(intColor);
    }

    @Override
    public AnAction getClickAction() {
      if (myColor != null) { // Cannot set colors that were derived
        return null;
      }
      return new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          final Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
          if (editor != null) {
            final Color color =
              ColorChooser.chooseColor(editor.getComponent(), AndroidBundle.message("android.choose.color"), getCurrentColor());
            if (color != null) {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  if (myElement instanceof XmlTag) {
                    ((XmlTag)myElement).getValue().setText(colorToString(color));
                  } else if (myElement instanceof XmlAttributeValue) {
                    XmlAttribute attribute = PsiTreeUtil.getParentOfType(myElement, XmlAttribute.class);
                    if (attribute != null) {
                      attribute.setValue(colorToString(color));
                    }
                  }
                }
              });
            }
          }
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyRenderer that = (MyRenderer)o;

      if (myColor != null ? !myColor.equals(that.myColor) : that.myColor != null) return false;
      if (!myElement.equals(that.myElement)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myElement.hashCode();
      result = 31 * result + (myColor != null ? myColor.hashCode() : 0);
      return result;
    }
  }
}
