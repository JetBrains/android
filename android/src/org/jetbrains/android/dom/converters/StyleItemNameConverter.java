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
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.AppResourceRepository;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.resourceManagers.SystemResourceManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class StyleItemNameConverter extends ResolvingConverter<String> {
  /**
   * Finds the parent name from the passed style {@link XmlTag}. The parent name might be in the parent attribute or it can be part of the
   * style name.
   */
  @Nullable/*if the tag is null or doesn't define a parent*/
  private static String getParentNameFromTag(@Nullable XmlTag styleTag) {
    if (styleTag == null) {
      return null;
    }

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
    } else if (parentName.startsWith(SdkConstants.STYLE_RESOURCE_PREFIX)) {
      parentName = StringUtil.trimStart(parentName, SdkConstants.STYLE_RESOURCE_PREFIX);
    } else if (parentName.startsWith(SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX)){
      parentName = SdkConstants.PREFIX_ANDROID + StringUtil.trimStart(parentName, SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX);
    }

    return parentName;
  }

  @NotNull
  @Override
  public Collection<String> getVariants(ConvertContext context) {
    List<String> result = Lists.newArrayList();

    if (context.getModule() != null && context.getTag() != null) {
      // Try to find the parents of the styles where this item is defined and add to the suggestion every non-framework attribute that has been used.
      // This is helpful in themes like AppCompat where there is not only a framework attribute defined but also a custom attribute. This
      // will show both in the completion list.
      AppResourceRepository appResourceRepository = AppResourceRepository.getOrCreateInstance(context.getModule());
      XmlTag styleTag = context.getTag().getParentTag();
      String parent = getParentNameFromTag(styleTag);
      List<ResourceItem> parentDefinitions =
        parent != null && appResourceRepository != null ? appResourceRepository.getResourceItem(ResourceType.STYLE, parent) : null;

      if (parentDefinitions != null && !parentDefinitions.isEmpty()) {
        HashSet<String> attributeNames = Sets.newHashSet();
        LinkedList<ResourceItem> toExplore = Lists.newLinkedList(parentDefinitions);
        int i = 0;
        while (!toExplore.isEmpty() && i++ < ResourceResolver.MAX_RESOURCE_INDIRECTION) {
          ResourceItem parentItem = toExplore.pop();
          StyleResourceValue parentValue = (StyleResourceValue)parentItem.getResourceValue(false);
          if (parentValue == null || parentValue.isFramework()) {
            // No parent or the parent is a framework style
            continue;
          }

          for (ItemResourceValue value : parentValue.getValues()) {
            if (!value.isFramework()) {
              attributeNames.add(value.getName());
            }
          }

          List<ResourceItem> parents = appResourceRepository.getResourceItem(ResourceType.STYLE, parentValue.getParentStyle());
          if (parents != null) {
            toExplore.addAll(parents);
          }
        }

        result.addAll(attributeNames);
      }
    }

    ResourceManager manager = SystemResourceManager.getInstance(context);
    if (manager != null) {
      AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
      if (attrDefs != null) {
        for (String name : attrDefs.getAttributeNames()) {
          result.add(SdkConstants.PREFIX_ANDROID + name);
        }
      }
    }

    return result;
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
    if (s == null) return null;
    String[] strs = s.split(":");
    if (strs.length < 2 || !"android".equals(strs[0])) {
      return s;
    }
    if (strs.length == 2) {
      ResourceManager manager = SystemResourceManager.getInstance(context);
      if (manager != null) {
        AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
        if (attrDefs != null && attrDefs.getAttrDefByName(strs[1]) != null) {
          return s;
        }
      }
    }
    return null;
  }

  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }
}
