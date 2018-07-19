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

import com.android.ide.common.rendering.api.*;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
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

import static com.android.SdkConstants.*;

public final class AttributeDefinitionsImpl implements AttributeDefinitions {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.dom.attrs.AttributeDefinitionsImpl");
  private static final Splitter PIPE_SPLITTER = Splitter.on('|').trimResults();

  // Used for parsing group of attributes, used heuristically to skip long comments before <eat-comment/>.
  private static final int ATTR_GROUP_MAX_CHARACTERS = 40;

  @NotNull private final Map<ResourceReference, AttributeDefinition> myAttrs = new HashMap<>();
  @NotNull private final Map<ResourceReference, StyleableDefinitionImpl> myStyleables = new HashMap<>();

  @Nullable private final AttributeDefinitions myFrameworkAttributeDefinitions;

  private AttributeDefinitionsImpl(@Nullable AttributeDefinitions frameworkAttributeDefinitions) {
    myFrameworkAttributeDefinitions = frameworkAttributeDefinitions;
  }

  public static AttributeDefinitions parseFrameworkFiles(@NotNull XmlFile... files) {
    AttributeDefinitionsImpl attributeDefinitions = new AttributeDefinitionsImpl(null);
    for (XmlFile file : files) {
      attributeDefinitions.addAttrsFromFile(file);
    }
    return attributeDefinitions;
  }

  public static AttributeDefinitions create(@Nullable AttributeDefinitions frameworkAttributeDefinitions,
                                            @NotNull AbstractResourceRepository resources) {
    AttributeDefinitionsImpl attributeDefinitions = new AttributeDefinitionsImpl(frameworkAttributeDefinitions);
    attributeDefinitions.initializeFromResourceRepository(resources);
    return attributeDefinitions;
  }

  private void initializeFromResourceRepository(@NotNull AbstractResourceRepository resources) {
    for (ResourceNamespace namespace : resources.getNamespaces()) {
      List<ResourceItem> items = resources.getResourceItems(namespace, ResourceType.ATTR);
      for (ResourceItem item : items) {
        ResourceValue resourceValue = item.getResourceValue();
        if (resourceValue instanceof AttrResourceValue) {
          createOrUpdateAttributeDefinition((AttrResourceValue)resourceValue, null);
        }
      }
    }

    for (ResourceNamespace namespace : resources.getNamespaces()) {
      List<ResourceItem> items = resources.getResourceItems(namespace, ResourceType.STYLEABLE);
      for (ResourceItem item : items) {
        ResourceValue resourceValue = item.getResourceValue();
        if (resourceValue instanceof StyleableResourceValue) {
          StyleableResourceValue styleableValue = (StyleableResourceValue)resourceValue;
          ResourceReference reference = styleableValue.asReference();
          StyleableDefinitionImpl styleable =
              myStyleables.computeIfAbsent(reference, (ref) -> new StyleableDefinitionImpl(namespace, styleableValue.getName()));
          for (AttrResourceValue attrValue : styleableValue.getAllAttributes()) {
            createOrUpdateAttributeDefinition(attrValue, styleable);
          }
        }
      }
    }
  }

  private void createOrUpdateAttributeDefinition(@NotNull AttrResourceValue attrValue, @Nullable StyleableDefinitionImpl parentStyleable) {
    ResourceReference attrRef = attrValue.asReference();
    AttributeDefinition attr = myAttrs.get(attrRef);
    if (attr == null) {
      if (myFrameworkAttributeDefinitions != null && attrValue.getNamespace().equals(ResourceNamespace.ANDROID)) {
        attr = myFrameworkAttributeDefinitions.getAttrDefinition(attrRef);
        if (attr != null) {
          // Copy the framework attribute definition to be able to add parent styleables.
          attr = new AttributeDefinition(attr);
        }
      }

      if (attr == null) {
        attr = new AttributeDefinition(attrValue.getNamespace(), attrValue.getName(), attrValue.getLibraryName(), null);
      }

      myAttrs.put(attrRef, attr);
    }

    attr.addFormats(attrValue.getFormats());

    Map<String, Integer> valueMappings = attrValue.getAttributeValues();
    if (valueMappings != null && !valueMappings.isEmpty()) {
      attr.setValueMappings(valueMappings);
      Map<String, String> valueDescriptions = Maps.newHashMapWithExpectedSize(valueMappings.size());
      for (String value : valueMappings.keySet()) {
        String description = attrValue.getValueDescription(value);
        if (description != null) {
          valueDescriptions.put(value, description);
        }
      }
      if (!valueDescriptions.isEmpty()) {
        attr.setValueDescriptions(valueDescriptions);
      }
    }

    String description = attrValue.getDescription();
    if (description != null) {
      attr.setDescription(description, parentStyleable == null ? null : parentStyleable.getResourceReference());
    }
    String groupName = attrValue.getGroupName();
    if (groupName != null) {
      attr.setGroupName(groupName);
    }

    if (parentStyleable != null) {
      parentStyleable.addAttribute(attr);
    }
  }

