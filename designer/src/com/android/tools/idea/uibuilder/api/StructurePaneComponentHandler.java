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
package com.android.tools.idea.uibuilder.api;

import com.android.AndroidXConstants;
import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/**
 * A handler for a component in the structure pane.
 */
public class StructurePaneComponentHandler extends PropertyComponentHandler {
  /**
   * Returns the title used to identify this component in the structure pane
   *
   * @param component a component to get the title of
   * @return a title of the component (usually the non qualified tag name)
   */
  @NotNull
  public String getTitle(@NotNull NlComponent component) {
    return getSimpleTagName(component.getTagName());
  }

  /**
   * Returns the attribute values used to identify this component in the structure pane
   *
   * @param component a component to get the title attributes from
   * @return a string representing important attribute values or the empty string if no attributes should be shown
   */
  @NotNull
  public String getTitleAttributes(@NotNull NlComponent component) {
    return "";
  }

  /**
   * Returns the icon used to identify this component in the structure pane.<br>
   * This default implementation assumes the icon is one of the builtin icons.
   *
   * @param component a component to get the icon for
   * @return an icon to identify the component
   */
  @NotNull
  public Icon getIcon(@NotNull NlComponent component) {
    String name = component.getTagName();
    if (NlComponentHelperKt.isOrHasSuperclass(component, AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_BARRIER)) {
      String direction = component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_BARRIER_DIRECTION);
      if (SdkConstants.VALUE_TOP.equals(direction) || SdkConstants.VALUE_BOTTOM.equals(direction)) {
        name += "Horizontal";
      }
    }
    return loadBuiltinIcon(name);
  }
}
