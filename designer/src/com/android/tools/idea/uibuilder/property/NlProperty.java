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
package com.android.tools.idea.uibuilder.property;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableCellRenderer;
import java.util.Set;

public class NlProperty extends PTableItem {
  // Certain attributes are special and do not have an attribute defintion from attrs.xml
  private static final Set<String> ATTRS_WITHOUT_DEFN = ImmutableSet.of(
    SdkConstants.ATTR_STYLE, // <View style="..." />
    SdkConstants.ATTR_CLASS, // class is suggested as an attribute for a <fragment>!
    SdkConstants.ATTR_LAYOUT // <include layout="..." />
  );

  @NotNull private final XmlTag myTag;
  @NotNull private final XmlAttributeDescriptor myDescriptor;
  @Nullable private final AttributeDefinition myDefinition;
  private NlPropertyRenderer myRenderer;

  public NlProperty(@NotNull XmlTag tag, @NotNull XmlAttributeDescriptor descriptor, @Nullable AttributeDefinition attributeDefinition) {
    if (attributeDefinition == null && !ATTRS_WITHOUT_DEFN.contains(descriptor.getName())) {
      throw new IllegalArgumentException("Missing attribute definition for " + descriptor.getName());
    }

    myTag = tag;
    myDescriptor = descriptor;
    myDefinition = attributeDefinition;
  }

  @Override
  @NotNull
  public String getName() {
    return myDescriptor.getName();
  }

  @Nullable
  public String getValue() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myTag.isValid()) {
      return "";
    }
    return myTag.getAttributeValue(myDescriptor.getName(myTag));
  }

  @NotNull
  @Override
  public TableCellRenderer getCellRenderer() {
    if (myRenderer == null) {
      myRenderer = new NlPropertyRenderer();
    }
    return myRenderer;
  }

  @Override
  public String toString() {
    String namespace = "unknown";
    if (myDescriptor instanceof NamespaceAwareXmlAttributeDescriptor) {
      namespace = ((NamespaceAwareXmlAttributeDescriptor)myDescriptor).getNamespace(myTag);
    }

    return Objects.toStringHelper(this)
      .add("name", getName())
      .add("namespace", namespace)
      .toString();
  }

  public String getTooltipText() {
    return myDescriptor.getName(myTag);
  }
}
