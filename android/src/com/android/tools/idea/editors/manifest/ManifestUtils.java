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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.tools.idea.projectsystem.SourceProvidersKt.findByFile;
import static com.android.tools.idea.projectsystem.SourceProvidersKt.isManifestFile;
import static com.android.xml.AndroidManifest.ATTRIBUTE_GLESVERSION;
import static org.jetbrains.android.dom.attrs.ToolsAttributeUtil.ATTR_NODE;
import static org.jetbrains.android.dom.attrs.ToolsAttributeUtil.ATTR_REMOVE;

import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.manifmerger.Actions;
import com.android.manifmerger.IntentFilterNodeKeyResolver;
import com.android.manifmerger.XmlNode;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.model.MergedManifestSnapshot;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.android.tools.idea.projectsystem.SourceProviderManager;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.lint.detector.api.Lint;
import com.android.utils.PositionXmlParser;
import com.google.common.io.Files;
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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class ManifestUtils {

  private ManifestUtils() {
  }

  @NotNull
  public static List<? extends Actions.Record> getRecords(@NotNull MergedManifestSnapshot manifest, @NotNull Node node) {
    Actions actions = manifest.getActions();
    if (actions != null) {
      // if node is an Attr, element is node's owner element; otherwise element is (Element)node
      final Element element;
      if (node instanceof Element) {
        element = (Element)node;
      } else if (node instanceof Attr) {
        Attr attribute = (Attr)node;
        element = attribute.getOwnerElement();
      } else {
        return Collections.emptyList();
      }
      Node parentNode = element.getParentNode();
      if (parentNode instanceof Element) {
        Element parentElement = (Element)parentNode;
        // special case when parent element is an intent-filter because the intent-filter sub-element node keys are not necessarily
        // unique in the xml file, but the sub-elements have the same history as the intent-filter
        if (TAG_INTENT_FILTER.equals(parentElement.getTagName())) {
          XmlNode.NodeKey parentKey = getNodeKey(manifest, parentElement);
          if (parentKey != null) {
            return actions.getNodeRecords(parentKey);
          }
        }
      }
      XmlNode.NodeKey key = getNodeKey(manifest, element);
      if (key == null) {
        return Collections.emptyList();
      }
      if (node instanceof Attr) {
        Attr attribute = (Attr)node;
        XmlNode.NodeName nodeName = XmlNode.fromXmlName(attribute.getName());
        List<? extends Actions.Record> attributeRecords = actions.getAttributeRecords(key, nodeName);
        if (!attributeRecords.isEmpty()) {
          return attributeRecords;
        }
      }
      return actions.getNodeRecords(key);
    }
    return Collections.emptyList();
  }

  @Nullable/*can not find report node for xml tag*/
  static XmlNode.NodeKey getNodeKey(@NotNull MergedManifestSnapshot manifest, @NotNull Element element) {
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
        } else if (TAG_INTENT_FILTER.equals(element.getTagName())) {
          key = manifest.getNodeKey(element.getTagName() + "#" + IntentFilterNodeKeyResolver.INSTANCE.getKey(element));
        } else {
          key = null;
        }
      }
    }
    return key;
  }

  @Nullable
  public static Node getSourceNode(@NotNull Module module, @NotNull Actions.Record record) {
    SourceFilePosition sourceFilePosition = record.getActionLocation();
    SourceFile sourceFile = sourceFilePosition.getFile();
    File file = sourceFile.getSourceFile();
    SourcePosition sourcePosition = sourceFilePosition.getPosition();
    if (file != null && !SourcePosition.UNKNOWN.equals(sourcePosition)) {
      VirtualFile vFile = VfsUtil.findFileByIoFile(file, false);
      assert vFile != null;
      Module fileModule = ModuleUtilCore.findModuleForFile(vFile, module.getProject());
      if (fileModule != null && !fileModule.equals(module)) { // redirect to library merged manifest?
        MergedManifestSnapshot manifest = MergedManifestManager.getSnapshot(fileModule);
        Document document = manifest.getDocument();
        assert document != null;
        Element root = document.getDocumentElement();
        assert root != null;
        int startLine = sourcePosition.getStartLine();
        int startColumn = sourcePosition.getStartColumn();
        return PositionXmlParser.findNodeAtLineAndCol(document, startLine, startColumn);
      } else {
        int startLine = sourcePosition.getStartLine();
        int startColumn = sourcePosition.getStartColumn();
        try {
          byte[] bytes = Files.toByteArray(file);
          Document document = PositionXmlParser.parse(bytes);
          return PositionXmlParser.findNodeAtLineAndCol(document, startLine, startColumn);
        }
        catch (IOException | SAXException | ParserConfigurationException ignore) {
        }
      }
    }

    return null;
  }

  @NotNull
  static SourceFilePosition getActionLocation(@NotNull Module module, @NotNull Actions.Record record) {
    // This is an artifact of the way we generate the merged manifest: when the merger asks for the
    // content of a manifest from a dependency, we use the FileStreamProvider to swap in the result of a
    // recursive merged manifest computation for the corresponding module. This means that the record points
    // to the manifest file as the source, but the record's source file position points to a node in the
    // merged manifest for the corresponding module. To get the location in the actual manifest, we recurse
    // back through the incremental merged manifests that lead to this one, stopping when we hit a source
    // file that wasn't replaced by the FileStreamProvider.
    SourceFilePosition sourceFilePosition = record.getActionLocation();
    SourceFile sourceFile = sourceFilePosition.getFile();
    File file = sourceFile.getSourceFile();
    SourcePosition sourcePosition = sourceFilePosition.getPosition();
    if (file == null || SourcePosition.UNKNOWN.equals(sourcePosition)) {
      return sourceFilePosition;
    }
    VirtualFile vFile = VfsUtil.findFileByIoFile(file, false);
    Module fileModule = vFile == null ? null : ModuleUtilCore.findModuleForFile(vFile, module.getProject());
    if (module.equals(fileModule)) {
      // When merging manifests for a module, we don't replace a file's content with a recursive merged manifest
      // computation if that file belongs to that module (e.g. primary, flavor, and build type manifests).
      // So in this case the source file position is already accurate.
      return sourceFilePosition;
    }
    AndroidFacet facet = fileModule == null ? null : AndroidFacet.getInstance(fileModule);
    if (facet == null || !isManifestFile(facet, vFile)) {
      // Non-manifest files (e.g. navigation) don't get replaced either, so we already have the correct position.
      return sourceFilePosition;
    }
    MergedManifestSnapshot manifest = MergedManifestManager.getSnapshot(fileModule);
    Document document = manifest.getDocument();
    assert document != null;
    Element root = document.getDocumentElement();
    assert root != null;
    int startLine = sourcePosition.getStartLine();
    int startColumn = sourcePosition.getStartColumn();
    Node node = PositionXmlParser.findNodeAtLineAndCol(document, startLine, startColumn);
    if (node == null) {
      Logger.getInstance(ManifestPanel.class).warn("Can not find node in " + fileModule + " for " + sourceFilePosition);
      return sourceFilePosition;
    }
    List<? extends Actions.Record> records = getRecords(manifest, node);
    if (!records.isEmpty()) {
      return getActionLocation(fileModule, records.get(0));
    }
    return sourceFilePosition;
  }

  @Nullable/*this file is not from the main module*/
  public static NamedIdeaSourceProvider findManifestSourceProvider(@NotNull AndroidFacet facet, @NotNull VirtualFile manifestFile) {
    return findByFile(SourceProviderManager.getInstance(facet).getCurrentSourceProviders(), manifestFile);
  }

  static @NotNull XmlFile getMainManifest(@NotNull AndroidFacet facet) {
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
          (Lint.getChildCount(element) == 0 && element.getAttributes().getLength() == 1)) {
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
    IdeResourcesUtil.ensureNamespaceImported(manifest, TOOLS_URI, null);
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
