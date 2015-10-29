/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.dom;

import com.android.resources.ResourceType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Builds data model for structure view (can be invoked with Alt+7 or Ctrl+F12 on default keymap)
 * for Android resources files.
 */
public class ResourceStructureViewBuilder extends TreeBasedStructureViewBuilder {
  private final DomFileElement<Resources> myResources;

  public ResourceStructureViewBuilder(@NotNull DomFileElement<Resources> resources) {
    myResources = resources;
  }

  @NotNull
  StructureViewModel createStructureViewModel() {
    return new StructureViewModelBase(myResources.getFile(), new Root(myResources));
  }

  @NotNull
  @Override
  public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
    return new StructureViewModelBase(myResources.getFile(), new Root(myResources));
  }

  private static class Root extends PsiTreeElementBase<PsiElement> {
    private final DomFileElement<Resources> myResources;

    Root(@NotNull DomFileElement<Resources> resources) {
      super(resources.getXmlElement());
      myResources = resources;
    }

    @NotNull
    @Override
    public Collection<StructureViewTreeElement> getChildrenBase() {
      final ArrayList<Leaf> result = Lists.newArrayList();
      myResources.getRootElement().acceptChildren(new DomElementVisitor() {
        @Override
        public void visitDomElement(DomElement element) {
          if (element instanceof ResourceElement) {
            final ResourceElement resourceElement = (ResourceElement)element;
            final ResourceType type = AndroidResourceUtil.getType(resourceElement.getXmlTag());

            final String name = ((ResourceElement)element).getName().getValue();
            final XmlElement xmlElement = element.getXmlElement();
            if (name != null && type != null && xmlElement != null) {
              result.add(new Leaf(xmlElement, name, type));
            }
          }
        }
      });
      // acceptChildren doesn't call visitor on children in the order they appear in file,
      // but calls it using the order children appear in defining DOM interface.
      // Thus, to make all the elements appear in the order they are in source file, we
      // need to sort them by offset.
      Collections.sort(result);

      // Copy is required because method's signature is Collection<StructureViewTreeElement>,
      // not Collection<? extends StructureViewTreeElement>
      return ImmutableList.<StructureViewTreeElement>copyOf(result);
    }

    @NotNull
    @Override
    public String getPresentableText() {
      return String.format("Resources file '%s'", myResources.getFile().getName());
    }

    @Override
    public String toString() {
      final StringBuilder builder = new StringBuilder(getPresentableText());
      builder.append('\n');
      for (StructureViewTreeElement child : getChildrenBase()) {
        builder.append("  ").append(child.toString()).append('\n');
      }
      return builder.toString();
    }
  }

  private static class Leaf extends PsiTreeElementBase<PsiElement> implements Comparable<Leaf> {
    private final String myName;
    private final ResourceType myResourceType;
    private final int myOffset;

    protected Leaf(@NotNull PsiElement psiElement, @NotNull String name, @NotNull ResourceType resourceType) {
      super(psiElement);
      myName = name;
      myResourceType = resourceType;
      myOffset = psiElement.getTextOffset();
    }

    @NotNull
    @Override
    public Collection<StructureViewTreeElement> getChildrenBase() {
      return Collections.emptyList();
    }

    @Nullable
    @Override
    public String getPresentableText() {
      return myResourceType.getDisplayName() + " - " + myName;
    }

    @Override
    public int compareTo(Leaf o) {
      return (o == null) ? 1 : Ints.compare(myOffset, o.myOffset);
    }

    @Override
    public String toString() {
      return getPresentableText();
    }
  }
}
