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

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
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
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
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
import java.io.File;

import static com.android.SdkConstants.*;

/**
 * Annotator which puts colors in the editor gutter for both color files, as well
 * as any XML resource that references a color attribute (\@color) or color literal (#AARRGGBBB),
 * or references it from Java code (R.color.name). It also previews small icons.
 */
public class AndroidColorAnnotator implements Annotator {
  private static final int ICON_SIZE = 8;
  private static final String COLOR_PREFIX = "@color/";
  private static final String ANDROID_COLOR_PREFIX = "@android:color/";
  private static final String DRAWABLE_PREFIX = "@drawable/";

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof XmlTag) {
      XmlTag tag = (XmlTag)element;
      String tagName = tag.getName();
      if ((ResourceType.COLOR.getName().equals(tagName) || ResourceType.DRAWABLE.getName().equals(tagName))) {
        DomElement domElement = DomManager.getDomManager(element.getProject()).getDomElement(tag);
        if (domElement instanceof ResourceElement) {
          String value = tag.getValue().getText().trim();
          annotateXml(element, holder, value);
        }
      } else if (SdkConstants.TAG_ITEM.equals(tagName)) {
        XmlTagValue value = tag.getValue();
        String text = value.getText();
        annotateXml(element, holder, text);
      }
    } else if (element instanceof XmlAttributeValue) {
      XmlAttributeValue v = (XmlAttributeValue)element;
      String value = v.getValue();
      if (value == null || value.isEmpty()) {
        return;
      }
      annotateXml(element, holder, value);
    } else if (element instanceof PsiReferenceExpression && AndroidPsiUtils.isResourceReference(element)) {
      // (isResourceReference will return true for both "R.drawable.foo" and the foo literal leaf in the
      // same expression, which would result in both elements getting annotated and the icon showing up
      // in the gutter twice. Instead we only count the outer one.
      ResourceType type = AndroidPsiUtils.getResourceType(element);
      if (type == ResourceType.COLOR || type == ResourceType.DRAWABLE) {
        String name = AndroidPsiUtils.getResourceName(element);
        annotateResourceReference(type, holder, element, name, false);
      }
    }
  }

  private static void annotateXml(PsiElement element, AnnotationHolder holder, String value) {
    if (value.startsWith("#")) {
      if (element instanceof XmlTag) {
        Annotation annotation = holder.createInfoAnnotation(element, null);
        annotation.setGutterIconRenderer(new MyRenderer(element, null));
      } else {
        assert element instanceof XmlAttributeValue;
        Color color = ResourceHelper.parseColor(value);
        if (color != null) {
          Annotation annotation = holder.createInfoAnnotation(element, null);
          annotation.setGutterIconRenderer(new MyRenderer(element, null));
        }
      }
    } else if (value.startsWith(COLOR_PREFIX)) {
      annotateResourceReference(ResourceType.COLOR, holder, element, value.substring(COLOR_PREFIX.length()), false);
    } else if (value.startsWith(ANDROID_COLOR_PREFIX)) {
      annotateResourceReference(ResourceType.COLOR, holder, element, value.substring(ANDROID_COLOR_PREFIX.length()), true);
    } else if (value.startsWith(DRAWABLE_PREFIX)) {
      annotateResourceReference(ResourceType.DRAWABLE, holder, element, value.substring(DRAWABLE_PREFIX.length()), false);
    } else if (value.startsWith(ANDROID_DRAWABLE_PREFIX)) {
      annotateResourceReference(ResourceType.DRAWABLE, holder, element, value.substring(ANDROID_DRAWABLE_PREFIX.length()), false);
    }
  }

  /**
   * When annotating Java files, we need to find an associated layout file to pick the resource
   * resolver from (e.g. to for example have a theme association which will drive how colors are
   * resolved). This file picks one of the open layout files, and if not found, the first layout
   * file found in the resources (if any).
   * */
  @Nullable
  private static VirtualFile pickLayoutFile(@NotNull Module module, @NotNull AndroidFacet facet) {
    VirtualFile layout = null;
    VirtualFile[] openFiles = FileEditorManager.getInstance(module.getProject()).getOpenFiles();
    for (VirtualFile file : openFiles) {
      if (file.getName().endsWith(DOT_XML) && file.getParent() != null &&
          file.getParent().getName().startsWith(FD_RES_LAYOUT)) {
        layout = file;
        break;
      }
    }

    if (layout == null) {
      // Pick among actual files in the project
      for (VirtualFile resourceDir : facet.getAllResourceDirectories()) {
        for (VirtualFile folder : resourceDir.getChildren()) {
          if (folder.getName().startsWith(FD_RES_LAYOUT) && folder.isDirectory()) {
            for (VirtualFile file : folder.getChildren()) {
              if (file.getName().endsWith(DOT_XML) && file.getParent() != null &&
                  file.getParent().getName().startsWith(FD_RES_LAYOUT)) {
                layout = file;
                break;
              }
            }
          }
        }
      }
    }
    return layout;
  }

  private static void annotateResourceReference(@NotNull ResourceType type,
                                                @NotNull AnnotationHolder holder,
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

    Configuration configuration = pickConfiguration(facet, module, file);
    if (configuration == null) {
      return;
    }

    ResourceValue value = findResourceValue(type, name, isFramework, module, configuration);
    if (value != null) {
      // TODO: Use a *shared* fallback resolver for this?
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      if (resourceResolver != null) {
        annotateResourceValue(type, holder, element, value, resourceResolver);
      }
    }
  }

  /** Picks a suitable configuration to use for resource resolution */
  @Nullable
  private static Configuration pickConfiguration(AndroidFacet facet, Module module, PsiFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }

    VirtualFile layout;
    String parentName = virtualFile.getParent().getName();
    if (!parentName.startsWith(FD_RES_LAYOUT)) {
      layout = pickLayoutFile(module, facet);
      if (layout == null) {
        return null;
      }
    } else {
      layout = virtualFile;
    }

    return facet.getConfigurationManager().getConfiguration(layout);
  }

  /** Annotates the given element with the resolved value of the given {@link ResourceValue} */
  private static void annotateResourceValue(@NotNull ResourceType type,
                                            @NotNull AnnotationHolder holder,
                                            @NotNull PsiElement element,
                                            @NotNull ResourceValue value,
                                            @NotNull ResourceResolver resourceResolver) {
    if (type == ResourceType.COLOR) {
      Color color = ResourceHelper.resolveColor(resourceResolver, value);
      if (color != null) {
        Annotation annotation = holder.createInfoAnnotation(element, null);
        annotation.setGutterIconRenderer(new MyRenderer(element, color));
      }
    } else {
      assert type == ResourceType.DRAWABLE;
      // TODO: Supported nested resolution, as is handled by ResourceHelper.resolveColor
      // TODO: Pick the smallest resolution, if possible! E.g. if the theme resolver located
      //    drawable-hdpi/foo.png, and drawable-mdpi/foo.png pick that one instead (and ditto
      //    for -ldpi etc)
      //    This is probably simplest by just iterating through the source files in the
      //    ResourceItem if it's not a value alias
      String path = value.getValue();
      if (path != null && path.endsWith(DOT_PNG)) {
        File iconFile = new File(path);
        if (iconFile.exists()) {
          // Try to find the smallest resolution of the same image
          //String parentName = file.getParentFile().getName();
          long length = iconFile.length();
          if (length > 5000) { // Don't try to load large images
            return;
          }
          Annotation annotation = holder.createInfoAnnotation(element, null);
          annotation.setGutterIconRenderer(new com.android.tools.idea.rendering.GutterIconRenderer(element, iconFile));
        }
      }
    }
  }

  /** Looks up the resource item of the given type and name for the given configuration, if any */
  @Nullable
  private static ResourceValue findResourceValue(ResourceType type,
                                                 String name,
                                                 boolean isFramework,
                                                 Module module,
                                                 Configuration configuration) {
    if (isFramework) {
      ResourceRepository frameworkResources = configuration.getFrameworkResources();
      if (frameworkResources == null) {
        return null;
      }
      if (!frameworkResources.hasResourceItem(type, name)) {
        return null;
      }
      ResourceItem item = frameworkResources.getResourceItem(type, name);
      return item.getResourceValue(type, configuration.getFullConfig(), false);
    } else {
      ProjectResources projectResources = ProjectResources.get(module, true, true);
      if (projectResources == null) {
        return null;
      }
      if (!projectResources.hasResourceItem(type, name)) {
        return null;
      }
      return projectResources.getConfiguredValue(type, name, configuration.getFullConfig());
    }
  }

  @VisibleForTesting
  static String colorToString(Color color) {
    long longColor = ((long)color.getAlpha() << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    return '#' + Long.toHexString(longColor);
  }

  private static class MyRenderer extends GutterIconRenderer {
    private final PsiElement myElement;
    private final Color myColor;

    private MyRenderer(@NotNull PsiElement element, @Nullable Color color) {
      myElement = element;
      myColor = color;
    }

    @NotNull
    @Override
    public Icon getIcon() {
      final Color color = getCurrentColor();
      return color == null ? EmptyIcon.create(ICON_SIZE) : new ColorIcon(ICON_SIZE, color);
    }

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
      // TODO: Compare with modification count in project resources (if not framework)
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
