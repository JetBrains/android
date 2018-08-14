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

package org.jetbrains.android.dom.converters;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.res.ResourceNamespaceContext;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.resourceManagers.FrameworkResourceManager;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.ide.common.resources.AbstractResourceRepository.MAX_RESOURCE_INDIRECTION;
import static com.intellij.openapi.util.Pair.pair;

public class StyleItemNameConverter extends ResolvingConverter<String> {

  @NotNull
  @Override
  public Collection<String> getVariants(ConvertContext context) {
    List<String> result = Lists.newArrayList();

    if (context.getModule() != null && context.getTag() != null) {
      XmlTag styleTag = context.getTag().getParentTag();
      if (styleTag != null) {
        result.addAll(getAttributesUsedByParentStyle(styleTag));
      }
    }

    ResourceManager manager = FrameworkResourceManager.getInstance(context);
    if (manager != null) {
      AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
      if (attrDefs != null) {
        for (ResourceReference attr : attrDefs.getAttrs()) {
          result.add(attr.getQualifiedName());
        }
      }
    }

    return result;
  }

  /**
   * Try to find the parents of the styles where this item is defined and add to the suggestion every non-framework attribute that has been
   * used. This is helpful in themes like AppCompat where there is not only a framework attribute defined but also a custom attribute. This
   * will show both in the completion list.
   */
  @NotNull
  private static Collection<String> getAttributesUsedByParentStyle(@NotNull XmlTag styleTag) {
    Module module = ModuleUtilCore.findModuleForPsiElement(styleTag);
    if (module == null) {
      return Collections.emptyList();
    }

    LocalResourceRepository appResources = ResourceRepositoryManager.getAppResources(module);
    if (appResources == null) {
      return Collections.emptyList();
    }

    ResourceReference parentStyleReference = getParentStyleFromTag(styleTag);
    if (parentStyleReference == null) {
      return Collections.emptyList();
    }
    List<ResourceItem> parentStyles = appResources.getResources(parentStyleReference);

    ResourceNamespaceContext namespacesContext = ResourceHelper.getNamespacesContext(styleTag);
    if (namespacesContext == null) {
      return Collections.emptyList();
    }

    ResourceNamespace namespace = namespacesContext.getCurrentNs();
    ResourceNamespace.Resolver resolver = namespacesContext.getResolver();
    HashSet<String> attributeNames = new HashSet<>();

    ArrayDeque<Pair<ResourceItem, Integer>> toExplore = new ArrayDeque<>();
    for (ResourceItem parentStyle : parentStyles) {
      toExplore.push(pair(parentStyle, 0));
    }

    while (!toExplore.isEmpty()) {
      Pair<ResourceItem, Integer> top = toExplore.pop();
      int depth = top.second;
      if (depth > MAX_RESOURCE_INDIRECTION) {
        continue; // This branch in the parent graph is too deep.
      }

      ResourceItem parentItem = top.first;
      StyleResourceValue parentValue = (StyleResourceValue)parentItem.getResourceValue();
      if (parentValue == null || parentValue.isFramework()) {
        // No parent or the parent is a framework style
        continue;
      }

      for (StyleItemResourceValue value : parentValue.getDefinedItems()) {
        if (!value.isFramework()) {
          ResourceReference attr = value.getAttr();
          if (attr != null) {
            attributeNames.add(attr.getRelativeResourceUrl(namespace, resolver).getQualifiedName());
          }
        }
      }

      parentStyleReference = parentValue.getParentStyle();
      if (parentStyleReference != null) {
        for (ResourceItem parentStyle : appResources.getResources(parentStyleReference)) {
          toExplore.add(pair(parentStyle, depth + 1));
        }
      }
    }
    return attributeNames;
  }

  /**
   * Finds the parent style from the passed style {@link XmlTag}. The parent name might be in the parent attribute or it can be part of the
   * style name. Returns null if it cannot determine the parent style.
   */
  @Nullable
  private static ResourceReference getParentStyleFromTag(@NotNull XmlTag styleTag) {
    String parentName = styleTag.getAttributeValue(SdkConstants.ATTR_PARENT);
    if (parentName == null) {
      String styleName = styleTag.getAttributeValue(SdkConstants.ATTR_NAME);
      if (styleName == null) {
        return null;
      }

      int lastDot = styleName.lastIndexOf('.');
      if (lastDot == -1) {
        return null;
      }

      parentName = styleName.substring(0, lastDot);
    }

    ResourceUrl parentUrl = ResourceUrl.parseStyleParentReference(parentName);
    if (parentUrl == null) {
      return null;
    }

    return ResourceHelper.resolve(parentUrl, styleTag);
  }

  @Override
  public LookupElement createLookupElement(String s) {
    if (s == null) {
      return null;
    }

    // Prioritize non framework attributes at the top
    return PrioritizedLookupElement.withPriority(LookupElementBuilder.create(s), s.startsWith(SdkConstants.PREFIX_ANDROID) ? 0 : 1);
  }

  @Override
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null) {
      return null;
    }

    XmlElement xmlElement = context.getXmlElement();
    if (xmlElement == null) {
      return null;
    }

    ResourceUrl attrUrl = ResourceUrl.parseAttrReference(s);
    if (attrUrl == null) {
      return null;
    }

    ResourceReference attributeReference = ResourceHelper.resolve(attrUrl, xmlElement);
    if (attributeReference == null) {
      return null;
    }

    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(xmlElement);
    if (repositoryManager == null) {
      return null;
    }

    AbstractResourceRepository repository = attributeReference.getNamespace() == ResourceNamespace.ANDROID ?
                                            repositoryManager.getFrameworkResources(false) :
                                            repositoryManager.getAppResources(true);
    if (repository == null) {
      return null;
    }

    return repository.getResources(attributeReference).isEmpty() ? null : s;
  }

  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }
}
