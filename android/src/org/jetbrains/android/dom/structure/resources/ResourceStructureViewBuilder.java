/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android.dom.structure.resources;

import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
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
import org.jetbrains.android.dom.resources.Attr;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.dom.resources.StyleItem;
import org.jetbrains.android.dom.structure.StructureUtils;
import com.android.tools.idea.res.IdeResourcesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Structure view builder for &lt;resources&gt; XML files
 */
public class ResourceStructureViewBuilder extends TreeBasedStructureViewBuilder {
  private final DomFileElement<Resources> myResources;

  public ResourceStructureViewBuilder(@NotNull DomFileElement<Resources> resources) {
    myResources = resources;
  }

  @NotNull
  @Override
  public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
    return new StructureViewModelBase(myResources.getFile(), new ResourcesRoot(myResources));
  }

  private static class ResourcesRoot extends PsiTreeElementBase<PsiElement> {
    private final DomFileElement<Resources> myResources;

    ResourcesRoot(@NotNull DomFileElement<Resources> resources) {
      super(resources.getXmlElement());
      myResources = resources;
    }

    @NotNull
    @Override
    public Collection<StructureViewTreeElement> getChildrenBase() {
      if (!myResources.isValid()) return Collections.emptySet();

      List<StructureViewTreeElement> result = new ArrayList<>();

      StructureUtils.acceptChildrenInOrder(myResources.getRootElement(), new DomElementVisitor() {
        /**
         * Instead of implementing this method, we add {@link #visitResourceElement(ResourceElement)} which is called reflectively only for
         * {@link ResourceElement}s.
         *
         * @see DomElementVisitor
         */
        @Override
        public void visitDomElement(DomElement element) {}

        public void visitResourceElement(ResourceElement element) {
          final ResourceType type = IdeResourcesUtil.getResourceTypeForResourceTag(element.getXmlTag());
          final String name = element.getName().getValue();
          final XmlElement xmlElement = element.getXmlElement();
          if (name != null && type != null && xmlElement != null) {
            result.add(new Resource(element, name, type));
          }
        }

        public void visitAttr(Attr element) {
          final ResourceReference resourceReference = element.getName().getValue();
          final XmlElement xmlElement = element.getXmlElement();
          if (resourceReference != null && xmlElement != null) {
            result.add(new Resource(element, resourceReference.getName(), resourceReference.getResourceType()));
          }
        }
      });
      return result;
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
        if (child instanceof Resource) {
          for (StructureViewTreeElement grandChild : ((Resource)child).getChildrenBase()) {
            builder.append("    ").append(grandChild.toString()).append('\n');
          }
        }
      }
      return builder.toString();
    }
  }

  private static class Resource extends PsiTreeElementBase<PsiElement> {
    private final String myName;
    private final ResourceType myResourceType;
    private final DomElement myElement;

    protected Resource(@NotNull DomElement element, @NotNull String name, @NotNull ResourceType resourceType) {
      super(element.getXmlTag());
      myElement = element;
      myName = name;
      myResourceType = resourceType;
    }

    @NotNull
    @Override
    public Collection<StructureViewTreeElement> getChildrenBase() {
      final List<StructureViewTreeElement> result = new ArrayList<>();
      final DomElementVisitor visitor = new DomElementVisitor() {
        public void visitStyleItem(StyleItem element) {
          final ResourceReference resourceReference = element.getName().getValue();
          if (resourceReference != null) {
            result.add(new Resource(element, resourceReference.getName(), ResourceType.STYLE_ITEM));
          }
        }

        public void visitAttr(Attr element) {
          final ResourceReference resourceReference = element.getName().getValue();
          final XmlElement xmlElement = element.getXmlElement();
          if (resourceReference != null && xmlElement != null) {
            result.add(new Resource(element, resourceReference.getName(), resourceReference.getResourceType()));
          }
        }

        @Override
        public void visitDomElement(DomElement element) {
        }
      };
      StructureUtils.acceptChildrenInOrder(myElement, visitor);
      return result;
    }

    @Nullable
    @Override
    public String getPresentableText() {
      return myResourceType.getDisplayName() + " - " + myName;
    }

    @Override
    public String toString() {
      return getPresentableText();
    }
  }
}