  private void addAttrsFromFile(@NotNull XmlFile file) {
    XmlDocument document = file.getDocument();
    if (document == null) return;
    XmlTag rootTag = document.getRootTag();
    if (rootTag == null || !TAG_RESOURCES.equals(rootTag.getName())) return;

    String attrGroup = null;
    for (XmlTag tag : rootTag.getSubTags()) {
      String tagName = tag.getName();
      if (TAG_ATTR.equals(tagName)) {
        AttributeDefinition def = parseAttrTag(tag, null);

        // Sets group for attribute, for example: sets "Button Styles" group for "buttonStyleSmall" attribute.
        if (def != null) {
          def.setGroupName(attrGroup);
        }
      }
      else if (TAG_DECLARE_STYLEABLE.equals(tagName)) {
        StyleableDefinitionImpl def = parseDeclareStyleableTag(tag);
        // Only "Theme" Styleable has attribute groups.
        if (def != null && def.getName().equals("Theme")) {
          parseAndAddAttrGroups(tag);
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
        if (newAttrGroup != null && newAttrGroup.length() <= ATTR_GROUP_MAX_CHARACTERS && !newAttrGroup.startsWith("TODO:")) {
          attrGroup = newAttrGroup;
          if (attrGroup.endsWith(".")) {
            attrGroup = attrGroup.substring(0, attrGroup.length() - 1);
          }
        }
      }
    }
  }

  @Nullable
  private AttributeDefinition parseAttrTag(@NotNull XmlTag tag,@Nullable ResourceReference parentStyleable) {
    String name = tag.getAttributeValue(ATTR_NAME);
    if (name == null) {
      LOG.info("Found attr tag with no name: " + tag.getText());
      return null;
    }
    ResourceReference attrRef = getAttrReference(name);
    if (attrRef == null) {
      LOG.info("Found attr tag with an invalid name: " + tag.getText());
      return null;
    }

    AttributeDefinition attrDef = myAttrs.get(attrRef);

    if (attrDef == null) {
      attrDef = new AttributeDefinition(ResourceNamespace.ANDROID, name, null, null);
      myAttrs.put(attrDef.getResourceReference(), attrDef);
    }

    List<AttributeFormat> parsedFormats;
    List<AttributeFormat> formats = new ArrayList<>();
    String format = tag.getAttributeValue(ATTR_FORMAT);
    if (format != null) {
      parsedFormats = parseAttrFormat(format);
      formats.addAll(parsedFormats);
    }
    XmlTag[] values = tag.findSubTags(TAG_ENUM);
    if (values.length != 0) {
      formats.add(AttributeFormat.ENUM);
    }
    else {
      values = tag.findSubTags(TAG_FLAG);
      if (values.length != 0) {
        formats.add(AttributeFormat.FLAGS);
      }
    }
    attrDef.addFormats(formats);
    String description = parseDocComment(tag);
    if (description != null) {
      attrDef.setDescription(description, parentStyleable);
    }
    Map<String, Integer> valueMappings = new HashMap<>();
    Map<String, String> valueDescriptions = new HashMap<>();
    parseValues(values, valueMappings, valueDescriptions);
    if (!valueMappings.isEmpty()) {
      attrDef.setValueMappings(valueMappings);
    }
    if (!valueDescriptions.isEmpty()) {
      attrDef.setValueDescriptions(valueDescriptions);
    }
    return attrDef;
  }

  @Nullable
  private static ResourceReference getAttrReference(@NotNull String name) {
    if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
      return ResourceReference.attr(ResourceNamespace.ANDROID, name.substring(ANDROID_NS_NAME_PREFIX_LEN));
    }
    int colonPos = name.indexOf(':');
    if (colonPos < 0) {
      return ResourceReference.attr(ResourceNamespace.ANDROID, name);
    }
    return null;
  }

  @Nullable
  private static String parseDocComment(@NotNull XmlTag tag) {
    PsiElement comment = XmlDocumentationProvider.findPreviousComment(tag);
    if (comment != null) {
      String docValue = XmlUtil.getCommentText((XmlComment)comment);
      if (docValue != null) {
        docValue = docValue.trim();
        if (!docValue.isEmpty()) {
          return docValue;
        }
      }
    }
    return null;
  }

