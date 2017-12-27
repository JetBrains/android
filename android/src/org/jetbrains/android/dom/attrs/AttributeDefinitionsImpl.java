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

import com.google.common.collect.Multimap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import com.intellij.xml.util.XmlUtil;
import com.intellij.xml.util.documentation.XmlDocumentationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.*;

public class AttributeDefinitionsImpl implements AttributeDefinitions {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.dom.attrs.AttributeDefinitionsImpl");

  //Used for parsing group of attributes, used heuristically to skip long comments before <eat-comment/>
  private static final int ATTR_GROUP_MAX_CHARACTERS = 40;

  private final Map<String, AttributeDefinition> myAttrs = new HashMap<>();
  private final Map<String, StyleableDefinitionImpl> myStyleables = new HashMap<>();

  private final Map<String, Map<String, Integer>> myEnumMap = new HashMap<>();
  private final AttributeDefinitions mySystemAttributeDefinitions;

  public AttributeDefinitionsImpl(@NotNull XmlFile... files) {
    mySystemAttributeDefinitions = null;
    for (XmlFile file : files) {
      addAttrsFromFile(file, null);
    }
  }

  public AttributeDefinitionsImpl(@Nullable AttributeDefinitions systemAttributeDefinitions, @NotNull Multimap<String, XmlFile> files) {
    mySystemAttributeDefinitions = systemAttributeDefinitions;
    for (Map.Entry<String, XmlFile> file : files.entries()) {
      addAttrsFromFile(file.getValue(), file.getKey());
    }
  }

  private void addAttrsFromFile(@NotNull XmlFile file, @Nullable String libraryName) {
    Map<StyleableDefinitionImpl, String[]> parentMap = new HashMap<>();
    XmlDocument document = file.getDocument();
    if (document == null) return;
    XmlTag rootTag = document.getRootTag();
    if (rootTag == null || !TAG_RESOURCES.equals(rootTag.getName())) return;

    String attrGroup = null;
    for (XmlTag tag : rootTag.getSubTags()) {
      String tagName = tag.getName();
      if (TAG_ATTR.equals(tagName)) {
        AttributeDefinition def = parseAttrTag(tag, null, libraryName);

        // Sets group for attribute, for example: sets "Button Styles" group for "buttonStyleSmall" attribute
        if (def != null) {
          def.setAttrGroup(attrGroup);
        }
      }
      else if (TAG_DECLARE_STYLEABLE.equals(tagName)) {
        StyleableDefinitionImpl def = parseDeclareStyleableTag(tag, parentMap, libraryName);
        // Only "Theme" Styleable has attribute groups
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
        if (newAttrGroup != null && newAttrGroup.length() <= ATTR_GROUP_MAX_CHARACTERS) {
          attrGroup = newAttrGroup;
        }
      }
    }

    for (Map.Entry<StyleableDefinitionImpl, String[]> entry : parentMap.entrySet()) {
      StyleableDefinitionImpl definition = entry.getKey();
      String[] parentNames = entry.getValue();
      for (String parentName : parentNames) {
        StyleableDefinitionImpl parent = getStyleableByName(parentName);
        if (parent != null) {
          definition.addParent(parent);
          parent.addChild(definition);
        }
        else {
          LOG.info("Found tag with unknown parent: " + parentName);
        }
      }
    }
  }

  @Nullable
  private AttributeDefinition parseAttrTag(XmlTag tag, @Nullable String parentStyleable, @Nullable String libraryName) {
    String name = tag.getAttributeValue(ATTR_NAME);
    if (name == null) {
      LOG.info("Found attr tag with no name: " + tag.getText());
      return null;
    }
    AttributeDefinition def = myAttrs.get(name);
    if (mySystemAttributeDefinitions != null && name.startsWith(ANDROID_NS_NAME_PREFIX)) {
      // Reference to a system attribute.
      if (def == null) {
        String unqualifiedName = name.substring(ANDROID_NS_NAME_PREFIX_LEN);
        def = mySystemAttributeDefinitions.getAttrDefByName(unqualifiedName);
        if (def != null) {
          def = def.cloneWithName(name);
          myAttrs.put(name, def);
        }
      }
      return def;
    }

    // Locally defined attribute.
    if (def == null) {
      def = new AttributeDefinition(name, libraryName, parentStyleable, Collections.emptySet());
      myAttrs.put(name, def);
    }

    List<AttributeFormat> parsedFormats;
    List<AttributeFormat> formats = new ArrayList<>();
    String format = tag.getAttributeValue(ATTR_FORMAT);
    if (format != null) {
      parsedFormats = parseAttrFormat(format);
      if (parsedFormats != null) formats.addAll(parsedFormats);
    }
    XmlTag[] values = tag.findSubTags(TAG_ENUM);
    if (values.length > 0) {
      formats.add(AttributeFormat.Enum);
    }
    else {
      values = tag.findSubTags(TAG_FLAG);
      if (values.length > 0) {
        formats.add(AttributeFormat.Flag);
      }
    }
    def.addFormats(formats);
    parseDocComment(tag, def, parentStyleable);
    parseAndAddValues(def, values);
    return def;
  }

