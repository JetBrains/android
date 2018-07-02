/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.dom.attrs;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.google.common.collect.Multimap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import com.intellij.xml.util.documentation.XmlDocumentationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;

public class AttributeDefinitionsImpl implements AttributeDefinitions {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.dom.attrs.AttributeDefinitionsImpl");
  private static final Pattern WHITESPACES = Pattern.compile("\\s+");

  // Used for parsing group of attributes, used heuristically to skip long comments before <eat-comment/>
  private static final int ATTR_GROUP_MAX_CHARACTERS = 40;

  private final Map<ResourceReference, AttributeDefinition> myAttrs = new HashMap<>();
  private final Map<ResourceReference, StyleableDefinitionImpl> myStyleables = new HashMap<>();

  private final AttributeDefinitions myFrameworkAttributeDefinitions;

  public AttributeDefinitionsImpl(@NotNull ResourceNamespace namespace, @NotNull XmlFile... files) {
    assert namespace == ResourceNamespace.ANDROID;
    myFrameworkAttributeDefinitions = null;
    for (XmlFile file : files) {
      addAttrsFromFile(file, namespace, null);
    }
  }

  public AttributeDefinitionsImpl(@Nullable AttributeDefinitions frameworkAttributeDefinitions, @NotNull Multimap<String, XmlFile> files) {
    myFrameworkAttributeDefinitions = frameworkAttributeDefinitions;
    for (Map.Entry<String, XmlFile> file : files.entries()) {
      addAttrsFromFile(file.getValue(), ResourceNamespace.TODO(), file.getKey());
    }
  }

  private void addAttrsFromFile(@NotNull XmlFile file,
                                @NotNull ResourceNamespace namespace,
                                @Nullable String libraryName) {
    Map<StyleableDefinitionImpl, List<ResourceReference>> parentMap = new HashMap<>();
    XmlDocument document = file.getDocument();
    if (document == null) return;
    XmlTag rootTag = document.getRootTag();
    if (rootTag == null || !TAG_RESOURCES.equals(rootTag.getName())) return;

    String attrGroup = null;
    for (XmlTag tag : rootTag.getSubTags()) {
      String tagName = tag.getName();
      if (TAG_ATTR.equals(tagName)) {
        AttributeDefinition def = parseAttrTag(tag, namespace, null, libraryName);

        // Sets group for attribute, for example: sets "Button Styles" group for "buttonStyleSmall" attribute
        if (def != null) {
          def.setAttrGroup(attrGroup);
        }
      }
      else if (TAG_DECLARE_STYLEABLE.equals(tagName)) {
        StyleableDefinitionImpl def = parseDeclareStyleableTag(tag, parentMap, namespace, libraryName);
        // Only "Theme" Styleable has attribute groups.
        if (def != null && def.getName().equals("Theme")) {
          parseAndAddAttrGroups(tag, namespace);
        }
      }
      else if (TAG_EAT_COMMENT.equals(tagName)) {
        // The framework attribute file follows a special convention where related attributes are grouped together,
        // and there is always a set of comments that indicate these sections which look like this:
        //     <!-- =========== -->
        //     <!-- Text styles -->
        //     <!-- =========== -->
        //     <eat-comment />
        // These section headers are always immediately followed by an <eat-comment>,
        // so to identify these we just look for <eat-comments>, and then we look for the comment within the block that isn't ascii art.
        String newAttrGroup = getCommentBeforeEatComment(tag);

        // Not all <eat-comment /> sections are actually attribute headers, some are comments.
        // We identify these by looking at the line length; category comments are short, and descriptive comments are longer
        if (newAttrGroup != null && newAttrGroup.length() <= ATTR_GROUP_MAX_CHARACTERS) {
          attrGroup = newAttrGroup;
          if (attrGroup.endsWith(".")) {
            attrGroup = attrGroup.substring(0, attrGroup.length() - 1);
          }
        }
      }
    }

    for (Map.Entry<StyleableDefinitionImpl, List<ResourceReference>> entry : parentMap.entrySet()) {
      StyleableDefinitionImpl definition = entry.getKey();
      List<ResourceReference> parents = entry.getValue();
      for (ResourceReference parentRef : parents) {
        StyleableDefinitionImpl parent = (StyleableDefinitionImpl)getStyleableDefinition(parentRef);
        if (parent != null) {
          definition.addParent(parent);
          parent.addChild(definition);
        }
        else {
          LOG.info("Found tag with unknown parent: " + parentRef.getQualifiedName());
        }
      }
    }
  }

