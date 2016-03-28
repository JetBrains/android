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
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.editors.NlPropertyEditors;
import com.android.tools.idea.uibuilder.property.ptable.PTableCellEditor;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import com.android.tools.idea.uibuilder.property.renderer.NlPropertyRenderers;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableCellRenderer;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class NlProperty extends PTableItem {
  // Certain attributes are special and do not have an attribute definition from attrs.xml
  private static final Set<String> ATTRS_WITHOUT_DEFN = ImmutableSet.of(
    SdkConstants.ATTR_STYLE, // <View style="..." />
    SdkConstants.ATTR_CLASS, // class is suggested as an attribute for a <fragment>!
    SdkConstants.ATTR_LAYOUT // <include layout="..." />
  );

  @NotNull protected final NlComponent myComponent;
  @Nullable protected final AttributeDefinition myDefinition;
  @NotNull private final String myName;
  @Nullable private final String myNamespace;

  public static NlProperty create(@NotNull NlComponent component,
                                  @NotNull XmlAttributeDescriptor descriptor,
                                  @Nullable AttributeDefinition attributeDefinition) {
    if (attributeDefinition != null && attributeDefinition.getFormats().contains(AttributeFormat.Flag)) {
      return new NlFlagProperty(component, descriptor, attributeDefinition);
    }
    else {
      return new NlProperty(component, descriptor, attributeDefinition);
    }
  }

  protected NlProperty(@NotNull NlComponent component,
                       @NotNull XmlAttributeDescriptor descriptor,
                       @Nullable AttributeDefinition attributeDefinition) {
    if (attributeDefinition == null && !ATTRS_WITHOUT_DEFN.contains(descriptor.getName())) {
      throw new IllegalArgumentException("Missing attribute definition for " + descriptor.getName());
    }

    // NOTE: we do not save any PSI data structures as fields as they could go out of date as the user edits the file.
    // Instead, we have a reference to the component, and query whatever information we need from the component, and expect
    // that the component can provide that information by having a shadow copy that is consistent with the rendering
    myComponent = component;
    myName = descriptor.getName();
    myNamespace = descriptor instanceof NamespaceAwareXmlAttributeDescriptor ?
                  ((NamespaceAwareXmlAttributeDescriptor)descriptor).getNamespace(component.getTag()) : null;
    myDefinition = attributeDefinition;
  }

  @NotNull
  public NlComponent getComponent() {
    return myComponent;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @Nullable
  public String getValue() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myComponent.getAttribute(myNamespace, myName);
  }

  @Nullable
  public ResourceResolver getResolver() {
    Configuration configuration = myComponent.getModel().getConfiguration();
    //noinspection ConstantConditions
    if (configuration == null) { // happens in unit test
      return null;
    }

    // TODO: what happens if this is configuration dependent? (in theory, those should be edited in the theme editor)
    return configuration.getResourceResolver();
  }

  @Override
  public void setValue(Object value) {
    assert ApplicationManager.getApplication().isDispatchThread();
    final String attrValue = value == null ? null : value.toString();
    String msg = String.format("Set %1$s.%2$s to %3$s", myComponent.getTagName(), myName, attrValue);
    new WriteCommandAction.Simple(myComponent.getModel().getProject(), msg, myComponent.getTag().getContainingFile()) {
      @Override
      protected void run() throws Throwable {
        String v = StringUtil.isEmpty(attrValue) ? null : attrValue;
        myComponent.setAttribute(myNamespace, myName, v);
      }
    }.execute();
  }

  @NotNull
  public List<String> getParentStylables() {
    return myDefinition == null ? Collections.<String>emptyList() : myDefinition.getParentStyleables();
  }

  @Nullable
  public AttributeDefinition getDefinition() {
    return myDefinition;
  }

  @NotNull
  @Override
  public TableCellRenderer getCellRenderer() {
    return NlPropertyRenderers.get(this);
  }

  @Override
  public boolean isEditable(int col) {
    return NlPropertyEditors.get(this) != null;
  }

  @Override
  public PTableCellEditor getCellEditor() {
    return NlPropertyEditors.get(this);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
      .add("name", myName)
      .add("namespace", namespaceToPrefix(myNamespace))
      .toString();
  }

  @Override
  public String getTooltipText() {
    StringBuilder sb = new StringBuilder(100);
    sb.append(namespaceToPrefix(myNamespace));
    sb.append(myName);
    if (myDefinition != null) {
      sb.append(": ");
      sb.append(myDefinition.getDocValue(null));
    }
    return sb.toString();
  }

  @NotNull
  private static String namespaceToPrefix(@Nullable String namespace) {
    if (namespace != null && SdkConstants.NS_RESOURCES.equalsIgnoreCase(namespace)) {
      return SdkConstants.ANDROID_PREFIX;
    } else {
      return "";
    }
  }
}
