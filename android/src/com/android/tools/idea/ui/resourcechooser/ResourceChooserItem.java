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
package com.android.tools.idea.ui.resourcechooser;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.SdkConstants.SAMPLE_PREFIX;
import static com.android.SdkConstants.TOOLS_SAMPLE_PREFIX;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.ide.common.rendering.api.SampleDataResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.PredefinedSampleDataResourceRepository;
import com.android.tools.idea.res.SampleDataResourceItem;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ResourceChooserItem {
  public static final String DEFAULT_FOLDER_NAME = "Default";

  @NotNull protected final ResourceType myType;
  @NotNull protected final String myName;
  private final HashMap<Integer, Icon> myIcons = Maps.newHashMapWithExpectedSize(2);

  public ResourceChooserItem(@NotNull ResourceType type, @NotNull String name) {
    myType = type;
    myName = name;
  }

  @NotNull
  public List<String> getQualifiers() {
    return Collections.singletonList(DEFAULT_FOLDER_NAME);
  }

  @NotNull
  public ResourceType getType() {
    return myType;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getQualifiedName() {
    return isFramework() ? ANDROID_NS_NAME_PREFIX + getName() : getName();
  }

  @Nullable
  public PathString getFile() {
    return null;
  }

  @Nullable
  public String getPath() {
    return null;
  }

  @Nullable
  public String getFileForQualifiers(String qualifiers) {
    return null;
  }

  @NotNull
  public abstract String getResourceUrl();

  @NotNull
  public ResourceValue getResourceValue() {
    return new ResourceValueImpl(ResourceNamespace.fromBoolean(isFramework()), getType(), getName(), getResourceUrl());
  }

  @Override
  public String toString() {
    // We need to return JUST the name so quick-search in the list works
    return getName();
  }

  @Nullable
  public Icon getIcon(int iconSize) {
    return myIcons.get(iconSize);
  }

  public void setIcon(@NotNull Icon icon) {
    assert icon.getIconWidth() == icon.getIconHeight();
    myIcons.put(icon.getIconWidth(), icon);
  }

  public abstract boolean isFramework();

  public boolean isAttr() {
    return false;
  }

  public boolean isReference() {
    String s = getDefaultValue();
    if (s != null) {
      return s.startsWith(PREFIX_RESOURCE_REF) || s.startsWith(PREFIX_THEME_REF);
    }

    return false;
  }

  @Nullable
  public abstract String getDefaultValue();

  @NotNull
  public List<Pair<FolderConfiguration, String>> getQualifiersAndValues() {
    return Collections.emptyList();
  }

  public static ResourceChooserItem createProjectItem(@NotNull ResourceType type,
                                                      @NotNull String name,
                                                      @NotNull List<ResourceItem> resourceItems) {
    return new ResourceChooserItemImpl(type, name, resourceItems, false);
  }

  public static ResourceChooserItem createFrameworkItem(@NotNull ResourceType type,
                                                        @NotNull String name,
                                                        @NotNull List<ResourceItem> resourceItems) {
    return new ResourceChooserItemImpl(type, name, resourceItems, true);
  }

  private static class ResourceChooserItemImpl extends ResourceChooserItem {
    @NotNull private final List<ResourceItem> myResourceItems;
    private final boolean myIsFramework;

    public ResourceChooserItemImpl(@NotNull ResourceType type,
                                   @NotNull String name,
                                   @NotNull List<ResourceItem> resourceItems,
                                   boolean isFramework) {
      super(type, name);
      myResourceItems = resourceItems;
      myIsFramework = isFramework;
    }

    @Override
    public boolean isFramework() {
      return myIsFramework;
    }

    @Override
    @Nullable
    public PathString getFile() {
      return !myResourceItems.isEmpty() ? myResourceItems.get(0).getSource() : null;
    }

    @Override
    @Nullable
    public String getPath() {
      PathString file = getFile();
      return file != null ? file.getNativePath() : null;
    }

    @Override
    @Nullable
    public String getFileForQualifiers(String qualifiers) {
      for (ResourceItem item : myResourceItems) {
        if (qualifiers.equals(item.getConfiguration().getQualifierString())) {
          PathString file = item.getSource();
          if (file != null) {
            return file.getNativePath();
          }
        }
      }

      return null;
    }

    @NotNull
    @Override
    public String getResourceUrl() {
      StringBuilder builder = new StringBuilder(PREFIX_RESOURCE_REF);
      if (isFramework()) {
        builder.append(ANDROID_NS_NAME_PREFIX);
      }
      return builder.append(myType.getName())
                    .append('/')
                    .append(myName)
                    .toString();
    }

    @Override
    @NotNull
    public List<String> getQualifiers() {
      Set<String> set = Sets.newHashSet();
      for (ResourceItem item : myResourceItems) {
        String q = item.getConfiguration().getQualifierString();
        if (!q.isEmpty()) {
          set.add(q);
        }
        else {
          set.add(DEFAULT_FOLDER_NAME);
        }
      }
      List<String> qualifiers = new ArrayList<>(set);
      Collections.sort(qualifiers);
      return qualifiers;
    }

    @Override
    public String getDefaultValue() {
      ResourceItem first = myResourceItems.get(0);
      ResourceValue value = first.getResourceValue();
      if (value != null) {
        return value.getValue();
      }

      return null;
    }

    @Override
    @NotNull
    public List<Pair<FolderConfiguration, String>> getQualifiersAndValues() {
      ArrayList<Pair<FolderConfiguration, String>> pairs = new ArrayList<>(myResourceItems.size());
      for (ResourceItem item : myResourceItems) {
        ResourceValue resourceValue = item.getResourceValue();
        FolderConfiguration configuration = item.getConfiguration();
        pairs.add(Pair.create(configuration, resourceValue != null ? resourceValue.getValue() : null));
      }
      return pairs;
    }
  }

  public static class AttrItem extends ResourceChooserItem {
    private boolean myFramework;

    public AttrItem(@NotNull ResourceType type, boolean framework, @NotNull String name) {
      super(type, name);
      myFramework = framework;
    }

    @Override
    public boolean isAttr() {
      return true;
    }

    @Override
    public boolean isFramework() {
      return myFramework;
    }

    @Override
    @NotNull
    public String getResourceUrl() {
      if (isFramework()) {
        return PREFIX_THEME_REF + ANDROID_NS_NAME_PREFIX + ResourceType.ATTR.getName() + '/' + myName;
      }
      else {
        return PREFIX_THEME_REF + ResourceType.ATTR.getName() + '/' + myName;
      }
    }

    @Override
    public String toString() {
      // since all attrs are in a single section, we show the full name
      return getQualifiedName();
    }

    @Override
    public boolean isReference() {
      return true;
    }

    @Override
    public String getDefaultValue() {
      // TODO: Look this up?
      return null;
    }
  }

  public static class SampleDataItem extends ResourceChooserItem {
    @NotNull private final SampleDataResourceItem myItem;
    private int myIndex = -1;

    public SampleDataItem(@NotNull SampleDataResourceItem item) {
      super(ResourceType.SAMPLE_DATA, item.getName());
      myItem = item;
    }

    @NotNull
    public SampleDataResourceItem getResourceItem() {
      return myItem;
    }

    @NotNull
    @Override
    public String getResourceUrl() {
      boolean isPredefinedData = PredefinedSampleDataResourceRepository.NAMESPACE.equals(myItem.getResourceValue().getNamespace());
      String prefix = isPredefinedData ? TOOLS_SAMPLE_PREFIX : SAMPLE_PREFIX;
      return prefix + myName + ((myIndex >= 0) ? "[" + myIndex + "]" : "");
    }

    @Override
    public boolean isFramework() {
      return false;
    }

    @Nullable
    @Override
    public String getDefaultValue() {
      return Iterables.getFirst(getSampleDataResourceValue().getValueAsLines(), getResourceUrl());
    }

    @NotNull
    private SampleDataResourceValue getSampleDataResourceValue() {
      SampleDataResourceValue value = (SampleDataResourceValue)myItem.getResourceValue();
      return value;
    }

    /**
     * Set the selected value index to specify the. If index < 0,
     * {@link #getResourceUrl()} will return the default url (values of this item will be used
     * sequentially), otherwise the index of the value will be added to the url.
     *
     * @throws IndexOutOfBoundsException if index is greater than the number of values in this item.
     */
    public void setValueIndex(int index) {
      if (index >= getSampleDataResourceValue().getValueAsLines().size()) {
        throw new IndexOutOfBoundsException();
      }
      myIndex = index;
    }

    @NotNull
    @Override
    public List<Pair<FolderConfiguration, String>> getQualifiersAndValues() {
      FolderConfiguration folderConfiguration = new FolderConfiguration();
      return getSampleDataResourceValue().getValueAsLines().stream()
                                         .limit(10)
                                         .map((string) -> Pair.create(folderConfiguration, string))
                                         .collect(Collectors.toList());
    }

    @NotNull
    @Override
    public ResourceValue getResourceValue() {
      ResourceType type = myItem.getContentType() == SampleDataResourceItem.ContentType.IMAGE ?
                          ResourceType.DRAWABLE :
                          myItem.getType();
      SampleDataResourceValue value = getSampleDataResourceValue();
      return new ResourceValueImpl(myItem.getNamespace(), type, myItem.getName(), value.getValueAsLines().get(0), value.getLibraryName());
    }
  }
}
