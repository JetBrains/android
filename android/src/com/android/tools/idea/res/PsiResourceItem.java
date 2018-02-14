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
package com.android.tools.idea.res;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ValueXmlHelper;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

import static com.android.SdkConstants.*;

class PsiResourceItem extends ResourceItem {
  private final XmlTag myTag;
  private PsiFile myFile;

  PsiResourceItem(@NonNull String name, @NonNull ResourceType type, @NotNull ResourceNamespace namespace, @Nullable XmlTag tag, @NonNull PsiFile file) {
    // TODO: Actually figure out the namespace.
    super(name, namespace, type, null, null);
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
      FolderConfiguration fromQualifiers = FolderConfiguration.getConfigForQualifierString(qualifiers);
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
    PsiElement parent = source == null && myFile != null ? AndroidPsiUtils.getPsiParentSafely(myFile) : null;

    if (parent == null || !(parent instanceof PsiDirectory)) {
      return source;
    }

    String name = ((PsiDirectory)parent).getName();
    ResourceFolderType folderType = ResourceFolderType.getFolderType(name);
    FolderConfiguration configuration = FolderConfiguration.getConfigForFolder(name);
    int index = name.indexOf('-');
    String qualifiers = index == -1 ? "" : name.substring(index + 1);
    source = new PsiResourceFile(myFile, Collections.singletonList(this), qualifiers, folderType, configuration);
    setSource(source);

    return source;
  }

  /**
   * GETTER WITH SIDE EFFECTS that registers we have taken an interest in this value
   * so that if the value changes we will get a resource changed event fire.
   */
  @Nullable
  @Override
  public ResourceValue getResourceValue() {
    if (mResourceValue == null) {
      //noinspection VariableNotUsedInsideIf
      if (myTag == null) {
        // Density based resource value?
        ResourceType type = getType();
        Density density = type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP ? getFolderDensity() : null;
        if (density != null) {
          mResourceValue = new DensityBasedResourceValue(getReferenceToSelf(),
                                                         getSource().getFile().getAbsolutePath(),
                                                         density,
                                                         null);
        } else {
          mResourceValue = new ResourceValue(getReferenceToSelf(),
                                             getSource().getFile().getAbsolutePath(),
                                             null);
        }
      } else {
        mResourceValue = parseXmlToResourceValue();
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
  private ResourceValue parseXmlToResourceValue() {
    assert myTag != null;

    if (!myTag.isValid()) {
      return null;
    }

    ResourceValue value;
    switch (getType()) {
      case STYLE:
        String parent = getAttributeValue(myTag, ATTR_PARENT);
        value = parseStyleValue(new StyleResourceValue(getReferenceToSelf(), parent, null));
        break;
      case DECLARE_STYLEABLE:
        value = parseDeclareStyleable(new DeclareStyleableResourceValue(getReferenceToSelf(), null, null));
        break;
      case ATTR:
        value = parseAttrValue(new AttrResourceValue(getReferenceToSelf(), null));
        break;
      case ARRAY:
          value = parseArrayValue(new ArrayResourceValue(getReferenceToSelf(), null) {
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
        value = parsePluralsValue(new PluralsResourceValue(getReferenceToSelf(), null, null) {
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
        value = parseTextValue(new PsiTextResourceValue(getReferenceToSelf(), null, null, null));
        break;
      default:
        value = parseValue(new ResourceValue(getReferenceToSelf(), null));
        break;
    }

    value.setNamespaceResolver(getNamespaceResolver(myTag));
    return value;
  }

  @NotNull
  private static ResourceNamespace.Resolver getNamespaceResolver(XmlTag tag) {
    // TODO(b/72688160, namespaces): precompute this to avoid the read lock.
    return new ResourceNamespace.Resolver() {
      @Nullable
      @Override
      public String uriToPrefix(@NonNull String namespaceUri) {
        return ReadAction.compute(() -> {
          if (!tag.isValid()) {
            return null;
          }
          return StringUtil.nullize(tag.getPrefixByNamespace(namespaceUri));
        });
      }

      @Nullable
      @Override
      public String prefixToUri(@NonNull String namespacePrefix) {
        return ReadAction.compute(() -> {
          if (!tag.isValid()) {

            return null;
          }
          return StringUtil.nullize(tag.getNamespaceByPrefix(namespacePrefix));
        });
      }
    };
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
      if (!StringUtil.isEmpty(name)) {
        // is the attribute in the android namespace?
        boolean isFrameworkAttr = declareStyleable.isFramework();
        if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
          name = name.substring(ANDROID_NS_NAME_PREFIX_LEN);
          isFrameworkAttr = true;
        }

        AttrResourceValue attr = parseAttrValue(child,
                                                new AttrResourceValue(new ResourceReference(ResourceType.ATTR, name, isFrameworkAttr),
                                                                      null));
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
      if (!StringUtil.isEmpty(name)) {
        String value = ValueXmlHelper.unescapeResourceString(ResourceHelper.getTextContent(child), true, true);
        ItemResourceValue itemValue = new ItemResourceValue(styleValue.getNamespace(), name, value, styleValue.getLibraryName());
        itemValue.setNamespaceResolver(getNamespaceResolver(child));
        styleValue.addItem(itemValue);
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
      String text = ValueXmlHelper.unescapeResourceString(ResourceHelper.getTextContent(child), true, true);
      arrayValue.addElement(text);
    }

    return arrayValue;
  }

  private ResourceValue parsePluralsValue(PluralsResourceValue value) {
    assert myTag != null;
    for (XmlTag child : myTag.getSubTags()) {
      String quantity = child.getAttributeValue(ATTR_QUANTITY);
      if (quantity != null) {
        String text = ValueXmlHelper.unescapeResourceString(ResourceHelper.getTextContent(child), true, true);
        value.addPlural(quantity, text);
      }
    }

    return value;
  }

  @NonNull
  private ResourceValue parseValue(@NonNull ResourceValue value) {
    assert myTag != null;
    String text = ResourceHelper.getTextContent(myTag);
    text = ValueXmlHelper.unescapeResourceString(text, true, true);
    value.setValue(text);
    return value;
  }

  @NonNull
  private PsiTextResourceValue parseTextValue(@NonNull PsiTextResourceValue value) {
    assert myTag != null;
    String text = ResourceHelper.getTextContent(myTag);
    text = ValueXmlHelper.unescapeResourceString(text, true, true);
    value.setValue(text);
    return value;
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
    return super.toString() + ": " + (myTag != null ? ResourceHelper.getTextContent(myTag) : "null" + (myFile != null ? ":" + myFile.getName() : ""));
  }

  private class PsiTextResourceValue extends TextResourceValue {
    public PsiTextResourceValue(ResourceReference reference, String textValue, String rawXmlValue, String libraryName) {
      super(reference, textValue, rawXmlValue, libraryName);
    }

    @Override
    public String getRawXmlValue() {
      if (myTag != null && myTag.isValid()) {
        if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
          return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> myTag.getValue().getText());
        }
        return myTag.getValue().getText();
      }
      else {
        return getValue();
      }
    }
  }
}
