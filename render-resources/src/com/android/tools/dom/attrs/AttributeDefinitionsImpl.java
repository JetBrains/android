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
package com.android.tools.dom.attrs;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX_LEN;
import static com.android.SdkConstants.ATTR_FORMAT;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.TAG_ATTR;
import static com.android.SdkConstants.TAG_DECLARE_STYLEABLE;
import static com.android.SdkConstants.TAG_ENUM;
import static com.android.SdkConstants.TAG_FLAG;
import static com.android.SdkConstants.TAG_RESOURCES;

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleableResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.resources.base.CommentTrackingXmlPullParser;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * The default implementation of the {@link AttributeDefinitions} interface. Used to represent either all
 * attr and styleable resources or just the framework ones.
 */
public final class AttributeDefinitionsImpl implements AttributeDefinitions {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.dom.attrs.AttributeDefinitionsImpl");
  private static final Splitter PIPE_SPLITTER = Splitter.on('|').trimResults();

  @NotNull private final Map<ResourceReference, AttributeDefinition> myAttrs = new HashMap<>();
  @NotNull private final Map<ResourceReference, StyleableDefinitionImpl> myStyleables = new HashMap<>();

  @Nullable private final AttributeDefinitions myFrameworkAttributeDefinitions;

  private AttributeDefinitionsImpl(@Nullable AttributeDefinitions frameworkAttributeDefinitions) {
    myFrameworkAttributeDefinitions = frameworkAttributeDefinitions;
  }

  /**
   * Creates framework attribute definitions by parsing XML files defining them.
   *
   * @param files the files to parse
   * @return the framework attribute definitions
   */
  @NotNull
  public static AttributeDefinitions parseFrameworkFiles(@NotNull File... files) {
    AttributeDefinitionsImpl attributeDefinitions = new AttributeDefinitionsImpl(null);
    for (File file : files) {
      attributeDefinitions.addAttrsFromFile(file);
    }
    return attributeDefinitions;
  }

  /**
   * Creates application attribute definitions based on the given framework attribute definitions
   * and the application resource repository.
   *
   * @param frameworkAttributeDefinitions the framework attribute definitions
   * @param resources the application resource repository
   * @return the attribute definitions for both, application and framework attributes
   */
  @NotNull
  public static AttributeDefinitions create(@Nullable AttributeDefinitions frameworkAttributeDefinitions,
                                            @NotNull ResourceRepository resources) {
    AttributeDefinitionsImpl attributeDefinitions = new AttributeDefinitionsImpl(frameworkAttributeDefinitions);
    attributeDefinitions.initializeFromResourceRepository(resources);
    return attributeDefinitions;
  }

