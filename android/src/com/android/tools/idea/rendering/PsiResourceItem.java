/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ValueXmlHelper;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.google.common.base.Splitter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

import static com.android.SdkConstants.*;
import static com.android.ide.common.resources.ResourceResolver.*;

public class PsiResourceItem extends ResourceItem {
  private final XmlTag myTag;
  private PsiFile myFile;

  PsiResourceItem(@NonNull String name, @NonNull ResourceType type, @Nullable XmlTag tag, @NonNull PsiFile file) {
    super(name, type, null);
    myTag = tag;
    myFile = file;
  }

  @Override
  public FolderConfiguration getConfiguration() {
    PsiResourceFile source = (PsiResourceFile)super.getSource();

    // Temporary safety workaround
    if (source == null) {
      if (myFile != null) {
        PsiDirectory parent = myFile.getParent();
        if (parent != null) {
          String name = parent.getName();
          FolderConfiguration configuration = FolderConfiguration.getConfigForFolder(name);
          if (configuration != null) {
            return configuration;
          }
        }
      }

      String qualifiers = getQualifiers();
      if (qualifiers.isEmpty()) {
        return new FolderConfiguration();
      }
      FolderConfiguration fromQualifiers = FolderConfiguration.getConfigFromQualifiers(Splitter.on('-').split(qualifiers));
      if (fromQualifiers == null) {
        return new FolderConfiguration();
      }
      return fromQualifiers;
    }
    return source.getFolderConfiguration();
  }

  @Nullable
  @Override
  public ResourceFile getSource() {
    ResourceFile source = super.getSource();

    // Temporary safety workaround
    if (source == null && myFile != null && myFile.getParent() != null) {
      PsiDirectory parent = myFile.getParent();
      if (parent != null) {
        String name = parent.getName();
        ResourceFolderType folderType = ResourceFolderType.getFolderType(name);
        FolderConfiguration configuration = FolderConfiguration.getConfigForFolder(name);
        int index = name.indexOf('-');
        String qualifiers = index == -1 ? "" : name.substring(index + 1);
        source = new PsiResourceFile(myFile, Collections.<ResourceItem>singletonList(this), qualifiers, folderType,
                                     configuration);
        setSource(source);
      }
    }

    return source;
  }

  @Nullable
  @Override
  public ResourceValue getResourceValue(boolean isFrameworks) {
    if (mResourceValue == null) {
      //noinspection VariableNotUsedInsideIf
      if (myTag == null) {
        // Density based resource value?
        ResourceType type = getType();
        Density density = type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP ? getFolderDensity() : null;
        if (density != null) {
          mResourceValue = new DensityBasedResourceValue(type, getName(), getSource().getFile().getAbsolutePath(), density, isFrameworks);
        } else {
          mResourceValue = new ResourceValue(type, getName(), getSource().getFile().getAbsolutePath(), isFrameworks);
        }
      } else {
        mResourceValue = parseXmlToResourceValue(isFrameworks);
      }
    }

    return mResourceValue;
  }

  @Nullable
  private Density getFolderDensity() {
    FolderConfiguration configuration = getConfiguration();
    if (configuration != null) {
      DensityQualifier densityQualifier = configuration.getDensityQualifier();
      if (densityQualifier != null) {
        return densityQualifier.getValue();
      }
    }
    return null;
  }

  @Nullable
  private ResourceValue parseXmlToResourceValue(boolean isFrameworks) {
    assert myTag != null;

    if (!myTag.isValid()) {
      return null;
    }

    ResourceType type = getType();
    String name = getName();

    ResourceValue value;
    switch (type) {
      case STYLE:
        String parent = getAttributeValue(myTag, ATTR_PARENT);
        value = parseStyleValue(new StyleResourceValue(type, name, parent, isFrameworks));
        break;
      case DECLARE_STYLEABLE:
        value = parseDeclareStyleable(new DeclareStyleableResourceValue(type, name, isFrameworks));
        break;
      case ATTR:
        value = parseAttrValue(new AttrResourceValue(type, name, isFrameworks));
        break;
      case ARRAY:
        value = parseArrayValue(new ArrayResourceValue(name, isFrameworks) {
          // Allow the user to specify a specific element to use via tools:index
          @Override
          protected int getDefaultIndex() {
            String index = myTag.getAttributeValue(ATTR_INDEX, TOOLS_URI);
            if (index != null) {
              return Integer.parseInt(index);
            }
            return super.getDefaultIndex();
          }
        });
        break;
      case PLURALS:
        value = parsePluralsValue(new PluralsResourceValue(name, isFrameworks) {
          // Allow the user to specify a specific quantity to use via tools:quantity
          @Override
          public String getValue() {
            String quantity = myTag.getAttributeValue(ATTR_QUANTITY, TOOLS_URI);
            if (quantity != null) {
              String value = getValue(quantity);
              if (value != null) {
                return value;
              }
            }
            return super.getValue();
          }
        });
        break;
      case STRING:
        value = parseTextValue(new PsiTextResourceValue(type, name, isFrameworks));
        break;
      default:
        value = parseValue(new ResourceValue(type, name, isFrameworks));
        break;
    }

    return value;
  }

