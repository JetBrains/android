/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res.aar;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceItemWithVisibility;
import com.android.ide.common.resources.ResourceVisitor;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.tools.idea.res.ResourceHelper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Common superclass for {@link AarSourceResourceRepository} and {@link AarProtoResourceRepository}.
 */
public abstract class AbstractAarResourceRepository extends AbstractResourceRepository implements AarResourceRepository {
  @NotNull protected final ResourceNamespace myNamespace;
  @NotNull protected final Map<ResourceType, ListMultimap<String, ResourceItem>> myResources = new EnumMap<>(ResourceType.class);
  @NotNull private final Map<ResourceType, Set<ResourceItem>> myPublicResources = new EnumMap<>(ResourceType.class);
  @Nullable protected final String myLibraryName;

  protected AbstractAarResourceRepository(@NotNull ResourceNamespace namespace, @Nullable String libraryName) {
    myNamespace = namespace;
    myLibraryName = libraryName;
  }

  @Override
  @NotNull
  protected ListMultimap<String, ResourceItem> getResourcesInternal(
    @NotNull ResourceNamespace namespace, @NotNull ResourceType resourceType) {
    if (!namespace.equals(myNamespace)) {
      return ImmutableListMultimap.of();
    }
    return myResources.getOrDefault(resourceType, ImmutableListMultimap.of());
  }

  @NotNull
  protected ListMultimap<String, ResourceItem> getOrCreateMap(@NotNull ResourceType resourceType) {
    return myResources.computeIfAbsent(resourceType, type -> ArrayListMultimap.create());
  }

  /**
   * Populates the {@link #myPublicResources} map. Has to be called after {@link #myResources} has been populated.
   */
  protected void populatePublicResourcesMap() {
    for (Map.Entry<ResourceType, ListMultimap<String, ResourceItem>> entry : myResources.entrySet()) {
      ResourceType resourceType = entry.getKey();
      ImmutableSet.Builder<ResourceItem> setBuilder = null;
      ListMultimap<String, ResourceItem> items = entry.getValue();
      for (ResourceItem item : items.values()) {
        if (((ResourceItemWithVisibility)item).getVisibility() == ResourceVisibility.PUBLIC) {
          if (setBuilder == null) {
            setBuilder = ImmutableSet.builder();
          }
          setBuilder.add(item);
        }
      }
      myPublicResources.put(resourceType, setBuilder == null ? ImmutableSet.of() : setBuilder.build());
    }
  }

  @Override
  public void accept(@NonNull ResourceVisitor visitor) {
    if (visitor.shouldVisitNamespace(myNamespace)) {
      acceptByResources(myResources, visitor);
    }
  }

  @Override
  @NotNull
  public Collection<ResourceItem> getPublicResources(@NotNull ResourceNamespace namespace, @NotNull ResourceType type) {
    if (!namespace.equals(myNamespace)) {
      return Collections.emptySet();
    }
    Set<ResourceItem> resourceItems = myPublicResources.get(type);
    return resourceItems == null ? Collections.emptySet() : resourceItems;
  }

  @Override
  @NotNull
  public final ResourceNamespace getNamespace() {
    return myNamespace;
  }

  @Override
  @Nullable
  public final String getLibraryName() {
    return myLibraryName;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return myLibraryName == null ? "Android Framework" : myLibraryName;
  }

  /**
   * Produces a string to be returned by the {@link AarFileResourceItem#getValue()} method.
   * The string represents an URL in one of the following formats:
   * <ul>
   *  <li>file URL, e.g. "file:///foo/bar/res/layout/my_layout.xml"</li>
   *  <li>URL of a zipped element inside the res.apk file, e.g. "apk:///foo/bar/res.apk!/res/layout/my_layout.xml"</li>
   * </ul>
   *
   * @param relativeResourcePath the relative path of a file resource
   * @return the URL pointing to the file resource
   * @see ResourceHelper#toFileResourcePathString(String)
   */
  @NotNull
  abstract String getResourceUrl(@NotNull String relativeResourcePath);

