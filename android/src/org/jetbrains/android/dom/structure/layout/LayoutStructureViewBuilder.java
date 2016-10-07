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
package org.jetbrains.android.dom.structure.layout;

import com.android.SdkConstants;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.DomFileElement;
import icons.AndroidIcons;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;
import org.jetbrains.android.dom.layout.Fragment;
import org.jetbrains.android.dom.layout.Include;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.dom.structure.StructureUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Builder of structure view for layout XML files
 */
public class LayoutStructureViewBuilder extends TreeBasedStructureViewBuilder {

  private final DomFileElement<LayoutViewElement> myElement;

  public LayoutStructureViewBuilder(@NotNull DomFileElement<LayoutViewElement> element) {
    myElement = element;
  }

  @NotNull
  @Override
  public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
    return new StructureViewModelBase(myElement.getFile(), new LayoutNode(myElement.getRootElement()));
  }

  /**
   * Tree node corresponding to &lt;fragment&gt; tag
   */
  private static class FragmentNode extends PsiTreeElementBase<XmlTag> {
    private final Fragment myElement;

    public FragmentNode(@NotNull Fragment element) {
      super(element.getXmlTag());
      myElement = element;
    }

    @NotNull
    @Override
    public Collection<StructureViewTreeElement> getChildrenBase() {
      return Collections.emptyList();
    }

    @Override
    public Icon getIcon(boolean open) {
      return AndroidIcons.Views.Fragment;
    }

    @Nullable
    @Override
    public String getPresentableText() {
      return Joiner.on(" ").skipNulls().join("Fragment", myElement.getFragmentName().getRawText());
    }

    @Override
    public String toString() {
      return getPresentableText();
    }
  }

  /**
   * Tree node corresponding to &lt;include&gt; tag
   */
  private static class IncludeNode extends PsiTreeElementBase<XmlTag> {
    private final Include myElement;

    private IncludeNode(@NotNull Include element) {
      super(element.getXmlTag());
      myElement = element;
    }

    @NotNull
    @Override
    public Collection<StructureViewTreeElement> getChildrenBase() {
      return Collections.emptyList();
    }

    @Nullable
    @Override
    public String getPresentableText() {
      String result = "Include";
      final String text = myElement.getLayout().getRawText();
      if (text != null) {
        result += " " + text;
      }
      return result;
    }

    @Override
    public Icon getIcon(boolean open) {
      return AndroidIcons.Views.Include;
    }

    @Override
    public String toString() {
      return getPresentableText();
    }
  }

  /**
   * Tree node corresponding to view tag
   */
  private static class LayoutNode extends PsiTreeElementBase<XmlTag> {
    private final LayoutViewElement myElement;

    public LayoutNode(@NotNull LayoutViewElement element) {
      super(element.getXmlTag());
      myElement = element;
    }

    @NotNull
    @Override
    public Collection<StructureViewTreeElement> getChildrenBase() {
      final List<StructureViewTreeElement> result = Lists.newArrayList();
      final DomElementVisitor visitor = new DomElementVisitor() {
        public void visitLayoutViewElement(LayoutViewElement element) {
          result.add(new LayoutNode(element));
        }

        public void visitFragment(Fragment element) {
          result.add(new FragmentNode(element));
        }

        public void visitInclude(Include element) {
          result.add(new IncludeNode(element));
        }

        @Override
        public void visitDomElement(DomElement element) {
        }
      };
      StructureUtils.acceptChildrenInOrder(myElement, visitor);
      return result;
    }

    @Override
    public Icon getIcon(boolean open) {
      return AndroidDomElementDescriptorProvider.getIconForViewTag(myElement.getXmlTag().getName());
    }

    @Override
    public String getLocationString() {
      final XmlTag xmlTag = myElement.getXmlTag();
      final XmlAttribute idAttribute = xmlTag.getAttribute("id", SdkConstants.NS_RESOURCES);
      return idAttribute == null ? null : idAttribute.getValue();
    }

    @Override
    public boolean isSearchInLocationString() {
      return true;
    }

    @NotNull
    @Override
    public String getPresentableText() {
      final XmlTag xmlTag = myElement.getXmlTag();
      final String className = xmlTag.getName();
      return className.substring(className.lastIndexOf('.') + 1);
    }

    @Override
    public String toString() {
      final StringBuilder builder = new StringBuilder();
      dumpIndented(builder, 0);
      return builder.toString();
    }

    /**
     * Helper method to build a tree-shaped text representation of structure
     * Used in {@link #toString()} for debugging and unit testing
     */
    private void dumpIndented(@NotNull final StringBuilder builder, final int indentLevel) {
      for (int i = 0; i < indentLevel; i++) {
        builder.append("  ");
      }
      builder.append(getPresentableText());
      final String locationString = getLocationString();
      if (locationString != null) {
        builder.append(" (").append(locationString).append(")");
      }
      builder.append('\n');

      for (StructureViewTreeElement element : getChildrenBase()) {
        if (element instanceof LayoutNode) {
          ((LayoutNode)element).dumpIndented(builder, indentLevel + 1);
        }
        else {
          for (int i = 0; i < (indentLevel + 1); i++) {
            builder.append("  ");
          }
          builder.append(element.toString()).append("\n");
        }
      }
    }
  }
}
