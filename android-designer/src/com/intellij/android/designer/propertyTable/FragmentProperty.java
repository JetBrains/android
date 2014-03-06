/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.propertyTable;

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.Property;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.renderers.LabelPropertyRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public class FragmentProperty extends Property<RadViewComponent> implements IXmlAttributeLocator {
  private final String myAttribute;
  private final PropertyRenderer myRenderer = new LabelPropertyRenderer(null);
  private final PropertyEditor myEditor;
  private final String myJavadocText;
  private final String myNamespace;

  public FragmentProperty(@NotNull String name, @Nullable String namespace, PropertyEditor editor, String javadocText) {
    super(null, name);
    myNamespace = namespace;
    myEditor = editor;
    myJavadocText = javadocText;
    setImportant(true);
    myAttribute = name;
  }

  @Override
  public Property<RadViewComponent> createForNewPresentation(@Nullable Property parent, @NotNull String name) {
    return null;
  }

  @Override
  public Object getValue(@NotNull RadViewComponent component) throws Exception {
    //noinspection ConstantConditions
    String value = component.getTag().getAttributeValue(myAttribute, myNamespace);
    return value == null ? "" : value;
  }

  @Override
  public void setValue(@NotNull final RadViewComponent component, @Nullable final Object value) throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        if (StringUtil.isEmpty((String)value)) {
          //noinspection ConstantConditions
          XmlAttribute attribute = component.getTag().getAttribute(myAttribute, myNamespace);
          if (attribute != null) {
            attribute.delete();
          }
        }
        else {
          //noinspection ConstantConditions
          component.getTag().setAttribute(myAttribute, myNamespace, (String)value);
        }
      }
    });
  }

  @Override
  public boolean isDefaultValue(@NotNull RadViewComponent component) throws Exception {
    //noinspection ConstantConditions
    return component.getTag().getAttribute(myAttribute, myNamespace) == null;
  }

  @Override
  public void setDefaultValue(@NotNull RadViewComponent component) throws Exception {
    //noinspection ConstantConditions
    if (component.getTag().getAttribute(myAttribute, myNamespace) != null) {
      setValue(component, null);
    }
  }

  @Override
  public boolean availableFor(List<PropertiesContainer> components) {
    return false;
  }

  @NotNull
  @Override
  public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  @Override
  public PropertyEditor getEditor() {
    return myEditor;
  }

  @Override
  public String getJavadocText() {
    return myJavadocText;
  }

  @Override
  public boolean checkAttribute(RadViewComponent component, XmlAttribute attribute) {
    //noinspection ConstantConditions
    return component.getTag().getAttribute(myAttribute, myNamespace) == attribute;
  }

}