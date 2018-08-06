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
package com.android.tools.idea.uibuilder.handlers.motion.property2;

import com.android.tools.adtui.model.stdui.EditingSupport;
import com.android.tools.adtui.ptable2.PTableItem;
import com.android.tools.idea.common.property2.api.ActionIconButton;
import com.android.tools.idea.common.property2.api.PropertyItem;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.AUTO_URI;

public class MotionPropertyItem implements PropertyItem, PTableItem {
  private final String myName;
  private final MotionSceneModel.BaseTag myTag;
  private final EditingSupport myEditingSupport;

  public MotionPropertyItem(@NotNull String name, @NotNull MotionSceneModel.BaseTag tag) {
    myName = name;
    myTag = tag;
    myEditingSupport = EditingSupport.Companion.getINSTANCE();
  }

  @NotNull
  @Override
  public String getNamespace() {
    return myTag.isAndroidAttribute(myName) ? ANDROID_URI : AUTO_URI;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  public MotionSceneModel.BaseTag getTag() {
    return myTag;
  }

  @Nullable
  @Override
  public String getValue() {
    if (myTag instanceof MotionSceneModel.CustomAttributes) {
      MotionSceneModel.CustomAttributes customAttributes = (MotionSceneModel.CustomAttributes)myTag;
      String valueTag = customAttributes.getValueTagName();
      return valueTag != null ? customAttributes.getValue(valueTag) : null;
    }
    else {
      return myTag.getValue(myName);
    }
  }

  @Override
  public void setValue(@Nullable String newValue) {
    if (myTag instanceof MotionSceneModel.CustomAttributes) {
      MotionSceneModel.CustomAttributes customAttributes = (MotionSceneModel.CustomAttributes)myTag;
      String valueTag = customAttributes.getValueTagName();
      if (valueTag != null) {
        customAttributes.setValue(valueTag, StringUtil.notNullize(newValue));
      }
    }
    else {
      myTag.setValue(myName, StringUtil.notNullize(newValue));
    }
  }

  @Override
  public boolean isReference() {
    return false;
  }

  @Nullable
  @Override
  public ActionIconButton getBrowseButton() {
    return null;
  }

  @Nullable
  @Override
  public Icon getNamespaceIcon() {
    return null;
  }

  @Nullable
  @Override
  public String getResolvedValue() {
    return null;
  }

  @NotNull
  @Override
  public String getTooltipForName() {
    return "";
  }

  @NotNull
  @Override
  public String getTooltipForValue() {
    return "";
  }

  @NotNull
  @Override
  public EditingSupport getEditingSupport() {
    return myEditingSupport;
  }

  @NotNull
  @Override
  public PropertyItem getDesignProperty() {
    throw new UnsupportedOperationException();
  }
}
