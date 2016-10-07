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

package org.jetbrains.android.dom;

import com.android.SdkConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.SoftHashMap;
import com.intellij.util.xml.DefinesXml;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.dom.DomElementXmlDescriptor;
import icons.AndroidIcons;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.dom.xml.XmlResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.LayoutViewClassUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class AndroidDomElementDescriptorProvider implements XmlElementDescriptorProvider {
  private static final Map<String, Ref<Icon>> ourViewTagName2Icon = new SoftHashMap<>();

  @Nullable
  private static XmlElementDescriptor getDescriptor(DomElement domElement, XmlTag tag, @Nullable String baseClassName) {
    AndroidFacet facet = AndroidFacet.getInstance(domElement);
    if (facet == null) return null;
    final String name = domElement.getXmlTag().getName();
    final PsiClass aClass = baseClassName != null
                            ? LayoutViewClassUtils.findClassByTagName(facet, name, baseClassName)
                            : null;
    final Icon icon = getIconForTag(name, domElement);

    final DefinesXml definesXml = domElement.getAnnotation(DefinesXml.class);
    if (definesXml != null) {
      return new AndroidXmlTagDescriptor(aClass, new DomElementXmlDescriptor(domElement), baseClassName, icon);
    }
    final PsiElement parent = tag.getParent();
    if (parent instanceof XmlTag) {
      final XmlElementDescriptor parentDescriptor = ((XmlTag)parent).getDescriptor();

      if (parentDescriptor != null && parentDescriptor instanceof AndroidXmlTagDescriptor) {
        XmlElementDescriptor domDescriptor = parentDescriptor.getElementDescriptor(tag, (XmlTag)parent);
        if (domDescriptor != null) {
          return new AndroidXmlTagDescriptor(aClass, domDescriptor, baseClassName, icon);
        }
      }
    }
    return null;
  }

  @Override
  public XmlElementDescriptor getDescriptor(XmlTag tag) {
    final Pair<AndroidDomElement, String> pair = getDomElementAndBaseClassQName(tag);
    if (pair == null) {
      return null;
    }
    return getDescriptor(pair.getFirst(), tag, pair.getSecond());
  }

  @Nullable
  public static Pair<AndroidDomElement, String> getDomElementAndBaseClassQName(@NotNull XmlTag tag) {
    final PsiFile file = tag.getContainingFile();
    if (!(file instanceof XmlFile)) return null;
    Project project = file.getProject();
    if (project.isDefault()) return null;

    final DomManager domManager = DomManager.getDomManager(project);
    if (domManager.getFileElement((XmlFile)file, AndroidDomElement.class) == null) return null;
    
    final DomElement domElement = domManager.getDomElement(tag);
    if (!(domElement instanceof AndroidDomElement)) {
      return null;
    }

    String className = null;
    if (domElement instanceof LayoutViewElement) {
      className = AndroidUtils.VIEW_CLASS_NAME;
    }
    else if (domElement instanceof XmlResourceElement) {
      className = SdkConstants.CLASS_PREFERENCE;
    }
    return Pair.create((AndroidDomElement)domElement, className);
  }

  @Nullable
  public static Icon getIconForTag(@Nullable String tagName, @Nullable DomElement context) {
    if (tagName == null || !(context instanceof LayoutViewElement)) {
      return null;
    }
    return getIconForViewTag(tagName);
  }

  @Nullable
  public static Icon getIconForViewTag(@NotNull String tagName) {
    synchronized (ourViewTagName2Icon) {
      if (ourViewTagName2Icon.isEmpty()) {
        final Map<String, Icon> map = getInitialViewTagName2IconMap();

        for (Map.Entry<String, Icon> entry : map.entrySet()) {
          ourViewTagName2Icon.put(entry.getKey(), Ref.create(entry.getValue()));
        }
      }
      Ref<Icon> iconRef = ourViewTagName2Icon.get(tagName);

      if (iconRef == null) {
        iconRef = Ref.create(IconLoader.findIcon("AndroidIcons.Views." + tagName));
        ourViewTagName2Icon.put(tagName, iconRef);
      }
      return iconRef.get();
    }
  }

  @NotNull
  private static Map<String, Icon> getInitialViewTagName2IconMap() {
    final HashMap<String, Icon> map = new HashMap<>();
    map.put("fragment", AndroidIcons.Views.Fragment);
    map.put("include", AndroidIcons.Views.Include);
    map.put("view", AndroidIcons.Views.View);
    map.put("requestFocus", AndroidIcons.Views.RequestFocus);
    return map;
  }
}