  @Nullable
  private AttributeDefinition parseAttrTag(@NotNull XmlTag tag,
                                           @NotNull ResourceNamespace namespace,
                                           @Nullable ResourceReference parentStyleable,
                                           @Nullable String libraryName) {
    String name = tag.getAttributeValue(ATTR_NAME);
    if (name == null) {
      LOG.info("Found attr tag with no name: " + tag.getText());
      return null;
    }
    ResourceReference attrRef = getResourceReference(name, namespace, ResourceType.ATTR, tag);
    if (attrRef == null) {
      LOG.info("Found attr tag with an invalid name: " + tag.getText());
      return null;
    }
    AttributeDefinition def = myAttrs.get(attrRef);
    if (myFrameworkAttributeDefinitions != null && name.startsWith(ANDROID_NS_NAME_PREFIX)) {
      // Reference to a framework attribute.
      if (def == null) {
        def = myFrameworkAttributeDefinitions.getAttrDefinition(attrRef);
        if (def != null) {
          def = def.cloneWithQualifiedName();
          myAttrs.put(attrRef, def);
        }
      }
      return def;
    }

    // Locally defined attribute.
    if (def == null) {
      def = new AttributeDefinition(namespace, name, libraryName, parentStyleable, Collections.emptySet());
      myAttrs.put(def.getResourceReference(), def);
    }

    List<AttributeFormat> parsedFormats;
    List<AttributeFormat> formats = new ArrayList<>();
    String format = tag.getAttributeValue(ATTR_FORMAT);
    if (format != null) {
      parsedFormats = parseAttrFormat(format);
      formats.addAll(parsedFormats);
    }
    XmlTag[] values = tag.findSubTags(TAG_ENUM);
    if (values.length > 0) {
      formats.add(AttributeFormat.ENUM);
    }
    else {
      values = tag.findSubTags(TAG_FLAG);
      if (values.length > 0) {
        formats.add(AttributeFormat.FLAGS);
      }
    }
    def.addFormats(formats);
    parseDocComment(tag, def, parentStyleable);
    parseAndAddValues(def, values);
    return def;
  }

