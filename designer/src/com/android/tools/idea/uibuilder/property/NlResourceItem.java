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

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.idea.common.property.PropertiesManager;
import com.android.tools.idea.editors.strings.StringsWriteUtils;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;

public class NlResourceItem extends PTableItem {
  private AndroidFacet myFacet;
  private PropertiesManager myPropertiesManager;
  private ResourceValue myValue;
  private ResourceItem myItem;

  public NlResourceItem(@NotNull AndroidFacet facet,
                        @NotNull ResourceValue value,
                        @Nullable ResourceItem item,
                        @NotNull PropertiesManager propertiesManager) {
    myFacet = facet;
    myPropertiesManager = propertiesManager;
    myValue = value;
    myItem = item;
  }

  public int hashCode() {
    return myValue.getName().hashCode();
  }

  public boolean equals(@Nullable Object object) {
    if (!(object instanceof NlResourceItem)) {
      return false;
    }
    NlResourceItem other = (NlResourceItem)object;
    return myValue.getName().equals(other.myValue.getName());
  }

  public int compareTo(@NotNull NlResourceItem anotherItem) {
    return myValue.getName().compareTo(anotherItem.myValue.getName());
  }

  public AndroidFacet getFacet() {
    return myFacet;
  }

  public ResourceItem getResourceItem() {
    return myItem;
  }

  @NotNull
  @Override
  public String getName() {
    return myValue.getName();
  }

  @Nullable
  @Override
  public String getValue() {
    return convert(myValue);
  }

  @Override
  public boolean isEditable(int column) {
    return column == 1 && myItem != null &&
           FolderTypeRelationship.getRelatedFolders(myItem.getType()).contains(ResourceFolderType.VALUES);
  }

  @Override
  public void setValue(@Nullable Object value) {
    if (myItem == null) {
      throw new IllegalAccessError();
    }
    Project project = myFacet.getModule().getProject();
    String oldValue = myValue.getValue();
    String strValue = value == null || StringUtil.isEmpty(value.toString()) ? "" : value.toString();
    TransactionGuard.submitTransaction(project, () -> StringsWriteUtils.setItemText(project, myItem, strValue));
    myValue.setValue(strValue);
    myPropertiesManager.resourceChanged(myItem, oldValue, strValue);
  }

  @Nullable
  private static String convert(@NotNull ResourceValue resource) {
    String value = resource.getValue();
    if (StringUtil.isEmpty(value)) {
      return null;
    }
    if (resource.isFramework()) {
      if (value.startsWith(PREFIX_RESOURCE_REF) && !value.startsWith(SdkConstants.ANDROID_PREFIX)) {
        return SdkConstants.ANDROID_PREFIX + value.substring(1);
      }
      if (value.startsWith(PREFIX_THEME_REF) && !value.startsWith(SdkConstants.ANDROID_THEME_PREFIX)) {
        return SdkConstants.ANDROID_THEME_PREFIX + value.substring(1);
      }
    }
    return value;
  }
}
