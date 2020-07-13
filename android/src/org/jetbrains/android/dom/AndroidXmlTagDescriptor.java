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

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidXmlTagDescriptor implements XmlElementDescriptor {
  private final XmlElementDescriptor myParentDescriptor;

  public AndroidXmlTagDescriptor(@NotNull XmlElementDescriptor parentDescriptor) {
    myParentDescriptor = parentDescriptor;
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
    return myParentDescriptor.getElementsDescriptors(context);
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
    return myParentDescriptor.getAttributesDescriptors(context);
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
    return myParentDescriptor.getContentType();
  }

  @Override
  public String getDefaultValue() {
    return null;
  }

  @Override
  public PsiElement getDeclaration() {
    return myParentDescriptor.getDeclaration();
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

  @NotNull
  @Override
  public Object[] getDependencies() {
    return myParentDescriptor.getDependencies();
  }
}
