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
package com.android.tools.idea.editors.theme;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.editors.theme.attributes.editors.AttributeReferenceRendererEditor;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.google.common.collect.ImmutableList;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class ResourcesCompletionProvider implements AttributeReferenceRendererEditor.CompletionProvider, ThemeEditorContext.ChangeListener {
  private final ArrayList<ResourceValue> myAllResources = new ArrayList<ResourceValue>();

  ResourcesCompletionProvider(@NotNull ThemeEditorContext themeEditorContext) {
    fillResources(themeEditorContext.getResourceResolver());
  }

  @NotNull
  @Override
  public List<String> getCompletions(@NotNull EditedStyleItem value) {
    ConfiguredThemeEditorStyle selectedStyle = value.getSourceStyle();

    AttributeDefinition attrDefinition =
      ResolutionUtils.getAttributeDefinition(selectedStyle.getConfiguration(), value.getSelectedValue());
    if (attrDefinition == null) {
      return Collections.emptyList();
    }

    Set<ResourceType> acceptedTypes = EnumSet.noneOf(ResourceType.class);
    if (ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.Color)) {
      acceptedTypes.add(ResourceType.COLOR);
    }
    if (ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.Dimension)) {
      acceptedTypes.add(ResourceType.DIMEN);
    }
    if (ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.String)) {
      acceptedTypes.add(ResourceType.STRING);
    }
    if (ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.Reference)) {
      acceptedTypes.addAll(ImmutableList
                             .of(ResourceType.LAYOUT, ResourceType.COLOR, ResourceType.DRAWABLE, ResourceType.MIPMAP, ResourceType.STYLE,
                                 ResourceType.ATTR, ResourceType.STRING, ResourceType.DIMEN, ResourceType.TRANSITION));
    }

    ArrayList<String> resourceNamesList = new ArrayList<String>(myAllResources.size());
    for (ResourceValue resource : myAllResources) {
      if (!acceptedTypes.contains(resource.getResourceType())) {
        continue;
      }

      final String name = String.format("%1$s%2$s%3$s/%4$s", ResourceType.ATTR == resource.getResourceType()
                                                             ? SdkConstants.PREFIX_THEME_REF
                                                             : SdkConstants.PREFIX_RESOURCE_REF,
                                        resource.isFramework() ? SdkConstants.ANDROID_NS_NAME_PREFIX : "",
                                        resource.getResourceType().getName(), resource.getName());

      resourceNamesList.add(name);
    }

    return resourceNamesList;
  }

  private void fillResources(@Nullable ResourceResolver resourceResolver) {
    myAllResources.clear();

    if (resourceResolver == null) {
      return;
    }

    for (Map<String, ResourceValue> resourceTypeResource : resourceResolver.getFrameworkResources().values()) {
      myAllResources.addAll(resourceTypeResource.values());
    }
    for (Map<String, ResourceValue> resourceTypeResource : resourceResolver.getProjectResources().values()) {
      myAllResources.addAll(resourceTypeResource.values());
    }
  }

  @Override
  public void onNewConfiguration(ThemeEditorContext context) {
    fillResources(context.getResourceResolver());
  }
}