  @Nullable
  private static ResourceReference getResourceReference(@NotNull String name, @NotNull ResourceNamespace defaultNamespace,
                                                        @NotNull ResourceType resourceType, @NotNull XmlTag tag) {
    if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
      return new ResourceReference(ResourceNamespace.ANDROID, resourceType, name.substring(ANDROID_NS_NAME_PREFIX_LEN));
    }
    int colonPos = name.indexOf(':');
    if (colonPos < 0) {
      return new ResourceReference(defaultNamespace, resourceType, name);
    }
    String prefix = name.substring(0, colonPos);
    ResourceNamespace namespace = ResourceNamespace.fromNamespacePrefix(prefix, defaultNamespace, p -> tag.getNamespaceByPrefix(p));
    return namespace == null ? null : new ResourceReference(namespace, resourceType, name.substring(colonPos + 1));
  }

  private static void parseDocComment(XmlTag tag, AttributeDefinition def, @Nullable ResourceReference styleable) {
    PsiElement comment = XmlDocumentationProvider.findPreviousComment(tag);
    if (comment != null) {
      String docValue = XmlUtil.getCommentText((XmlComment)comment);
      if (docValue != null) {
        docValue = docValue.trim();
        if (!StringUtil.isEmpty(docValue)) {
          def.addDocValue(docValue, styleable);
        }
      }
    }
  }

  @Nullable
  private static String getCommentBeforeEatComment(XmlTag tag) {
    PsiElement comment = XmlDocumentationProvider.findPreviousComment(tag);
    for (int i = 0; i < 5; ++i) {
      if (comment == null) {
        break;
      }
      String value = StringUtil.trim(XmlUtil.getCommentText((XmlComment)comment));

      //  This check is there to ignore "formatting" comments like the first and third lines in
      //  <!-- ============== -->
      //  <!-- Generic styles -->
      //  <!-- ============== -->
      if (!StringUtil.isEmpty(value) && value.charAt(0) != '*' && value.charAt(0) != '=') {
        return value;
      }
      comment = XmlDocumentationProvider.findPreviousComment(comment.getPrevSibling());
    }
    return null;
  }

  @NotNull
  private static List<AttributeFormat> parseAttrFormat(String formatString) {
    String[] formats = formatString.split("\\|");
    List<AttributeFormat> result = new ArrayList<>(formats.length);
    for (String format : formats) {
      AttributeFormat attributeFormat = AttributeFormat.fromXmlName(format);
      if (attributeFormat != null) {
        result.add(attributeFormat);
      }
    }
    return result;
  }

  private static void parseAndAddValues(AttributeDefinition def, XmlTag[] values) {
    for (XmlTag value : values) {
      String valueName = value.getAttributeValue(ATTR_NAME);
      if (valueName == null) {
        LOG.info("Unknown value for tag: " + value.getText());
        continue;
      }

      def.addValue(valueName);
      PsiElement comment = XmlDocumentationProvider.findPreviousComment(value);
      if (comment != null) {
        String docValue = XmlUtil.getCommentText((XmlComment)comment);
        if (!StringUtil.isEmpty(docValue)) {
          def.addValueDoc(valueName, docValue);
        }
      }

      String strIntValue = value.getAttributeValue(ATTR_VALUE);
      if (strIntValue != null) {
        try {
          // Integer.decode cannot handle "ffffffff", see JDK issue 6624867
          int intValue = Long.decode(strIntValue).intValue();
          def.addValueMapping(valueName, intValue);
        }
        catch (NumberFormatException ignored) {
        }
      }
    }
  }

  @Nullable
  private StyleableDefinitionImpl parseDeclareStyleableTag(@NotNull XmlTag tag,
                                                           @NotNull Map<StyleableDefinitionImpl, List<ResourceReference>> parentMap,
                                                           @NotNull ResourceNamespace namespace,
                                                           @Nullable String libraryName) {
    String name = tag.getAttributeValue(ATTR_NAME);
    if (name == null) {
      LOG.info("Found declare-styleable tag with no name: " + tag.getText());
      return null;
    }
    StyleableDefinitionImpl def = new StyleableDefinitionImpl(namespace, name);
    String parentNameAttributeValue = tag.getAttributeValue(ATTR_PARENT);
    if (parentNameAttributeValue != null) {
      String[] parentNames = parentNameAttributeValue.split("\\s+");
      List<ResourceReference> parents = new ArrayList<>(parentNames.length);
      for (String parentName: parentNames) {
        ResourceReference parent = getResourceReference(parentName, namespace, ResourceType.STYLEABLE, tag);
        if (parent != null) {
          parents.add(parent);
        }
      }

      parentMap.put(def, parents);
    }
    myStyleables.put(def.getResourceReference(), def);

    for (XmlTag subTag : tag.findSubTags(TAG_ATTR)) {
      parseStyleableAttr(def, subTag, libraryName);
    }
    return def;
  }

  private void parseStyleableAttr(@NotNull StyleableDefinitionImpl def, @NotNull XmlTag tag, @Nullable String libraryName) {
    String name = tag.getAttributeValue(ATTR_NAME);
    if (name == null) {
      LOG.info("Found attr tag with no name: " + tag.getText());
      return;
    }

    ResourceReference styleable = def.getResourceReference();
    AttributeDefinition attr = parseAttrTag(tag, styleable.getNamespace(), styleable, libraryName);
    if (attr != null) {
      def.addAttribute(attr);
    }
  }

  private void parseAndAddAttrGroups(@NotNull XmlTag tag, @NotNull ResourceNamespace namespace) {
    String attrGroup = null;
    for (XmlTag subTag : tag.getSubTags()) {
      String subTagName = subTag.getName();
      if (TAG_ATTR.equals(subTagName)) {
        String attrName = subTag.getAttributeValue(ATTR_NAME);
        if (attrName != null) {
          ResourceReference attrRef = getResourceReference(attrName, namespace, ResourceType.ATTR, tag);
          if (attrRef != null) {
            AttributeDefinition def = myAttrs.get(attrRef);
            if (def != null) {
              def.setAttrGroup(attrGroup);
            }
          }
        }
      }
      else if (TAG_EAT_COMMENT.equals(subTagName)) {
        String newAttrGroup = getCommentBeforeEatComment(subTag);
        if (newAttrGroup != null && newAttrGroup.length() <= ATTR_GROUP_MAX_CHARACTERS) {
          attrGroup = newAttrGroup;
        }
      }
    }
  }

  @Override
  @Nullable
  public StyleableDefinition getStyleableDefinition(@NotNull ResourceReference styleable) {
    if (myFrameworkAttributeDefinitions != null && styleable.getNamespace().equals(ResourceNamespace.ANDROID)) {
      return myFrameworkAttributeDefinitions.getStyleableDefinition(styleable);
    }
    return myStyleables.get(styleable);
  }

  @Deprecated
  @Override
  @Nullable
  public StyleableDefinition getStyleableByName(@NotNull String name) {
    StyleableDefinition styleable = getStyleableDefinition(ResourceReference.styleable(ResourceNamespace.TODO(), name));
    if (styleable == null) {
      styleable = getStyleableDefinition(ResourceReference.styleable(ResourceNamespace.ANDROID, name));
    }
    return styleable;
  }

  @Override
  @NotNull
  public Set<ResourceReference> getAttrs() {
    return myAttrs.keySet();
  }

  @Nullable
  @Override
  public AttributeDefinition getAttrDefinition(@NotNull ResourceReference attr) {
    if (myFrameworkAttributeDefinitions != null && attr.getNamespace().equals(ResourceNamespace.ANDROID)) {
      return myFrameworkAttributeDefinitions.getAttrDefinition(attr);
    }
    return myAttrs.get(attr);
  }

  @Deprecated
  @Override
  @Nullable
  public AttributeDefinition getAttrDefByName(@NotNull String name) {
    ResourceReference attr;
    if (myFrameworkAttributeDefinitions == null) {
      // This object represents framework attributes.
      attr = ResourceReference.attr(ResourceNamespace.ANDROID, name);
    } else {
      // This object represents all attributes.
      attr = name.startsWith(ANDROID_NS_NAME_PREFIX) ?
             ResourceReference.attr(ResourceNamespace.ANDROID, name.substring(ANDROID_NS_NAME_PREFIX_LEN)) :
             ResourceReference.attr(ResourceNamespace.TODO(), name);
    }
    return getAttrDefinition(attr);
  }

  @Override
  @Nullable
  public String getAttrGroup(@NotNull ResourceReference attr) {
    AttributeDefinition attributeDefinition = myAttrs.get(attr);
    return attributeDefinition == null ? null : attributeDefinition.getAttrGroup();
  }
}
