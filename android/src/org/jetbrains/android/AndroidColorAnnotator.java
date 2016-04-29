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

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ColorPicker;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.AndroidPsiUtils.ResourceReferenceType;

/**
 * Annotator which puts colors in the editor gutter for both color files, as well
 * as any XML resource that references a color attribute (\@color) or color literal (#AARRGGBBB),
 * or references it from Java code (R.color.name). It also previews small icons.
 * <p>
 * TODO: Use {@link com.android.ide.common.resources.ResourceItemResolver} when possible!
 *
 * TODO: Add test. Unfortunately, it looks like none of the existing Annotator classes
 * in IntelliJ have unit tests, so there doesn't appear to be fixture support for this.
 */
public class AndroidColorAnnotator implements Annotator {
  private static final int ICON_SIZE = 8;
  private static final int MAX_ICON_SIZE = 5000;

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // disable it in test mode temporary, because of failing AndroidLayoutDomTest#testAttrReferences1()
      return;
    }
    if (element instanceof XmlTag) {
      XmlTag tag = (XmlTag)element;
      String tagName = tag.getName();
      if ((ResourceType.COLOR.getName().equals(tagName) || ResourceType.DRAWABLE.getName().equals(tagName)
            || ResourceType.MIPMAP.getName().equals(tagName))) {
        DomElement domElement = DomManager.getDomManager(element.getProject()).getDomElement(tag);
        if (domElement instanceof ResourceElement) {
          String value = tag.getValue().getText().trim();
          annotateXml(element, holder, value);
        }
      } else if (TAG_ITEM.equals(tagName)) {
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
    } else if (element instanceof PsiReferenceExpression) {
      ResourceReferenceType referenceType = AndroidPsiUtils.getResourceReferenceType(element);
      if (referenceType != ResourceReferenceType.NONE) {
        // (isResourceReference will return true for both "R.drawable.foo" and the foo literal leaf in the
        // same expression, which would result in both elements getting annotated and the icon showing up
        // in the gutter twice. Instead we only count the outer one.
        ResourceType type = AndroidPsiUtils.getResourceType(element);
        if (type == ResourceType.COLOR || type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP) {
          String name = AndroidPsiUtils.getResourceName(element);
          annotateResourceReference(type, holder, element, name, referenceType == ResourceReferenceType.FRAMEWORK);
        }
      }
    }
  }

  private static void annotateXml(PsiElement element, AnnotationHolder holder, String value) {
    if (value.startsWith("#")) {
      final PsiFile file = element.getContainingFile();
      if (file != null && AndroidResourceUtil.isInResourceSubdirectory(file, null)) {
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
      }
    } else if (value.startsWith(COLOR_RESOURCE_PREFIX)) {
      annotateResourceReference(ResourceType.COLOR, holder, element, value.substring(COLOR_RESOURCE_PREFIX.length()), false);
    } else if (value.startsWith(ANDROID_COLOR_RESOURCE_PREFIX)) {
      annotateResourceReference(ResourceType.COLOR, holder, element, value.substring(ANDROID_COLOR_RESOURCE_PREFIX.length()), true);
    } else if (value.startsWith(DRAWABLE_PREFIX)) {
      annotateResourceReference(ResourceType.DRAWABLE, holder, element, value.substring(DRAWABLE_PREFIX.length()), false);
    } else if (value.startsWith(ANDROID_DRAWABLE_PREFIX)) {
      annotateResourceReference(ResourceType.DRAWABLE, holder, element, value.substring(ANDROID_DRAWABLE_PREFIX.length()), true);
    } else if (value.startsWith(MIPMAP_PREFIX)) {
      annotateResourceReference(ResourceType.MIPMAP, holder, element, value.substring(MIPMAP_PREFIX.length()), false);
    }
  }

  /**
   * When annotating Java files, we need to find an associated layout file to pick the resource
   * resolver from (e.g. to for example have a theme association which will drive how colors are
   * resolved). This file picks one of the open layout files, and if not found, the first layout
   * file found in the resources (if any).
   * */
  @Nullable
  public static VirtualFile pickLayoutFile(@NotNull Module module, @NotNull AndroidFacet facet) {
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
    Project project = element.getProject();
    if (type == ResourceType.COLOR) {
      Color color = ResourceHelper.resolveColor(resourceResolver, value, project);
      if (color != null) {
        Annotation annotation = holder.createInfoAnnotation(element, null);
        annotation.setGutterIconRenderer(new MyRenderer(element, color));
      }
    } else {
      assert type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP;

      File file = ResourceHelper.resolveDrawable(resourceResolver, value, project);
      if (file != null && file.getPath().endsWith(DOT_XML)) {
        file = pickBitmapFromXml(file, resourceResolver, project);
      }
      File iconFile = pickBestBitmap(file);
      if (iconFile != null) {
        Annotation annotation = holder.createInfoAnnotation(element, null);
        annotation.setGutterIconRenderer(new com.android.tools.idea.rendering.GutterIconRenderer(element, iconFile));
      }
    }
  }

  @Nullable
  private static File pickBitmapFromXml(@NotNull File file, @NotNull ResourceResolver resourceResolver, @NotNull Project project) {
    try {
      String xml = Files.toString(file, Charsets.UTF_8);
      Document document = XmlUtils.parseDocumentSilently(xml, true);
      if (document != null && document.getDocumentElement() != null) {
        Element root = document.getDocumentElement();
        String tag = root.getTagName();
        Element target = null;
        String attribute = null;
        if ("vector".equals(tag)) {
          // Vectors are handled in the icon cache
          return file;
        }
        else if ("bitmap".equals(tag) || "nine-patch".equals(tag)) {
          target = root;
          attribute = ATTR_SRC;
        }
        else if ("selector".equals(tag) ||
                 "level-list".equals(tag) ||
                 "layer-list".equals(tag) ||
                 "transition".equals(tag)) {
          NodeList children = root.getChildNodes();
          for (int i = children.getLength() - 1; i >= 0; i--) {
            Node item = children.item(i);
            if (item.getNodeType() == Node.ELEMENT_NODE && TAG_ITEM.equals(item.getNodeName())) {
              target = (Element)item;
              if (target.hasAttributeNS(ANDROID_URI, ATTR_DRAWABLE)) {
                attribute = ATTR_DRAWABLE;
                break;
              }
            }
          }
        }
        else if ("clip".equals(tag) || "inset".equals(tag) || "scale".equals(tag)) {
          target = root;
          attribute = ATTR_DRAWABLE;
        } else {
          // <shape> etc - no bitmap to be found
          return null;
        }
        if (attribute != null && target.hasAttributeNS(ANDROID_URI, attribute)) {
          String src = target.getAttributeNS(ANDROID_URI, attribute);
          ResourceValue value = resourceResolver.findResValue(src, false);
          if (value != null) {
            return ResourceHelper.resolveDrawable(resourceResolver, value, project);

          }
        }
      }
    } catch (Throwable ignore) {
      // Not logging for now; afraid to risk unexpected crashes in upcoming preview. TODO: Re-enable.
      //Logger.getInstance(AndroidColorAnnotator.class).warn(String.format("Could not read/render icon image %1$s", file), e);
    }

    return null;
  }

  @Nullable
  public static File pickBestBitmap(@Nullable File bitmap) {
    if (bitmap != null && bitmap.exists()) {
      // Pick the smallest resolution, if possible! E.g. if the theme resolver located
      // drawable-hdpi/foo.png, and drawable-mdpi/foo.png pick that one instead (and ditto
      // for -ldpi etc)
      File smallest = findSmallestDpiVersion(bitmap);
      if (smallest != null) {
        return smallest;
      }

      // TODO: For XML drawables, look in the rendered output to see if there's a DPI version we can use:
      // These are found in  ${module}/build/generated/res/pngs/debug/drawable-*dpi

      long length = bitmap.length();
      if (length < MAX_ICON_SIZE) {
        return bitmap;
      }
    }

    return null;
  }

  @Nullable
  private static File findSmallestDpiVersion(@NonNull File bitmap) {
    File parentFile = bitmap.getParentFile();
    if (parentFile == null) {
      return null;
    }
    File resFolder = parentFile.getParentFile();
    if (resFolder == null) {
      return null;
    }
    String parentName = parentFile.getName();
    FolderConfiguration config = FolderConfiguration.getConfigForFolder(parentName);
    if (config == null) {
      return null;
    }
    DensityQualifier qualifier = config.getDensityQualifier();
    if (qualifier == null) {
      return null;
    }
    Density density = qualifier.getValue();
    if (density != null && density.isValidValueForDevice()) {
      String fileName = bitmap.getName();
      Density[] densities = Density.values();
      // Iterate in reverse, since the Density enum is in descending order
      for (int i = densities.length - 1; i >= 0; i--) {
        Density d = densities[i];
        if (d.isValidValueForDevice()) {
          String folder = parentName.replace(density.getResourceValue(), d.getResourceValue());
          bitmap = new File(resFolder, folder + File.separator + fileName);
          if (bitmap.exists()) {
            if (bitmap.length() > MAX_ICON_SIZE) {
              // No point continuing the loop; the other densities will be too big too
              return null;
            }
            return bitmap;
          }
        }
      }
    }

    return null;
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
      LocalResourceRepository appResources = AppResourceRepository.getAppResources(module, true);
      if (appResources == null) {
        return null;
      }
      if (!appResources.hasResourceItem(type, name)) {
        return null;
      }
      return appResources.getConfiguredValue(type, name, configuration.getFullConfig());
    }
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
          final Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
          if (editor != null) {
            // Need ARGB support in platform color chooser; see
            //  https://youtrack.jetbrains.com/issue/IDEA-123498
            //final Color color =
            //  ColorChooser.chooseColor(editor.getComponent(), AndroidBundle.message("android.choose.color"), getCurrentColor());
            final Color color = ColorPicker.showDialog(editor.getComponent(), "Choose Color", getCurrentColor(), true, null, false);
            if (color != null) {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  if (myElement instanceof XmlTag) {
                    ((XmlTag)myElement).getValue().setText(ResourceHelper.colorToString(color));
                  } else if (myElement instanceof XmlAttributeValue) {
                    XmlAttribute attribute = PsiTreeUtil.getParentOfType(myElement, XmlAttribute.class);
                    if (attribute != null) {
                      attribute.setValue(ResourceHelper.colorToString(color));
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
      // TODO: Compare with modification count in app resources (if not framework)
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