  private static void parseDocComment(XmlTag tag, AttributeDefinition def, @Nullable String styleable) {
    PsiElement comment = XmlDocumentationProvider.findPreviousComment(tag);
    if (comment != null) {
      String docValue = XmlUtil.getCommentText((XmlComment)comment);
      if (docValue != null && !StringUtil.isEmpty(docValue)) {
        def.addDocValue(docValue, styleable);
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

  @Nullable
  private static List<AttributeFormat> parseAttrFormat(String formatString) {
    List<AttributeFormat> result = new ArrayList<>();
    String[] formats = formatString.split("\\|");
    for (String format : formats) {
      AttributeFormat attributeFormat;
      try {
        attributeFormat = AttributeFormat.valueOf(StringUtil.capitalize(format));
      }
      catch (IllegalArgumentException e) {
        return null;
      }
      result.add(attributeFormat);
    }
    return result;
  }

  private void parseAndAddValues(AttributeDefinition def, XmlTag[] values) {
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
          int intValue = (int) (long) Long.decode(strIntValue);
          def.addValueMapping(valueName, intValue);
          Map<String, Integer> value2Int = myEnumMap.get(def.getName());
          if (value2Int == null) {
            value2Int = new HashMap<>();
            myEnumMap.put(def.getName(), value2Int);
          }
          value2Int.put(valueName, intValue);
        }
        catch (NumberFormatException ignored) {
        }
      }
    }
  }

  private StyleableDefinitionImpl parseDeclareStyleableTag(@NotNull XmlTag tag,
                                                           @NotNull Map<StyleableDefinitionImpl, String[]> parentMap,
                                                           @Nullable String libraryName) {
    String name = tag.getAttributeValue(ATTR_NAME);
    if (name == null) {
      LOG.info("Found declare-styleable tag with no name: " + tag.getText());
      return null;
    }
    StyleableDefinitionImpl def = new StyleableDefinitionImpl(name);
    String parentNameAttributeValue = tag.getAttributeValue(ATTR_PARENT);
    if (parentNameAttributeValue != null) {
      String[] parentNames = parentNameAttributeValue.split("\\s+");
      parentMap.put(def, parentNames);
    }
    myStyleables.put(name, def);

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

    AttributeDefinition attr = parseAttrTag(tag, def.getName(), libraryName);
    if (attr != null) {
      def.addAttribute(attr);
    }
  }

  private void parseAndAddAttrGroups(XmlTag tag) {
    String attrGroup = null;
    for (XmlTag subTag : tag.getSubTags()) {
      String subTagName = subTag.getName();
      if (TAG_ATTR.equals(subTagName)) {
        AttributeDefinition def = myAttrs.get(subTag.getAttributeValue(ATTR_NAME));
        if (def != null) {
          def.setAttrGroup(attrGroup);
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
  public StyleableDefinitionImpl getStyleableByName(@NotNull String name) {
    return myStyleables.get(name);
  }

  @NotNull
  @Override
  public Set<String> getAttributeNames() {
    return myAttrs.keySet();
  }

  @Override
  @Nullable
  public AttributeDefinition getAttrDefByName(@NotNull String name) {
    return myAttrs.get(name);
  }

  @Nullable
  @Override
  public String getAttrGroupByName(@NotNull String name) {
    if (myAttrs.get(name) == null) {
      return null;
    }
    return myAttrs.get(name).getAttrGroup();
  }

  @NotNull
  public Map<String, Map<String, Integer>> getEnumMap() {
    return myEnumMap;
  }
}