  @Nullable
  private static String getAttributeValue(XmlTag tag, String attributeName) {
    return tag.getAttributeValue(attributeName);
  }

  @NonNull
  private ResourceValue parseDeclareStyleable(@NonNull DeclareStyleableResourceValue declareStyleable) {
    assert myTag != null;
    for (XmlTag child : myTag.getSubTags()) {
      String name = getAttributeValue(child, ATTR_NAME);
      if (name != null) {
        // is the attribute in the android namespace?
        boolean isFrameworkAttr = declareStyleable.isFramework();
        if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
          name = name.substring(ANDROID_NS_NAME_PREFIX_LEN);
          isFrameworkAttr = true;
        }

        AttrResourceValue attr = parseAttrValue(child, new AttrResourceValue(ResourceType.ATTR, name, isFrameworkAttr));
        declareStyleable.addValue(attr);
      }
    }

    return declareStyleable;
  }

  @NonNull
  private ResourceValue parseStyleValue(@NonNull StyleResourceValue styleValue) {
    assert myTag != null;
    for (XmlTag child : myTag.getSubTags()) {
      String name = getAttributeValue(child, ATTR_NAME);
      if (name != null) {
        // is the attribute in the android namespace?
        boolean isFrameworkAttr = styleValue.isFramework();
        if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
          name = name.substring(ANDROID_NS_NAME_PREFIX_LEN);
          isFrameworkAttr = true;
        }

        ItemResourceValue resValue = new ItemResourceValue(name, isFrameworkAttr, styleValue.isFramework());
        resValue.setValue(ValueXmlHelper.unescapeResourceString(getTextContent(child), true, true));
        styleValue.addItem(resValue);
      }
    }

    return styleValue;
  }

  @NonNull
  private AttrResourceValue parseAttrValue(@NonNull AttrResourceValue attrValue) {
    assert myTag != null;
    return parseAttrValue(myTag, attrValue);
  }

  @NonNull
  private static AttrResourceValue parseAttrValue(@NonNull XmlTag myTag, @NonNull AttrResourceValue attrValue) {
    for (XmlTag child : myTag.getSubTags()) {
      String name = getAttributeValue(child, ATTR_NAME);
      if (name != null) {
        String value = getAttributeValue(child, ATTR_VALUE);
        if (value != null) {
          try {
            // Integer.decode/parseInt can't deal with hex value > 0x7FFFFFFF so we
            // use Long.decode instead.
            attrValue.addValue(name, (int)(long)Long.decode(value));
          } catch (NumberFormatException e) {
            // pass, we'll just ignore this value
          }
        }
      }
    }

    return attrValue;
  }

  private ResourceValue parseArrayValue(ArrayResourceValue arrayValue) {
    assert myTag != null;
    for (XmlTag child : myTag.getSubTags()) {
      String text = ValueXmlHelper.unescapeResourceString(getTextContent(child), true, true);
      arrayValue.addElement(text);
    }

    return arrayValue;
  }

  private ResourceValue parsePluralsValue(PluralsResourceValue value) {
    assert myTag != null;
    for (XmlTag child : myTag.getSubTags()) {
      String quantity = child.getAttributeValue(ATTR_QUANTITY);
      if (quantity != null) {
        String text = ValueXmlHelper.unescapeResourceString(getTextContent(child), true, true);
        value.addPlural(quantity, text);
      }
    }

    return value;
  }

  @NonNull
  private ResourceValue parseValue(@NonNull ResourceValue value) {
    assert myTag != null;
    String text = getTextContent(myTag);
    text = ValueXmlHelper.unescapeResourceString(text, true, true);
    value.setValue(text);
    return value;
  }

  /**
   * Returns the text content of a given tag
   */
  public static String getTextContent(@NonNull XmlTag tag) {
    // We can't just use tag.getValue().getTrimmedText() here because we need to remove
    // intermediate elements such as <xliff> text:
    // TODO: Make sure I correct handle HTML content for XML items in <string> nodes!
    // For example, for the following string we want to compute "Share with %s":
    // <string name="share">Share with <xliff:g id="application_name" example="Bluetooth">%s</xliff:g></string>
    XmlTag[] subTags = tag.getSubTags();
    XmlText[] textElements = tag.getValue().getTextElements();
    if (subTags.length == 0) {
      if (textElements.length == 1) {
        return getXmlTextValue(textElements[0]);
      } else if (textElements.length == 0) {
        return "";
      }
    }
    StringBuilder sb = new StringBuilder(40);
    appendText(sb, tag);
    return sb.toString();
  }

  @NonNull
  private PsiTextResourceValue parseTextValue(@NonNull PsiTextResourceValue value) {
    assert myTag != null;
    String text = getTextContent(myTag);
    text = ValueXmlHelper.unescapeResourceString(text, true, true);
    value.setValue(text);
    return value;
  }

  private static String getXmlTextValue(XmlText element) {
    PsiElement current = element.getFirstChild();
    if (current != null) {
      if (current.getNextSibling() != null) {
        StringBuilder sb = new StringBuilder();
        for (; current != null; current = current.getNextSibling()) {
          IElementType type = current.getNode().getElementType();
          if (type == XmlElementType.XML_CDATA) {
            PsiElement[] children = current.getChildren();
            if (children.length == 3) { // XML_CDATA_START, XML_DATA_CHARACTERS, XML_CDATA_END
              assert children[1].getNode().getElementType() == XmlTokenType.XML_DATA_CHARACTERS;
              sb.append(children[1].getText());
            }
            continue;
          }
          sb.append(current.getText());
        }
        return sb.toString();
      } else if (current.getNode().getElementType() == XmlElementType.XML_CDATA) {
        PsiElement[] children = current.getChildren();
        if (children.length == 3) { // XML_CDATA_START, XML_DATA_CHARACTERS, XML_CDATA_END
          assert children[1].getNode().getElementType() == XmlTokenType.XML_DATA_CHARACTERS;
          return children[1].getText();
        }
      }
    }

    return element.getText();
  }

  private static void appendText(@NonNull StringBuilder sb, @NonNull XmlTag tag) {
    PsiElement[] children = tag.getChildren();
    for (PsiElement child : children) {
      if (child instanceof XmlText) {
        XmlText text = (XmlText)child;
        sb.append(getXmlTextValue(text));
      } else if (child instanceof XmlTag) {
        XmlTag childTag = (XmlTag)child;
        // xliff support
        if (XLIFF_G_TAG.equals(childTag.getLocalName()) && childTag.getNamespace().startsWith(XLIFF_NAMESPACE_PREFIX)) {
          String example = childTag.getAttributeValue(ATTR_EXAMPLE);
          if (example != null) {
            // <xliff:g id="number" example="7">%d</xliff:g> minutes => "(7) minutes"
            sb.append('(').append(example).append(')');
            continue;
          } else {
            String id = childTag.getAttributeValue(ATTR_ID);
            if (id != null) {
              // Step <xliff:g id="step_number">%1$d</xliff:g> => Step ${step_number}
              sb.append('$').append('{').append(id).append('}');
              continue;
            }
          }
        }
        appendText(sb, childTag);
      }
    }
  }

  @NonNull
  PsiFile getPsiFile() {
    return myFile;
  }

  /** Clears the cached value, if any, and returns true if the value was cleared */
  public boolean recomputeValue() {
    if (mResourceValue != null) {
      // Force recompute in getResourceValue
      mResourceValue = null;
      return true;
    } else {
      return false;
    }
  }

  @Nullable
  public XmlTag getTag() {
    return myTag;
  }

  @Override
  public boolean equals(Object o) {
    // Only reference equality; we need to be able to distinguish duplicate elements which can happen during editing
    // for incremental updating to handle temporarily aliasing items.
    return this == o;
  }

  @Override
  public int hashCode() {
    return getName().hashCode();
  }

  @Override
  public String toString() {
    return super.toString() + ": " + (myTag != null ? getTextContent(myTag) : "null" + (myFile != null ? ":" + myFile.getName() : ""));
  }

  private class PsiTextResourceValue extends TextResourceValue {
    public PsiTextResourceValue(ResourceType type, String name, boolean isFramework) {
      super(type, name, isFramework);
    }

    @Override
    public String getRawXmlValue() {
      if (myTag != null && myTag.isValid()) {
        if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
          return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
            @Override
            public String compute() {
              return myTag.getValue().getText();
            }
          });
        }
        return myTag.getValue().getText();
      }
      else {
        return getValue();
      }
    }
  }
}
