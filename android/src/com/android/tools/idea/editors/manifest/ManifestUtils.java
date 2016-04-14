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

import com.android.SdkConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.inspections.lint.SuppressLintIntentionAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ManifestUtils {

  private ManifestUtils() {
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

  /**
   * @param line Line number in human form: starting from 1, NOT 0!
   * @param col Column number in human readable form, starting from 1, NOT 0!
   */
  @Nullable("could not find tag at that location")
  public static XmlTag getXmlTag(XmlFile file, int line, int col) {
    Document doc = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    assert doc != null;
    int offset = doc.getLineStartOffset(line - 1) + col - 1;
    PsiElement psiElement = file.findElementAt(offset); // this will find the "<" tag
    PsiElement tag = psiElement == null ? null : psiElement.getParent(); // the parent is the XmlTag
    return tag instanceof XmlTag ? (XmlTag)tag : null;
  }

  static void toolsRemove(@NotNull XmlFile manifest, @NotNull XmlElement item) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (item instanceof XmlTag) {
      toolsRemove(manifest, (XmlTag)item);
    }
    else if (item instanceof XmlAttribute) {
      XmlAttribute xmlAttribute = (XmlAttribute)item;
      XmlTag xmlTag = xmlAttribute.getParent();
      // can never mark name tag as removed, as we would need the name tag to ID the tag
      if ((SdkConstants.ATTR_NAME.equals(xmlAttribute.getLocalName()) && SdkConstants.ANDROID_URI.equals(xmlAttribute.getNamespace())) ||
          (xmlTag.getSubTags().length == 0 && xmlTag.getAttributes().length == 1)) {
        toolsRemove(manifest, xmlTag);
      }
      else {
        toolsRemove(manifest, xmlAttribute);
      }
    }
  }

  static void toolsRemove(@NotNull XmlFile manifest, @NotNull XmlTag item) {
    addToolsAttribute(manifest, item, "node", "remove");
  }

  static void toolsRemove(@NotNull XmlFile manifest, @NotNull XmlAttribute item) {
    addToolsAttribute(manifest, item.getParent(), "remove", item.getName());
  }

  static void addToolsAttribute(@NotNull XmlFile manifest, @NotNull XmlTag item, @NotNull String attributeName, @NotNull String attributeValue) {
    if (attributeName.contains(":")) {
      throw new IllegalArgumentException("should not have namespace as it's always tools");
    }
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    SuppressLintIntentionAction.ensureNamespaceImported(manifest.getProject(), manifest, SdkConstants.TOOLS_URI);
    XmlTag parent = null;
    XmlTag[] manifestTags = new XmlTag[]{manifest.getRootTag()};
    for (XmlTag tag : getPath(item)) {
      XmlTag found = findTag(manifestTags, tag);
      if (found == null) {
        if (parent == null) {
          // we have not even been able to find the correct matching root xml tag
          Logger.getInstance(ManifestUtils.class).warn("can not root tag " + tag + " in xml file " + manifest);
          return;
        }
        found = parent.createChildTag(tag.getLocalName(), null, null, false); // we ignore namespace for tag names in manifest
        found = parent.addSubTag(found, true); // add it right away, or namespace will not work
        XmlAttribute nameAttribute = tag.getAttribute(SdkConstants.ATTR_NAME, SdkConstants.ANDROID_URI);
        if (nameAttribute != null) {
          found.setAttribute(nameAttribute.getLocalName(), nameAttribute.getNamespace(), nameAttribute.getValue());
        }
        if (tag == item) {
          found.setAttribute(attributeName, SdkConstants.TOOLS_URI ,attributeValue);
        }
      }
      else if (tag == item) {
        XmlAttribute attribute = found.getAttribute(attributeName, SdkConstants.TOOLS_URI);
        if (attribute == null) {
          found.setAttribute(attributeName, SdkConstants.TOOLS_URI, attributeValue);
        }
        else {
          found.setAttribute(attributeName, SdkConstants.TOOLS_URI, attribute.getValue() + "," + attributeValue);
        }
      }
      parent = found;
      manifestTags = found.getSubTags();
    }
  }

  @Nullable
  private static XmlTag findTag(@NotNull XmlTag[] manifestTags, @NotNull XmlTag tag) {
    XmlAttribute nameAttribute = tag.getAttribute(SdkConstants.ATTR_NAME, SdkConstants.ANDROID_URI);
    String name = nameAttribute == null ? null : nameAttribute.getValue();
    for (XmlTag xmlTag : manifestTags) {
      if (tag.getName().equals(xmlTag.getName())) {
        if (name != null) {
          XmlAttribute xmlAttribute = xmlTag.getAttribute(SdkConstants.ATTR_NAME, SdkConstants.ANDROID_URI);
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
  private static List<XmlTag> getPath(@NotNull XmlTag xmlTag) {
    XmlTag tag = xmlTag;
    List<XmlTag> path = new ArrayList<XmlTag>();
    do {
      path.add(tag);
      tag = tag.getParentTag();
    } while (tag != null);
    Collections.reverse(path);
    return path;
  }
}
