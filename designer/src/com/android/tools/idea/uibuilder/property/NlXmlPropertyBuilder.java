/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.idea.rendering.AttributeSnapshot;
import com.android.tools.idea.res.ProjectResourceRepository;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.property.editors.NlXmlEditors;
import com.android.tools.idea.uibuilder.property.renderer.NlXmlRenderers;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.collect.TreeMultimap;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NlXmlPropertyBuilder {
  private static final Pattern INTELLIJ_LIBRARY_NAME_PATTERN = Pattern.compile("(.+)-\\d+\\.\\d+\\.\\d+(-.+)");

  private final NlPropertiesManager myPropertiesManager;
  private final PTable myTable;
  private final List<NlComponent> myComponents;
  private final Table<String, String, NlPropertyItem> myProperties;
  private final Multimap<String, NlResourceItem> mySliceMap;

  NlXmlPropertyBuilder(@NotNull NlPropertiesManager propertiesManager,
                       @NotNull PTable table,
                       @NotNull List<NlComponent> components,
                       Table<String, String, NlPropertyItem> properties) {
    myPropertiesManager = propertiesManager;
    myTable = table;
    myComponents = components;
    myProperties = properties;
    mySliceMap = TreeMultimap.create(String::compareTo, NlResourceItem::compareTo);
  }

  public boolean build() {
    myTable.setVisible(!myComponents.isEmpty());
    if (myComponents.isEmpty()) {
      return false;
    }
    NlComponent component = myComponents.get(0);
    List<PTableItem> items = new ArrayList<>();
    items.add(new NlResourceHeader(generateFileHeader(component.getModel().getFile().getVirtualFile().getPath())));
    for (AttributeSnapshot attribute : component.getAttributes()) {
      NlPropertyItem item = myProperties.get(attribute.namespace, attribute.name);
      if (item != null) {
        resolveValue(item);
        items.add(item);
      }
    }
    items.add(new AddPropertyItem(myProperties));
    for (String header : mySliceMap.keySet()) {
      items.add(new NlResourceHeader(header));
      items.addAll(mySliceMap.get(header));
    }

    int selectedRow = myTable.getSelectedRow();
    PTableItem selectedItem = myTable.getSelectedItem();
    int editingRow = myTable.getEditingRow();
    PTableItem editingItem = editingRow >= 0 ? myTable.getItemAt(editingRow) : null;

    myTable.getModel().setItems(items);
    myTable.setRendererProvider(NlXmlRenderers.getInstance());
    myTable.setEditorProvider(NlXmlEditors.getInstance(myPropertiesManager.getProject()));

    if (myTable.getRowCount() > 0) {
      if (!restoreEditing(editingRow, editingItem)) {
        myTable.restoreSelection(selectedRow, selectedItem);
      }
    }
    return true;
  }

  private void resolveValue(@NotNull NlPropertyItem property) {
    AndroidFacet facet = property.getModel().getFacet();
    ProjectResourceRepository resourceRepository = ProjectResourceRepository.getOrCreateInstance(facet);
    ResourceResolver resolver = property.getResolver();
    if (resolver == null) {
      return;
    }
    String value = property.getValue();
    while (!StringUtil.isEmpty(value) && NlPropertyItem.isReference(value)) {
      ResourceValue resource = resolver.findResValue(value, false);
      if (resource == null) {
        resource = resolver.findResValue(value, true);
      }
      if (resource == null) {
        return;
      }
      ResourceItem item = findUserDefinedResourceItem(resourceRepository, resource);
      NlResourceItem row = new NlResourceItem(facet, resource, item, myPropertiesManager);
      value = row.getValue();
      if (StringUtil.isEmpty(row.getValue())) {
        return;
      }
      mySliceMap.put(generateHeader(resource, item), row);
    }
  }

  private boolean restoreEditing(int editingRow, PTableItem editingItem) {
    if (editingRow < 0) {
      return false;
    }
    PTableItem currentItem = myTable.getItemAt(editingRow);
    if (currentItem == null || !editingItem.getClass().equals(currentItem.getClass())) {
      myTable.editingCanceled(null);
      return false;
    }
    else {
      myTable.restoreSelection(editingRow, editingItem);
      return true;
    }
  }

  @Nullable
  private static ResourceItem findUserDefinedResourceItem(@NotNull ProjectResourceRepository resourceRepository, @NotNull ResourceValue resource) {
    if (resource.isFramework() || resource.getLibraryName() != null || resource.getResourceType() == null) {
      return null;
    }
    List<ResourceItem> items = resourceRepository.getResourceItem(resource.getResourceType(), resource.getName());
    if (items == null) {
      return null;
    }
    for (ResourceItem item : items) {
      if (item.getResourceValue(false) == resource) {
        return item;
      }
    }
    return null;
  }

  @NotNull
  private static String generateHeader(@NotNull ResourceValue resource, @Nullable ResourceItem item) {
    ResourceType resourceType = resource.getResourceType();
    String type = resourceType != null ? "<" + resource.getResourceType().getName() + ">" : "<style>";
    if (resource.isFramework()) {
      return "android " + type;
    }
    if (!StringUtil.isEmpty(resource.getLibraryName())) {
      return formatLibraryName(resource.getLibraryName()) + " " + type;
    }
    if (item != null && item.getSource() != null) {
      return generateFileHeader(item.getSource().getFile().getPath());
    }
    return type;
  }

  private static String generateFileHeader(@NotNull String path) {
    String fileName = PathUtil.getFileName(path);
    String parentPath = PathUtil.getFileName(PathUtil.getParentPath(path));
    return parentPath + "/" + fileName;
  }

  @NotNull
  private static String formatLibraryName(@NotNull String libraryName) {
    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(libraryName);
    if (coordinate != null) {
      String artifact = coordinate.getArtifactId();
      return !StringUtil.isEmpty(artifact) ? artifact : libraryName;
    }

    // TODO: Currently the library name may come from Intellij. Remove this when that is no longer the case:
    Matcher matcher = INTELLIJ_LIBRARY_NAME_PATTERN.matcher(libraryName);
    if (matcher.find()) {
      String artifact = matcher.group(1);
      return !StringUtil.isEmpty(artifact) ? artifact : libraryName;

    }
    return libraryName;
  }
}
