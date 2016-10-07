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
package com.android.tools.idea.editors.manifest;

import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.manifmerger.Actions;
import com.android.manifmerger.XmlNode;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.utils.PositionXmlParser;
import com.google.common.base.Joiner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.xml.AndroidManifest.ATTRIBUTE_GLESVERSION;
import static org.jetbrains.android.dom.attrs.ToolsAttributeUtil.ATTR_NODE;
import static org.jetbrains.android.dom.attrs.ToolsAttributeUtil.ATTR_REMOVE;

public class ManifestUtils {

  private ManifestUtils() {
  }

  @NotNull
  static List<? extends Actions.Record> getRecords(@NotNull MergedManifest manifest, @NotNull Node item) {
    Actions actions = manifest.getActions();
    if (actions != null) {
      if (item instanceof Element) {
        Element element = (Element)item;
        XmlNode.NodeKey key = getNodeKey(manifest, element);
        return actions.getNodeRecords(key);
      }
      else if (item instanceof Attr) {
        Attr attribute = (Attr)item;
        Element element = attribute.getOwnerElement();
        XmlNode.NodeKey key = getNodeKey(manifest, element);
        XmlNode.NodeName name = XmlNode.fromXmlName(attribute.getName());
        List<? extends Actions.Record> attributeRecords = actions.getAttributeRecords(key, name);
        if (!attributeRecords.isEmpty()) {
          return attributeRecords;
        }
        return actions.getNodeRecords(key);
      }
    }
    return Collections.emptyList();
  }

