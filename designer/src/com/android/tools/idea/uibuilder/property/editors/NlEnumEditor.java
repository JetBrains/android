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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.editors.EnumEditor;
import com.android.tools.idea.uibuilder.property.EmptyProperty;
import com.android.tools.idea.uibuilder.property.editors.support.EnumSupport;
import com.android.tools.idea.uibuilder.property.editors.support.EnumSupportFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class NlEnumEditor extends EnumEditor {
  private String myApiVersion;

  public static NlTableCellEditor createForTable() {
    NlTableCellEditor cellEditor = new NlTableCellEditor();
    BrowsePanel browsePanel = new BrowsePanel(cellEditor, true);
    cellEditor.init(new NlEnumEditor(cellEditor, new CustomComboBox(), browsePanel, false), browsePanel);
    return cellEditor;
  }

  public static NlEnumEditor createForInspector(@NotNull NlEditingListener listener) {
    return new NlEnumEditor(listener, new CustomComboBox(), null, true);
  }

  public static NlEnumEditor createForInspectorWithBrowseButton(@NotNull NlEditingListener listener) {
    BrowsePanel.ContextDelegate delegate = new BrowsePanel.ContextDelegate();
    NlEnumEditor editor = new NlEnumEditor(listener, new CustomComboBox(), new BrowsePanel(delegate, false), true);
    delegate.setEditor(editor);
    return editor;
  }

  @TestOnly
  public static NlEnumEditor createForTest(@NotNull NlEditingListener listener, CustomComboBox comboBox) {
    return new NlEnumEditor(listener, comboBox, null, false);
  }

  private NlEnumEditor(@NotNull NlEditingListener listener,
                         @NotNull CustomComboBox comboBox, @Nullable BrowsePanel browsePanel, boolean includeBorder) {
    super(listener, comboBox, browsePanel, includeBorder, true);
  }

  @Override
  public void setProperty(@NotNull NlProperty property) {
    super.setProperty(property, !getApiVersion(property).equals(myApiVersion));
  }

  @Override
  protected void setModel(@NotNull NlProperty property) {
    myApiVersion = getApiVersion(property);
    super.setModel(property);
  }

  @Override
  protected EnumSupport getEnumSupport(@NotNull NlProperty property) {
    // Do not inline this method. Other classes should not know about EnumSupportFactory.
    assert EnumSupportFactory.supportsProperty(property) : this.getClass().getName() + property;
    return EnumSupportFactory.create(property);
  }

  @NotNull
  private static String getApiVersion(@NotNull NlProperty property) {
    IAndroidTarget target = property != EmptyProperty.INSTANCE ? property.getModel().getConfiguration().getTarget() : null;
    return target == null ? SdkVersionInfo.HIGHEST_KNOWN_STABLE_API + "U" : target.getVersion().getApiString();
  }


}
