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
package com.android.tools.idea.resources.aar;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceItemWithVisibility;
import com.android.ide.common.resources.ResourceVisitor;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  protected final ListMultimap<String, ResourceItem> getResourcesInternal(
    @NotNull ResourceNamespace namespace, @NotNull ResourceType resourceType) {
    if (!namespace.equals(myNamespace)) {
      return ImmutableListMultimap.of();
    }
    return myResources.getOrDefault(resourceType, ImmutableListMultimap.of());
  }

  @NotNull
  protected final ListMultimap<String, ResourceItem> getOrCreateMap(@NotNull ResourceType resourceType) {
    return myResources.computeIfAbsent(resourceType, type -> ArrayListMultimap.create());
  }

  protected final void addResourceItem(@NotNull ResourceItem item) {
    ListMultimap<String, ResourceItem> multimap = getOrCreateMap(item.getType());
    multimap.put(item.getName(), item);
  }

  /**
   * Populates the {@link #myPublicResources} map. Has to be called after {@link #myResources} has been populated.
   */
  protected final void populatePublicResourcesMap() {
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

  /**
   * Returns a styleable with attr references replaced by attr definitions returned by the {@link #getCanonicalAttr(AttrResourceValue)}
   * method.
   */
  @NotNull
  static AarStyleableResourceItem resolveAttrReferences(@NotNull AarStyleableResourceItem styleable) {
    AbstractAarResourceRepository repository = styleable.getRepository();
    List<AttrResourceValue> attributes = styleable.getAllAttributes();
    List<AttrResourceValue> resolvedAttributes = null;
    for (int i = 0; i < attributes.size(); i++) {
      AttrResourceValue attr = attributes.get(i);
      AttrResourceValue canonicalAttr = repository.getCanonicalAttr(attr);
      if (canonicalAttr != attr) {
        if (resolvedAttributes == null) {
          resolvedAttributes = new ArrayList<>(attributes.size());
          for (int j = 0; j < i; j++) {
            resolvedAttributes.add(attributes.get(j));
          }
        }
        resolvedAttributes.add(canonicalAttr);
      }
      else if (resolvedAttributes != null) {
        resolvedAttributes.add(attr);
      }
    }

    if (resolvedAttributes != null) {
      ResourceNamespace.Resolver namespaceResolver = styleable.getNamespaceResolver();
      styleable =
          new AarStyleableResourceItem(styleable.getName(), styleable.getSourceFile(), styleable.getVisibility(), resolvedAttributes);
      styleable.setNamespaceResolver(namespaceResolver);
    }
    return styleable;
  }

  /**
   * Makes resource maps immutable.
   */
  protected void freezeResources() {
    for (Map.Entry<ResourceType, ListMultimap<String, ResourceItem>> entry : myResources.entrySet()) {
      myResources.put(entry.getKey(), ImmutableListMultimap.copyOf(entry.getValue()));
    }
  }

  @Override
  public void accept(@NotNull ResourceVisitor visitor) {
    if (visitor.shouldVisitNamespace(myNamespace)) {
      AbstractResourceRepository.acceptByResources(myResources, visitor);
    }
  }

  @Override
  @NotNull
  public List<ResourceItem> getResources(@NotNull ResourceNamespace namespace, @NotNull ResourceType resourceType,
                                         @NotNull String resourceName) {
    ListMultimap<String, ResourceItem> map = getResourcesInternal(namespace, resourceType);
    List<ResourceItem> items = map.get(resourceName);
    return items == null ? ImmutableList.of() : items;
  }

  @Override
  @NotNull
  public ListMultimap<String, ResourceItem> getResources(@NotNull ResourceNamespace namespace, @NotNull ResourceType resourceType) {
    return getResourcesInternal(namespace, resourceType);
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
   */
  @NotNull
  abstract String getResourceUrl(@NotNull String relativeResourcePath);

  /**
   * Produces a {@link PathString} to be returned by the {@link AarResourceItem#getSource()} method.
   *
   * @param relativeResourcePath the relative path of the file the resource was created from
   * @param forFileResource true is the resource is a file resource, false if it is a value resource
   * @return the PathString to be returned by the {@link AarResourceItem#getSource()} method
   */
  @NotNull
  abstract PathString getSourceFile(@NotNull String relativeResourcePath, boolean forFileResource);

  /**
   * Produces a {@link PathString} to be returned by the {@link AarResourceItem#getOriginalSource()} method.
   *
   * @param relativeResourcePath the relative path of the file the resource was created from
   * @param forFileResource true is the resource is a file resource, false if it is a value resource
   * @return the PathString to be returned by the {@link AarResourceItem#getOriginalSource()} method
   */
  @Nullable
  PathString getOriginalSourceFile(@NotNull String relativeResourcePath, boolean forFileResource) {
    return getSourceFile(relativeResourcePath, forFileResource);
  }

  /**
   * Returns the file or directory this resource repository was loaded from. Resource repositories loaded from
   * the same file or directory with different file filtering options have the same origin.
   */
  @NotNull
  abstract Path getOrigin();

  /**
   * For an attr reference that doesn't contain formats tries to find an attr definition the reference is pointing to.
   * If such attr definition belongs to this resource repository and has the same description and and group name as
   * the attr reference, returns the attr definition. Otherwise returns the attr reference passed as the parameter.
   */
  @NotNull
  AttrResourceValue getCanonicalAttr(@NotNull AttrResourceValue attr) {
    if (attr.getFormats().isEmpty()) {
      List<ResourceItem> items = getResources(attr.getNamespace(), ResourceType.ATTR, attr.getName());
      for (ResourceItem item : items) {
        if (item instanceof AttrResourceValue &&
            Objects.equals(((AttrResourceValue)item).getDescription(), attr.getDescription()) &&
            Objects.equals(((AttrResourceValue)item).getGroupName(), attr.getGroupName())) {
          return (AttrResourceValue)item;
        }
      }
    }
    return attr;
  }

  @NotNull
  protected static String portableFileName(@NotNull String fileName) {
    return fileName.replace(File.separatorChar, '/');
  }

  /**
   * Parser of resource URLs. Unlike {@link com.android.resources.ResourceUrl}, this class is resilient to URL syntax
   * errors doesn't create any GC overhead.
   */
  protected final static class ResourceUrlParser {
    @NotNull private String resourceUrl = "";
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

      int prefixEnd;
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