  @Nullable("can not find report node for xml tag")
  static XmlNode.NodeKey getNodeKey(@NotNull MergedManifest manifest, @NotNull Element element) {
    XmlNode.NodeKey key = manifest.getNodeKey(element.getNodeName());
    if (key == null) {
      Attr nameAttribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
      if (nameAttribute != null) {
        key = manifest.getNodeKey(element.getTagName() + "#" + nameAttribute.getValue());
      }
      else {
        Attr glEsVersionAttribute = element.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_GLESVERSION);
        if (glEsVersionAttribute != null) {
          key = manifest.getNodeKey(element.getTagName() + "#" + glEsVersionAttribute.getValue());
        }
        else {
          NodeList children = element.getChildNodes();
          List<String> names = new ArrayList<>(children.getLength());
          for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
              Attr childAttribute = ((Element)child).getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
              if (childAttribute != null) {
                names.add(childAttribute.getValue());
              }
            }
          }
          Collections.sort(names);
          key = manifest.getNodeKey(element.getTagName() + "#" + Joiner.on('+').join(names));
        }
      }
    }
    return key;
  }

  @NotNull
  static SourceFilePosition getActionLocation(@NotNull Module module, @NotNull Actions.Record record) {
    SourceFilePosition sourceFilePosition = record.getActionLocation();
    SourceFile sourceFile = sourceFilePosition.getFile();
    File file = sourceFile.getSourceFile();
    SourcePosition sourcePosition = sourceFilePosition.getPosition();
    if (file != null && !SourcePosition.UNKNOWN.equals(sourcePosition)) {
      VirtualFile vFile = VfsUtil.findFileByIoFile(file, false);
      assert vFile != null;
      Module fileModule = ModuleUtilCore.findModuleForFile(vFile, module.getProject());
      if (fileModule != null && !fileModule.equals(module)) {
        MergedManifest manifest = MergedManifest.get(fileModule);
        Document document = manifest.getDocument();
        assert document != null;
        Element root = document.getDocumentElement();
        assert root != null;
        int startLine = sourcePosition.getStartLine();
        int startColumn = sourcePosition.getStartColumn();
        Node node = PositionXmlParser.findNodeAtLineAndCol(document, startLine, startColumn);
        if (node == null) {
          Logger.getInstance(ManifestPanel.class).warn("Can not find node in " + fileModule + " for " + sourceFilePosition);
        }
        else {
          List<? extends Actions.Record> records = getRecords(manifest, node);
          if (!records.isEmpty()) {
            return getActionLocation(fileModule, records.get(0));
          }
        }
      }
    }
    return sourceFilePosition;
  }

  @Nullable("this file is not from the main module")
  public static IdeaSourceProvider findManifestSourceProvider(@NotNull AndroidFacet facet, @NotNull VirtualFile manifestFile) {
    for (IdeaSourceProvider provider : IdeaSourceProvider.getCurrentSourceProviders(facet)) {
      if (manifestFile.equals(provider.getManifestFile())) {
        return provider;
      }
    }
    return null;
  }

  public static @NotNull XmlFile getMainManifest(@NotNull AndroidFacet facet) {
    VirtualFile manifestFile = AndroidRootUtil.getPrimaryManifestFile(facet);
    assert manifestFile != null;
    PsiFile psiFile = PsiManager.getInstance(facet.getModule().getProject()).findFile(manifestFile);
    assert psiFile != null;
    return (XmlFile) psiFile;
  }

  static void toolsRemove(@NotNull XmlFile manifest, @NotNull Node item) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (item instanceof Element) {
      toolsRemove(manifest, (Element)item);
    }
    else if (item instanceof Attr) {
      Attr attribute = (Attr)item;
      Element element = attribute.getOwnerElement();
      // Can never mark name tag as removed, as we would need the name tag to ID the tag
      if ((ATTR_NAME.equals(attribute.getLocalName()) && ANDROID_URI.equals(attribute.getNamespaceURI())) ||
          (LintUtils.getChildCount(element) == 0 && element.getAttributes().getLength() == 1)) {
        toolsRemove(manifest, element);
      }
      else {
        toolsRemove(manifest, attribute);
      }
    }
  }

  static void toolsRemove(@NotNull XmlFile manifest, @NotNull Element item) {
    addToolsAttribute(manifest, item, ATTR_NODE, "remove");
  }

  static void toolsRemove(@NotNull XmlFile manifest, @NotNull Attr item) {
    addToolsAttribute(manifest, item.getOwnerElement(), ATTR_REMOVE, item.getName());
  }

  static void addToolsAttribute(@NotNull XmlFile manifest,
                                @NotNull Element item,
                                @NotNull String attributeName,
                                @NotNull String attributeValue) {
    if (attributeName.contains(":")) {
      throw new IllegalArgumentException("should not have namespace as it's always tools");
    }
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    AndroidResourceUtil.ensureNamespaceImported(manifest, TOOLS_URI, null);
    XmlTag parent = null;
    XmlTag[] manifestTags = new XmlTag[]{manifest.getRootTag()};
    for (Element tag : getPath(item)) {
      XmlTag found = findTag(manifestTags, tag);
      if (found == null) {
        if (parent == null) {
          // we have not even been able to find the correct matching root xml tag
          Logger.getInstance(ManifestUtils.class).warn("can not root tag " + tag + " in xml file " + manifest);
          return;
        }
        found = parent.createChildTag(tag.getLocalName(), null, null, false); // we ignore namespace for tag names in manifest
        found = parent.addSubTag(found, true); // add it right away, or namespace will not work
        Attr nameAttribute = tag.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
        if (nameAttribute != null) {
          found.setAttribute(nameAttribute.getLocalName(), nameAttribute.getNamespaceURI(), nameAttribute.getValue());
        }
        if (tag == item) {
          found.setAttribute(attributeName, TOOLS_URI ,attributeValue);
        }
      }
      else if (tag == item) {
        XmlAttribute attribute = found.getAttribute(attributeName, TOOLS_URI);
        if (attribute == null) {
          found.setAttribute(attributeName, TOOLS_URI, attributeValue);
        }
        else {
          found.setAttribute(attributeName, TOOLS_URI, attribute.getValue() + "," + attributeValue);
        }
      }
      parent = found;
      manifestTags = found.getSubTags();
    }
  }

  @Nullable
  private static XmlTag findTag(@NotNull XmlTag[] manifestTags, @NotNull Element tag) {
    Attr nameAttribute = tag.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
    String name = nameAttribute == null ? null : nameAttribute.getValue();
    for (XmlTag xmlTag : manifestTags) {
      if (tag.getTagName().equals(xmlTag.getName())) {
        if (name != null) {
          XmlAttribute xmlAttribute = xmlTag.getAttribute(ATTR_NAME, ANDROID_URI);
          if (xmlAttribute != null && name.equals(xmlAttribute.getValue())) {
            return xmlTag;
          }
        }
        else {
          return xmlTag;
        }
      }
    }
    return null;
  }

  @NotNull
  private static List<Element> getPath(@NotNull Element element) {
    Element tag = element;
    List<Element> path = new ArrayList<>();
    while (true) {
      path.add(tag);
      Node parentNode = tag.getParentNode();
      if (parentNode instanceof Element) {
        tag = (Element)parentNode;
      }
      else {
        break;
      }
    }
    Collections.reverse(path);
    return path;
  }
}