  @Nullable
  private static String getCommentBeforeEatComment(@NotNull XmlTag tag) {
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
  private static List<AttributeFormat> parseAttrFormat(@NotNull String formatString) {
    List<String> formats = PIPE_SPLITTER.splitToList(formatString);
    List<AttributeFormat> result = new ArrayList<>(formats.size());
    for (String format : formats) {
      AttributeFormat attributeFormat = AttributeFormat.fromXmlName(format);
      if (attributeFormat != null) {
        result.add(attributeFormat);
      }
    }
    return result;
  }

  private static void parseValues(@NotNull XmlTag[] values, @NotNull Map<String, Integer> valueMappings,
                                  @NotNull Map<String, String> valueDescriptions) {
    for (XmlTag value : values) {
      String valueName = value.getAttributeValue(ATTR_NAME);
      if (valueName == null) {
        LOG.info("Unknown value for tag: " + value.getText());
        continue;
      }

      Integer intValue = null;
      String strIntValue = value.getAttributeValue(ATTR_VALUE);
      if (strIntValue != null) {
        try {
          // Integer.decode cannot handle "ffffffff", see JDK issue 6624867.
          intValue = Long.decode(strIntValue).intValue();
        }
        catch (NumberFormatException ignored) {
        }
      }
      valueMappings.put(valueName, intValue);

      PsiElement comment = XmlDocumentationProvider.findPreviousComment(value);
      if (comment != null) {
        String description = XmlUtil.getCommentText((XmlComment)comment);
        if (description != null) {
          description = description.trim();
          if (!description.isEmpty()) {
            valueDescriptions.put(valueName, description);
          }
        }
      }
    }
  }

  @Nullable
  private StyleableDefinitionImpl parseDeclareStyleableTag(@NotNull XmlTag tag) {
    String name = tag.getAttributeValue(ATTR_NAME);
    if (name == null) {
      LOG.info("Found declare-styleable tag with no name: " + tag.getText());
      return null;
    }
    StyleableDefinitionImpl def = new StyleableDefinitionImpl(ResourceNamespace.ANDROID, name);
    myStyleables.put(def.getResourceReference(), def);

    for (XmlTag subTag : tag.findSubTags(TAG_ATTR)) {
      parseStyleableAttr(def, subTag);
    }
    return def;
  }

  private void parseStyleableAttr(@NotNull StyleableDefinitionImpl styleableDef, @NotNull XmlTag tag) {
    String name = tag.getAttributeValue(ATTR_NAME);
    if (name == null) {
      LOG.info("Found attr tag with no name: " + tag.getText());
      return;
    }

    ResourceReference styleable = styleableDef.getResourceReference();
    AttributeDefinition attr = parseAttrTag(tag, styleable);
    if (attr != null) {
      styleableDef.addAttribute(attr);
    }
  }

  private void parseAndAddAttrGroups(@NotNull XmlTag tag) {
    String attrGroup = null;
    for (XmlTag subTag : tag.getSubTags()) {
      String subTagName = subTag.getName();
      if (TAG_ATTR.equals(subTagName)) {
        String attrName = subTag.getAttributeValue(ATTR_NAME);
        if (attrName != null) {
          ResourceReference attrRef = getAttrReference(attrName);
          if (attrRef == null) {
            LOG.info("Found attr tag with an invalid name: " + tag.getText());
            continue;
          }
          AttributeDefinition def = myAttrs.get(attrRef);
          if (def != null) {
            def.setGroupName(attrGroup);
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
    AttributeDefinition attributeDefinition = myAttrs.get(attr);
    if (attributeDefinition == null && myFrameworkAttributeDefinitions != null && attr.getNamespace().equals(ResourceNamespace.ANDROID)) {
      return myFrameworkAttributeDefinitions.getAttrDefinition(attr);
    }
    return attributeDefinition;
  }

  @Deprecated
  @Override
  @Nullable
  public AttributeDefinition getAttrDefByName(@NotNull String name) {
    ResourceReference attr;
    if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
      attr = ResourceReference.attr(ResourceNamespace.ANDROID, name.substring(ANDROID_NS_NAME_PREFIX_LEN));
    }
    else if (myFrameworkAttributeDefinitions == null) {
      // This object represents framework attributes only.
      attr = ResourceReference.attr(ResourceNamespace.ANDROID, name);
    }
    else {
      // This object represents all attributes.
      attr = ResourceReference.attr(ResourceNamespace.TODO(), name);
    }
    return getAttrDefinition(attr);
  }

  @Override
  @Nullable
  public String getAttrGroup(@NotNull ResourceReference attr) {
    AttributeDefinition attributeDefinition = getAttrDefinition(attr);
    return attributeDefinition == null ? null : attributeDefinition.getGroupName();
  }
}
