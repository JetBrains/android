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
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.SimpleClassMapConstructor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AndroidXmlTagDescriptor implements XmlElementDescriptor, PsiPresentableMetaData {
  private final XmlElementDescriptor myParentDescriptor;
  private final PsiClass myDeclarationClass;
  private final Icon myIcon;
  private final String myBaseClassName;

  public AndroidXmlTagDescriptor(@Nullable PsiClass declarationClass,
                                 @NotNull XmlElementDescriptor parentDescriptor,
                                 @Nullable String baseClassName,
                                 @Nullable Icon icon) {
    myParentDescriptor = parentDescriptor;
    myDeclarationClass = declarationClass;
    myIcon = icon;
    myBaseClassName = baseClassName;
  }

  @Override
  public String getQualifiedName() {
    return myParentDescriptor.getQualifiedName();
  }

  @Override
  public String getDefaultName() {
    return myParentDescriptor.getDefaultName();
  }

  @Override
  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    final XmlElementDescriptor[] descriptors = myParentDescriptor.getElementsDescriptors(context);

    if (myBaseClassName == null || context == null) {
      return descriptors;
    }
    final AndroidFacet facet = AndroidFacet.getInstance(context);

    if (facet == null) {
      return descriptors;
    }
    final XmlElementDescriptor[] androidDescriptors = new XmlElementDescriptor[descriptors.length];
    final DomElement domElement = DomManager.getDomManager(context.getProject()).getDomElement(context);
    final PsiClass baseClass = JavaPsiFacade.getInstance(context.getProject()).findClass(
      myBaseClassName, facet.getModule().getModuleWithLibrariesScope());

    for (int i = 0; i < descriptors.length; i++) {
      final XmlElementDescriptor descriptor = descriptors[i];
      final String tagName = descriptor.getName();
      final PsiClass aClass = tagName != null && baseClass != null
                              ? SimpleClassMapConstructor.findClassByTagName(facet, tagName, baseClass)
                              : null;
      final Icon icon = AndroidDomElementDescriptorProvider.getIconForTag(tagName, domElement);
      androidDescriptors[i] = new AndroidXmlTagDescriptor(aClass, descriptor, myBaseClassName, icon);
    }
    return androidDescriptors;
  }

  @Override
  public XmlElementDescriptor getElementDescriptor(XmlTag childTag, XmlTag contextTag) {
    final XmlElementDescriptor descriptor = myParentDescriptor.getElementDescriptor(childTag, contextTag);
    if (descriptor != null) {
      return descriptor;
    }

    final XmlNSDescriptor nsDescriptor = getNSDescriptor();
    return nsDescriptor != null ? new AndroidAnyTagDescriptor(nsDescriptor) : null;
  }

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(@Nullable XmlTag context) {
    final XmlAttributeDescriptor[] descriptors = myParentDescriptor.getAttributesDescriptors(context);

    // The rest of the function below ensures that layout_width attribute descriptor comes before
    // layout_height descriptor. Order of these is significant for automatic attribute insertion on
    // tag autocompletion (commenting out ArrayUtil.swap below breaks a bunch of unit tests).

    // Discussion of that on JetBrains issue tracker: https://youtrack.jetbrains.com/issue/IDEA-89857

    int layoutWidthIndex = -1;
    int layoutHeightIndex = -1;

    for (int i = 0; i < descriptors.length; i++) {
      final String name = descriptors[i].getName();

      if ("layout_width".equals(name)) {
        layoutWidthIndex = i;
      }
      else if ("layout_height".equals(name)) {
        layoutHeightIndex = i;
      }
    }
    if (layoutWidthIndex >= 0 && layoutHeightIndex >= 0 && layoutWidthIndex > layoutHeightIndex) {
      final XmlAttributeDescriptor[] result = descriptors.clone();
      ArrayUtil.swap(result, layoutWidthIndex, layoutHeightIndex);
      return result;
    }
    return descriptors;
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(@NonNls String attributeName, @Nullable XmlTag context) {
    final XmlAttributeDescriptor descriptor = myParentDescriptor.getAttributeDescriptor(attributeName, context);
    return descriptor != null ? descriptor : new AndroidAnyAttributeDescriptor(attributeName);
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
    final XmlAttributeDescriptor descriptor = myParentDescriptor.getAttributeDescriptor(attribute);
    return descriptor != null ? descriptor : new AndroidAnyAttributeDescriptor(attribute.getName());
  }

  @Override
  public XmlNSDescriptor getNSDescriptor() {
    return myParentDescriptor.getNSDescriptor();
  }

  @Override
  public XmlElementsGroup getTopGroup() {
    return null;
  }

  @Override
  public int getContentType() {
    if (myDeclarationClass != null) {
      final GlobalSearchScope scope = myDeclarationClass.getResolveScope();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(myDeclarationClass.getProject());
      final PsiClass view = facade.findClass(SdkConstants.CLASS_VIEW, scope);

      if (view != null && myDeclarationClass.isInheritor(view, true)) {
        final PsiClass viewGroup = facade.findClass(SdkConstants.CLASS_VIEWGROUP, scope);

        if (viewGroup != null) {
          return myDeclarationClass.isInheritor(viewGroup, true) ? CONTENT_TYPE_MIXED : CONTENT_TYPE_EMPTY;
        }
      }
    }
    return myParentDescriptor.getContentType();
  }

  @Override
  public String getDefaultValue() {
    return null;
  }

  @Override
  public PsiElement getDeclaration() {
    return myDeclarationClass != null ? myDeclarationClass : myParentDescriptor.getDeclaration();
  }

  @Override
  public String getName(PsiElement context) {
    return getDefaultName();
  }

  @Override
  public String getName() {
    return myParentDescriptor.getName();
  }

  @Override
  public void init(PsiElement element) {
    myParentDescriptor.init(element);
  }

  @Override
  public Object[] getDependences() {
    return myParentDescriptor.getDependences();
  }

  @Override
  public String getTypeName() {
    return null;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return myIcon;
  }
}
