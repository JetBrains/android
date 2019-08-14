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

import static com.android.SdkConstants.ANDROID_COLOR_RESOURCE_PREFIX;
import static com.android.SdkConstants.ANDROID_DRAWABLE_PREFIX;
import static com.android.SdkConstants.COLOR_RESOURCE_PREFIX;
import static com.android.SdkConstants.DRAWABLE_PREFIX;
import static com.android.SdkConstants.MIPMAP_PREFIX;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.tools.idea.AndroidPsiUtils.ResourceReferenceType;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItemResolver;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.rendering.GutterIconRenderer;
import com.android.tools.idea.res.ResourceHelper;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import java.awt.Color;
import org.jetbrains.android.AndroidAnnotatorUtil.ColorRenderer;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Annotator which puts colors in the editor gutter for both color files, as well
 * as any XML resource that references a color attribute (\@color) or color literal (#AARRGGBBB),
 * or references it from Java code (R.color.name). It also previews small icons.
 * <p>
 * TODO: Use {@link ResourceItemResolver} when possible!
 */
public class AndroidColorAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (StudioFlags.GUTTER_ICON_ANNOTATOR_IN_BACKGROUND_ENABLED.get()) {
      // Gutter icon annotator for Java and XML files is run as an external annotator when this flag is enabled.
      return;
    }
    if (element instanceof XmlTag) {
      XmlTag tag = (XmlTag)element;
      String tagName = tag.getName();
      if ((ResourceType.COLOR.getName().equals(tagName) || ResourceType.DRAWABLE.getName().equals(tagName)
            || ResourceType.MIPMAP.getName().equals(tagName))) {
        DomElement domElement = DomManager.getDomManager(element.getProject()).getDomElement(tag);
        if (domElement instanceof ResourceElement || ApplicationManager.getApplication().isUnitTestMode()) {
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
      if (value.isEmpty()) {
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

  private static void annotateXml(@NotNull PsiElement element, @NotNull AnnotationHolder holder, @NotNull String value) {
    if (value.startsWith("#")) {
      PsiFile file = element.getContainingFile();
      if (file != null && AndroidResourceUtil.isInResourceSubdirectory(file, null)) {
        if (element instanceof XmlTag) {
          Annotation annotation = holder.createInfoAnnotation(element, null);
          // TODO: put resource resolver
          annotation.setGutterIconRenderer(new ColorRenderer(element, null, true, null));
        } else {
          assert element instanceof XmlAttributeValue;
          Color color = ResourceHelper.parseColor(value);
          if (color != null) {
            Annotation annotation = holder.createInfoAnnotation(element, null);
            // TODO: put resource resolver
            annotation.setGutterIconRenderer(new ColorRenderer(element, null, true, null));
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
    Configuration configuration = AndroidAnnotatorUtil.pickConfiguration(file, facet);
    if (configuration == null) {
      return;
    }

    ResourceValue value = AndroidAnnotatorUtil.findResourceValue(type, name, isFramework, module, configuration);
    if (value != null) {
      annotateResourceValue(type, holder, element, value, facet, configuration);
    }
  }

  /** Annotates the given element with the resolved value of the given {@link ResourceValue}. */
  private static void annotateResourceValue(@NotNull ResourceType type,
                                            @NotNull AnnotationHolder holder,
                                            @NotNull PsiElement element,
                                            @NotNull ResourceValue value,
                                            @NotNull AndroidFacet facet,
                                            @NotNull Configuration configuration) {
    ResourceResolver resourceResolver = configuration.getResourceResolver();
    Project project = element.getProject();
    if (type == ResourceType.COLOR) {
      Color color = ResourceHelper.resolveColor(resourceResolver, value, project);
      if (color != null) {
        Annotation annotation = holder.createInfoAnnotation(element, null);
        // This adds the gutter icon for color reference in xml, java, and kotlin files.
        // For xml files, we want to open raw color and color resource picker.
        // For java and kotlin files, we should open color resource picker only and set R.color.[resource_name] to the field.
        // TODO: Open color resource picker for java and kotlin files.
        boolean isClickable = AndroidAnnotatorUtil.getFileType(element) == XmlFileType.INSTANCE;
        annotation.setGutterIconRenderer(new ColorRenderer(element, color, isClickable, configuration));
      }
    } else {
      assert type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP;

      VirtualFile iconFile = AndroidAnnotatorUtil.resolveDrawableFile(value, resourceResolver, facet);
      if (iconFile != null) {
        Annotation annotation = holder.createInfoAnnotation(element, null);
        annotation.setGutterIconRenderer(new GutterIconRenderer(resourceResolver, facet, iconFile, configuration));
      }
    }
  }
}
