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
package com.android.tools.idea.editors.theme.datamodels;

import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.ProjectResourceRepository;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static com.android.SdkConstants.PREFIX_ANDROID;

/**
 * This class represents styles in ThemeEditor
 * It knows about style in all FolderConfigurations
 */
public class ThemeEditorStyle {
  @NotNull private final ConfigurationManager myManager;
  @NotNull private final String myQualifiedName;

  public ThemeEditorStyle(@NotNull ConfigurationManager manager, @NotNull String qualifiedName) {
    myManager = manager;
    myQualifiedName = qualifiedName;
  }

  /**
   * Returns the style name. If this is a framework style, it will include the "android:" prefix.
   */
  @NotNull
  public String getQualifiedName() {
    return myQualifiedName;
  }

  /**
   * Returns the style name without namespaces or prefixes.
   */
  @NotNull
  public String getName() {
    return ResolutionUtils.getNameFromQualifiedName(myQualifiedName);
  }

  public boolean isFramework() {
    return myQualifiedName.startsWith(PREFIX_ANDROID);
  }


  public boolean isProjectStyle() {
    if (isFramework()) {
      return false;
    }
    ProjectResourceRepository repository = ProjectResourceRepository.getProjectResources(myManager.getModule(), true);
    assert repository != null;
    return repository.hasResourceItem(ResourceType.STYLE, myQualifiedName);
  }

  /**
   * Returns all the {@link ResourceItem} where this style is defined. This includes all the definitions in the
   * different resource folders.
   */
  @NotNull
  protected Collection<ResourceItem> getStyleResourceItems() {
    assert !isFramework();

    Collection<ResourceItem> resultItems;
    final Module module = myManager.getModule();
    if (isProjectStyle()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null : module.getName() + " module doesn't have AndroidFacet";

      // We need to keep a Set of ResourceItems to override them. The key is the folder configuration + the name
      final HashMap<String, ResourceItem> resourceItems = Maps.newHashMap();
      ThemeEditorUtils.acceptResourceResolverVisitor(facet, new ThemeEditorUtils.ResourceFolderVisitor() {
        @Override
        public void visitResourceFolder(@NotNull LocalResourceRepository resources, String moduleName, @NotNull String variantName, boolean isSourceSelected) {
          if (!isSourceSelected) {
            // Currently we ignore the source sets that are not active
            // TODO: Process all source sets
            return;
          }

          List<ResourceItem> items = resources.getResourceItem(ResourceType.STYLE, myQualifiedName);
          if (items == null) {
            return;
          }

          for (ResourceItem item : items) {
            String key = item.getConfiguration().toShortDisplayString() + "/" + item.getName();
            resourceItems.put(key, item);
          }
        }
      });

      resultItems = ImmutableList.copyOf(resourceItems.values());
    } else {
      LocalResourceRepository resourceRepository = AppResourceRepository.getAppResources(module, true);
      assert resourceRepository != null;
      List<ResourceItem> items = resourceRepository.getResourceItem(ResourceType.STYLE, myQualifiedName);
      if (items != null) {
        resultItems = items;
      } else {
        resultItems = Collections.emptyList();
      }
    }
    return resultItems;
  }

  /**
   * @return Collection of FolderConfiguration where this style is defined
   */
  @NotNull
  public Collection<FolderConfiguration> getFolders() {
    if (isFramework()) {
      return ImmutableList.of(new FolderConfiguration());
    }
    ImmutableList.Builder<FolderConfiguration> result = ImmutableList.builder();
    for (ResourceItem styleItem : getStyleResourceItems()) {
      result.add(styleItem.getConfiguration());
    }
    return result.build();
  }

  /**
   * @param configuration FolderConfiguration of the style to lookup
   * @return all values defined in this style with a FolderConfiguration configuration
   */
  @NotNull
  public Collection<ItemResourceValue> getValues(@NotNull FolderConfiguration configuration) {
    if (isFramework()) {
      IAndroidTarget target = myManager.getHighestApiTarget();
      assert target != null;

      com.android.ide.common.resources.ResourceItem styleItem =
        myManager.getResolverCache().getFrameworkResources(new FolderConfiguration(), target).getResourceItem(ResourceType.STYLE, getName());

      for (ResourceFile file : styleItem.getSourceFileList()) {
        if (file.getConfiguration().equals(configuration)) {
          StyleResourceValue style = (StyleResourceValue)file.getValue(ResourceType.STYLE, getName());
          return style.getValues();
        }
      }
      throw new IllegalArgumentException("bad folder config " + configuration);
    }

    for (final ResourceItem styleItem : getStyleResourceItems()) {
      if (configuration.equals(styleItem.getConfiguration())) {
        StyleResourceValue style = (StyleResourceValue)styleItem.getResourceValue(false);
        return style.getValues();
      }
    }
    throw new IllegalArgumentException("bad folder config " + configuration);
  }

  /**
   * @param configuration FolderConfiguration of the style to lookup
   * @return parent this style with a FolderConfiguration configuration
   */
  @Nullable("if there is no of this style")
  public String getParentName(@NotNull FolderConfiguration configuration) {
    if (isFramework()) {
      IAndroidTarget target = myManager.getHighestApiTarget();
      assert target != null;

      com.android.ide.common.resources.ResourceItem styleItem =
        myManager.getResolverCache().getFrameworkResources(new FolderConfiguration(), target).getResourceItem(ResourceType.STYLE, getName());

      for (ResourceFile file : styleItem.getSourceFileList()) {
        if (file.getConfiguration().equals(configuration)) {
          StyleResourceValue style = (StyleResourceValue)file.getValue(ResourceType.STYLE, getName());
          return ResolutionUtils.getParentQualifiedName(style);
        }
      }
      throw new IllegalArgumentException("bad folder config " + configuration);
    }

    for (final ResourceItem styleItem : getStyleResourceItems()) {
      if (configuration.equals(styleItem.getConfiguration())) {
        StyleResourceValue style = (StyleResourceValue)styleItem.getResourceValue(false);
        assert style != null;
        return ResolutionUtils.getParentQualifiedName(style);
      }
    }
    throw new IllegalArgumentException("bad folder config " + configuration);
  }
}