  private void initializeFromResourceRepository(@NotNull ResourceRepository resources) {
    for (ResourceNamespace namespace : resources.getNamespaces()) {
      Collection<ResourceItem> items = resources.getResources(namespace, ResourceType.ATTR).values();
      for (ResourceItem item : items) {
        ResourceValue resourceValue = item.getResourceValue();
        if (resourceValue instanceof AttrResourceValue) {
          createOrUpdateAttributeDefinition((AttrResourceValue)resourceValue, null);
        }
      }
    }

    for (ResourceNamespace namespace : resources.getNamespaces()) {
      Collection<ResourceItem> items = resources.getResources(namespace, ResourceType.STYLEABLE).values();
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
    if (!valueMappings.isEmpty()) {
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

  private void addAttrsFromFile(@NotNull File file) {
    try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
      CommentTrackingXmlPullParser parser = new CommentTrackingXmlPullParser(); // Parser for regular text XML.
      parser.setInput(stream, null);

      StyleableDefinitionImpl styleable = null;
      int event;
      do {
        event = parser.nextToken();
        int depth = parser.getDepth();
        switch (event) {
          case XmlPullParser.START_TAG: {
            String tagName = parser.getName();
            if (depth == 1) {
              if (!tagName.equals(TAG_RESOURCES)) {
                return;
              }
            }
            else if (depth > 1) {
              switch (tagName) {
                case TAG_ATTR:
                  processAttrTag(parser, file, parser.getLastComment(), parser.getAttrGroupComment(), styleable);
                  break;

                case TAG_DECLARE_STYLEABLE: {
                  if (styleable != null) {
                    LOG.info("Found nested declare-styleable tag at " + file.getAbsolutePath() + " line " + parser.getLineNumber());
                    break;
                  }

                  String styleableName = parser.getAttributeValue(null, ATTR_NAME);
                  if (styleableName == null) {
                    LOG.info("Found declare-styleable tag with no name at " + file.getAbsolutePath() + " line " + parser.getLineNumber());
                    break;
                  }

                  styleable = new StyleableDefinitionImpl(ResourceNamespace.ANDROID, styleableName);
                  myStyleables.put(styleable.getResourceReference(), styleable);
                  break;
                }
              }
            }
            break;
          }

          case XmlPullParser.END_TAG:
            if (parser.getName().equals(TAG_DECLARE_STYLEABLE)) {
              styleable = null;
            }
            break;
        }
      } while (event != XmlPullParser.END_DOCUMENT);
    } catch (IOException | XmlPullParserException e) {
      LOG.warn("Failed to parse " + file.getAbsolutePath(), e);
    }
  }

  private void processAttrTag(@NotNull XmlPullParser parser, @NotNull File file, @Nullable String precedingComment,
                              @Nullable String attrGroup, @Nullable StyleableDefinitionImpl parentStyleable)
        throws IOException, XmlPullParserException {
    String attrName = parser.getAttributeValue(null, ATTR_NAME);
    if (attrName == null) {
      LOG.info("Found attr tag with no name at " + file.getAbsolutePath() + " line " + parser.getLineNumber());
      return;
    }
    if (attrName.startsWith(ANDROID_NS_NAME_PREFIX)) {
      attrName = attrName.substring(ANDROID_NS_NAME_PREFIX_LEN);
    }
    if (attrName.indexOf(':') >= 0) {
      LOG.info("Found attr tag with an invalid name at " + file.getAbsolutePath() + " line " + parser.getLineNumber());
      return;
    }

    AttributeDefinition attrDef = myAttrs.get(ResourceReference.attr(ResourceNamespace.ANDROID, attrName));

    if (attrDef == null) {
      attrDef = new AttributeDefinition(ResourceNamespace.ANDROID, attrName, null, null);
      attrDef.setGroupName(attrGroup);

      myAttrs.put(attrDef.getResourceReference(), attrDef);
    }

    if (parentStyleable != null) {
      parentStyleable.addAttribute(attrDef);
    }

    Set<AttributeFormat> formats = EnumSet.noneOf(AttributeFormat.class);
    String format = parser.getAttributeValue(null, ATTR_FORMAT);
    if (format != null) {
      formats.addAll(parseAttrFormat(format));
    }

    Map<String, Integer> valueMappings = null;
    Map<String, String> valueDescriptions = null;
    String lastComment = null;
    int attrTagDepth = parser.getDepth();
    int event;
    do {
      event = parser.nextToken();
      switch (event) {
        case XmlPullParser.START_TAG: {
          String tagName = parser.getName();
          if (tagName.equals(TAG_ENUM) && !formats.contains(AttributeFormat.FLAGS)) {
            formats.add(AttributeFormat.ENUM);
          }
          else if (tagName.equals(TAG_FLAG) && !formats.contains(AttributeFormat.ENUM)) {
            formats.add(AttributeFormat.FLAGS);
          }
          String valueName = parser.getAttributeValue(null, ATTR_NAME);
          if (valueName == null) {
            LOG.info("Unknown value for tag: " + tagName);
            continue;
          }

          String strIntValue = parser.getAttributeValue(null, ATTR_VALUE);
          Integer intValue = strIntValue == null ? null : decodeIntegerValue(strIntValue);
          if (valueMappings == null) {
            valueMappings = new HashMap<>();
          }
          valueMappings.putIfAbsent(valueName, intValue);
          if (lastComment != null) {
            if (valueDescriptions == null) {
              valueDescriptions = new HashMap<>();
            }
            valueDescriptions.putIfAbsent(valueName, lastComment);
          }
          lastComment = null;
          break;
        }

        case XmlPullParser.COMMENT: {
          String commentText = parser.getText().trim();
          if (!isEmptyOrAsciiArt(commentText)) {
            lastComment = commentText;
          }
          break;
        }
      }
    } while (event != XmlPullParser.END_TAG || parser.getDepth() > attrTagDepth);

    attrDef.addFormats(formats);
    if (precedingComment != null) {
      attrDef.setDescription(precedingComment, parentStyleable == null ? null : parentStyleable.getResourceReference());
    }
    if (valueMappings != null) {
      attrDef.setValueMappings(valueMappings);
    }
    if (valueDescriptions != null) {
      attrDef.setValueDescriptions(valueDescriptions);
    }
  }

  @Nullable
  private static Integer decodeIntegerValue(@NotNull String value) {
    try {
      // Integer.decode cannot handle "ffffffff", see JDK issue 6624867.
      return Long.decode(value).intValue();
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  private static boolean isEmptyOrAsciiArt(@NotNull String commentText) {
    return commentText.isEmpty() || commentText.charAt(0) == '*' || commentText.charAt(0) == '=';
  }

  @NotNull
  private static Set<AttributeFormat> parseAttrFormat(@NotNull String formatString) {
    List<String> formats = PIPE_SPLITTER.splitToList(formatString);
    Set<AttributeFormat> result = EnumSet.noneOf(AttributeFormat.class);
    for (String format : formats) {
      AttributeFormat attributeFormat = AttributeFormat.fromXmlName(format);
      if (attributeFormat != null) {
        result.add(attributeFormat);
      }
    }
    return result;
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
