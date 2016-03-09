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
package com.intellij.android.designer.model;

import com.android.ide.common.rendering.api.ViewInfo;
import com.intellij.android.designer.designSurface.AndroidPasteFactory;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadLayout;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Operations for a hierarchy of {@link com.intellij.android.designer.model.RadViewComponent} instances, such as adding, removing
 * and moving components.
 */
public class RadComponentOperations {
  private RadComponentOperations() {
    // No state
  }

  public static RadViewComponent createComponent(@Nullable XmlTag tag, @NotNull MetaModel metaModel) throws Exception {
    RadViewComponent component = (RadViewComponent)metaModel.getModel().newInstance();
    assert component != null : tag;
    component.setMetaModel(metaModel);
    component.setTag(tag);

    Class<RadLayout> layout = metaModel.getLayout();
    if (layout == null) {
      component.setLayout(new RadViewLayout());
    }
    else {
      component.setLayout(layout.newInstance());
    }

    return component;
  }

  public static void moveComponent(final RadViewComponent container,
                                   final RadViewComponent movedComponent,
                                   @Nullable final RadViewComponent insertBefore)
    throws Exception {
    movedComponent.removeFromParent();
    container.add(movedComponent, insertBefore);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag xmlTag = movedComponent.getTag();

        XmlTag parentTag = container.getTag();
        XmlTag nextTag = insertBefore == null ? null : insertBefore.getTag();
        XmlTag newXmlTag;
        if (nextTag == null) {
          newXmlTag = parentTag.addSubTag(xmlTag, false);
        }
        else {
          newXmlTag = (XmlTag)parentTag.addBefore(xmlTag, nextTag);
        }

        xmlTag.delete();
        movedComponent.updateTag(newXmlTag);
      }
    });

    XmlFile xmlFile = RadModelBuilder.getXmlFile(container);
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(xmlFile.getProject());
    Document document = psiDocumentManager.getDocument(xmlFile);
    if (document != null) {
      psiDocumentManager.commitDocument(document);
    }

    PropertyParser propertyParser = RadModelBuilder.getPropertyParser(container);
    if (propertyParser != null) {
      propertyParser.load(movedComponent);
    }
  }

  public static void addComponent(RadViewComponent container, final RadViewComponent newComponent, @Nullable RadViewComponent insertBefore)
    throws Exception {
    container.add(newComponent, insertBefore);

    addComponentTag(container.getTag(), newComponent, insertBefore == null ? null : insertBefore.getTag(), new Computable<String>() {
      @Override
      public String compute() {
        String creation;
        if (newComponent.getInitialPaletteItem() != null) {
          creation = newComponent.getInitialPaletteItem().getCreation();
        } else {
          creation = newComponent.getMetaModel().getCreation();
        }
        return creation == null ? newComponent.getCreationXml() : creation;
      }
    });

    PropertyParser propertyParser = RadModelBuilder.getPropertyParser(container);
    if (propertyParser != null) {
      propertyParser.load(newComponent);

      if (!newComponent.getTag().isEmpty()) {
        addComponent(newComponent, ViewsMetaManager.getInstance(newComponent.getTag().getProject()), propertyParser);
      }
    }

    IdManager idManager = IdManager.get();
    if (idManager.needsDefaultId(newComponent)) {
      idManager.ensureIds(newComponent);
    }
  }

  private static void addComponent(RadViewComponent parentComponent,
                                   MetaManager metaManager,
                                   PropertyParser propertyParser) throws Exception {
    for (XmlTag tag : parentComponent.getTag().getSubTags()) {
      MetaModel metaModel = metaManager.getModelByTag(tag.getName());
      if (metaModel == null) {
        metaModel = metaManager.getModelByTag(VIEW_TAG);
        assert metaModel != null;
      }

      RadViewComponent component = createComponent(tag, metaModel);

      parentComponent.add(component, null);
      propertyParser.load(component);

      addComponent(component, metaManager, propertyParser);
    }
  }

  public static void pasteComponent(RadViewComponent container, RadViewComponent newComponent, @Nullable RadViewComponent insertBefore)
    throws Exception {
    container.add(newComponent, insertBefore);

    PropertyParser propertyParser = RadModelBuilder.getPropertyParser(container);
    if (propertyParser != null) {
      pasteComponent(newComponent, container.getTag(), insertBefore == null ? null : insertBefore.getTag(), propertyParser);
      IdManager.get().ensureIds(newComponent);
    }
  }

  private static void pasteComponent(final RadViewComponent component,
                                     XmlTag parentTag,
                                     @Nullable XmlTag nextTag,
                                     PropertyParser propertyParser) throws Exception {
    addComponentTag(parentTag, component, nextTag, new Computable<String>() {
      @Override
      public String compute() {
        Element pasteProperties = component.extractClientProperty(AndroidPasteFactory.KEY);

        if (pasteProperties == null) {
          return component.getMetaModel().getCreation();
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<").append(component.getMetaModel().getTag());

        for (Object object : pasteProperties.getAttributes()) {
          Attribute attribute = (Attribute)object;
          builder.append(" ").append(attribute.getName()).append("=\"").append(attribute.getValue()).append("\"");
        }

        for (Object object : pasteProperties.getChildren()) {
          Element element = (Element)object;
          String namespace = element.getName();

          for (Object child : element.getAttributes()) {
            Attribute attribute = (Attribute)child;
            builder.append(" ").append(namespace).append(":").append(attribute.getName()).append("=\"").append(attribute.getValue()).append(
              "\"");
          }
        }

        // TODO: Handle Android namespace properly if changed to something custom
        if (builder.indexOf("android:layout_width=\"") == -1) {
          builder.append(" android:layout_width=\"wrap_content\"");
        }
        if (builder.indexOf("android:layout_height=\"") == -1) {
          builder.append(" android:layout_height=\"wrap_content\"");
        }

        return builder.append("/>").toString();
      }
    });

    XmlTag xmlTag = component.getTag();
    List<RadComponent> children = component.getChildren();
    int size = children.size();
    for (int i = 0; i < size; i++) {
      RadViewComponent child = (RadViewComponent)children.get(i);

      XmlTag nextChildTag = null;
      if (i + 1 < size) {
        nextChildTag = ((RadViewComponent)children.get(i + 1)).getTag();
      }

      pasteComponent(child, xmlTag, nextChildTag, propertyParser);
    }

    propertyParser.load(component);
  }

  public static void addComponentTag(final XmlTag parentTag,
                                     final RadViewComponent component,
                                     @Nullable final XmlTag nextTag,
                                     final Computable<String> tagBuilder) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        Project project;
        RadViewComponent root = null;
        XmlFile xmlFile = null;

        if (!checkTag(parentTag) && component.getParent() == component.getRoot()) {
          root = (RadViewComponent)component.getParent();
          xmlFile = RadModelBuilder.getXmlFile(root);
          project = xmlFile.getProject();
        }
        else {
          project = parentTag.getProject();
        }

        Language language = StdFileTypes.XML.getLanguage();
        XmlTag xmlTag =
          XmlElementFactory.getInstance(project).createTagFromText("\n" + tagBuilder.compute(), language);

        if (checkTag(parentTag)) {
          String namespacePrefix = parentTag.getPrefixByNamespace(ANDROID_URI);
          // In the metadata the namespace prefix is hardcoded as "android"; convert to the current file's namespace
          if (namespacePrefix != null && !ANDROID_NS_NAME.equals(namespacePrefix)) {
            convertNamespacePrefix(xmlTag, namespacePrefix);
          }

          if (nextTag == null) {
            xmlTag = parentTag.addSubTag(xmlTag, false);
          }
          else {
            xmlTag = (XmlTag)parentTag.addBefore(xmlTag, nextTag);
          }
        }
        else {
          xmlTag.setAttribute(XMLNS_ANDROID, ANDROID_URI);
          if (xmlFile != null) {
            XmlDocument document = xmlFile.getDocument();
            if (document != null) {
              xmlTag = (XmlTag)document.add(xmlTag);
              XmlUtil.expandTag(xmlTag);
              XmlTag rootTag = document.getRootTag();
              if (rootTag != null) {
                root.setTag(rootTag);
              }
            }
          }
        }

        component.setTag(xmlTag);
      }
    });
  }

  private static boolean checkTag(XmlTag tag) {
    try {
      return tag != null && tag.getFirstChild() != null && !(tag.getFirstChild() instanceof PsiErrorElement);
    }
    catch (Throwable e) {
      return false;
    }
  }

  private static void convertNamespacePrefix(XmlTag xmlTag, String namespacePrefix) {
    for (XmlAttribute attribute : xmlTag.getAttributes()) {
      if (ANDROID_NS_NAME.equals(attribute.getNamespacePrefix())) {
        attribute.setName(namespacePrefix + ":" + attribute.getLocalName());
      }
    }
    for (XmlTag subTag : xmlTag.getSubTags()) {
      convertNamespacePrefix(subTag, namespacePrefix);
    }
  }

  public static void deleteAttribute(RadComponent component, String name) {
    deleteAttribute(((RadViewComponent)component).getTag(), name);
  }

  public static void deleteAttribute(XmlTag tag, String name) {
    deleteAttribute(tag, name, ANDROID_URI);
  }

  public static void deleteAttribute(XmlTag tag, String name, String namespace) {
    XmlAttribute attribute = tag.getAttribute(name, namespace);
    if (attribute != null) {
      attribute.delete();
    }
  }

  public static void printTree(StringBuilder builder, RadComponent component, int level) {
    for (int i = 0; i < level; i++) {
      builder.append('\t');
    }
    builder.append(component).append(" | ").append(component.getLayout()).append(" | ").append(component.getMetaModel().getTag())
      .append(" | ").append(component.getMetaModel().getTarget()).append(" = ").append(component.getChildren().size()).append("\n");
    for (RadComponent childComponent : component.getChildren()) {
      printTree(builder, childComponent, level + 1);
    }
  }

  public static void printTree(StringBuilder builder, ViewInfo viewInfo, int level) {
    for (int i = 0; i < level; i++) {
      builder.append('\t');
    }
    builder.append(viewInfo.getClassName()).append(" | ");
    try {
      builder.append(viewInfo.getViewObject()).append(" | ");
    }
    catch (Throwable e) {
      // ignored
    }
    try {
      builder.append(viewInfo.getLayoutParamsObject()).append(" = ");
    }
    catch (Throwable e) {
      // ignored
    }
    builder.append(viewInfo.getChildren().size()).append("\n");
    for (ViewInfo childViewInfo : viewInfo.getChildren()) {
      printTree(builder, childViewInfo, level + 1);
    }
  }
}