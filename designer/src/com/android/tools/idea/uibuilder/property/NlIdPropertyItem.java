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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.ide.common.resources.ResourcesUtil.stripPrefixFromId;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.PropertiesManager;
import com.android.tools.idea.uibuilder.property2.support.NeleIdRenameProcessor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.XmlName;
import java.util.List;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NlIdPropertyItem extends NlPropertyItem {

  protected NlIdPropertyItem(@NotNull XmlName name,
                             @Nullable AttributeDefinition attributeDefinition,
                             @NotNull List<NlComponent> components,
                             @Nullable PropertiesManager propertiesManager) {
    super(name, attributeDefinition, components, propertiesManager);
  }

  @Nullable
  @Override
  public String getValue() {
    return super.getValue() != null ? stripPrefixFromId(super.getValue()) : null;
  }

  @Nullable
  @Override
  public String resolveValue(@Nullable String value) {
    return value;
  }

  @Override
  public void setValue(@Nullable Object value) {
    String newValue = value != null ? value.toString() : null;
    String newId = newValue != null ? stripPrefixFromId(newValue) : "";
    String oldId = getValue();
    XmlTag tag = getTag();
    if (newValue != null && !newValue.isEmpty() && !newValue.startsWith(PREFIX_RESOURCE_REF) && !newValue.startsWith(PREFIX_THEME_REF)) {
      newValue = NEW_ID_PREFIX + newValue;
    }

    if (oldId != null
        && newValue != null
        && !oldId.isEmpty()
        && !newId.isEmpty()
        && !oldId.equals(newId)
        && tag != null
        && tag.isValid()) {
      // Offer rename refactoring?
      XmlAttribute attribute = tag.getAttribute(ATTR_ID, ANDROID_URI);
      if (attribute != null) {
        Module module = getModel().getModule();
        Project project = module.getProject();
        XmlAttributeValue valueElement = attribute.getValueElement();
        if (valueElement != null && valueElement.isValid()) {
          NeleIdRenameProcessor processor = new NeleIdRenameProcessor(project, valueElement, newValue);
          processor.run();
          return;
        }
      }
    }

    super.setValue(newValue);
  }
}