  /**
   * Produces a {@link PathString} to be returned by the {@link AarFileResourceItem#getSource()} method.
   *
   * @param relativeResourcePath the relative path of a file resource
   * @return the PathString pointing to the file resource
   */
  @NotNull
  abstract PathString getPathString(@NotNull String relativeResourcePath);

  /**
   * Returns the file or directory this resource repository was loaded from. Resource repositories loaded from
   * the same file or directory with different file filtering options have the same origin.
   */
  @NotNull
  abstract Path getOrigin();

  /**
   * Parser of resource URLs. Unlike {@link com.android.resources.ResourceUrl}, this class is resilient to URL syntax
   * errors doesn't create any GC overhead.
   */
  protected final static class ResourceUrlParser {
    @NotNull private String resourceUrl = "";
    private int prefixEnd;
    private int colonPos;
    private int slashPos;
    private int typeStart;
    private int namespacePrefixStart;
    private int nameStart;

    /**
     * Parses resource URL and sets the fields of this object to point to different parts of the URL.
     *
     * @param resourceUrl the resource URL to parse
     */
    public void parseResourceUrl(@NotNull String resourceUrl) {
      this.resourceUrl = resourceUrl;
      colonPos = -1;
      slashPos = -1;
      typeStart = -1;
      namespacePrefixStart = -1;

      if (resourceUrl.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
        if (resourceUrl.startsWith("@+")) {
          prefixEnd = 2;
        } else {
          prefixEnd = 1;
        }
      } else if (resourceUrl.startsWith(SdkConstants.PREFIX_THEME_REF)) {
        prefixEnd = 1;
      } else {
        prefixEnd = 0;
      }
      if (resourceUrl.startsWith("*", prefixEnd)) {
        prefixEnd++;
      }

      int len = resourceUrl.length();
      int start = prefixEnd;
      loop: for (int i = prefixEnd; i < len; i++) {
        char c = resourceUrl.charAt(i);
        switch (c) {
          case '/':
            if (slashPos < 0) {
              slashPos = i;
              typeStart = start;
              start = i + 1;
              if (colonPos >= 0) {
                break loop;
              }
            }
            break;

          case ':':
            if (colonPos < 0) {
              colonPos = i;
              namespacePrefixStart = start;
              start = i + 1;
              if (slashPos >= 0) {
                break loop;
              }
            }
            break;
        }
      }
      nameStart = start;
    }

    /**
     * Returns the namespace prefix of the resource URL, or null if the URL doesn't contain a prefix.
     */
    @Nullable
    public String getNamespacePrefix() {
      return colonPos >= 0 ? resourceUrl.substring(namespacePrefixStart, colonPos) : null;
    }

    /**
     * Returns the type of the resource URL, or null if the URL don't contain a type.
     */
    @Nullable
    public String getType() {
      return slashPos >= 0 ? resourceUrl.substring(typeStart, slashPos) : null;
    }

    /**
     * Returns the name part of the resource URL.
     */
    @NotNull
    public String getName() {
      return resourceUrl.substring(nameStart);
    }

    /**
     * Returns the qualified name of the resource without any prefix or type.
     */
    @NotNull
    public String getQualifiedName() {
      if (colonPos < 0) {
        return getName();
      }
      if (nameStart == colonPos + 1) {
        return resourceUrl.substring(namespacePrefixStart);
      }
      return resourceUrl.substring(namespacePrefixStart, colonPos + 1) + getName();
    }

    /**
     * Checks if the resource URL has the given type.
     */
    public boolean hasType(@NotNull String type) {
      if (slashPos < 0) {
        return false;
      }
      return slashPos == typeStart + type.length() && resourceUrl.startsWith(type, typeStart);
    }

    /**
     * Checks if the resource URL has the given namespace prefix.
     */
    public boolean hasNamespacePrefix(@NotNull String namespacePrefix) {
      if (colonPos < 0) {
        return false;
      }
      return colonPos == namespacePrefixStart + namespacePrefix.length() && resourceUrl.startsWith(namespacePrefix, namespacePrefixStart);
    }
  }
}
