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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

import static com.android.SdkConstants.*;

class PsiResourceItem extends ResourceItem {
  private final SmartPsiElementPointer<XmlTag> myTagPointer;
  private final SmartPsiElementPointer<PsiFile> myFilePointer;
  private final XmlTag myOriginalTag;

  private PsiResourceItem(@NonNull String name,
                          @NonNull ResourceType type,
                          @NotNull ResourceNamespace namespace,
                          @Nullable XmlTag tag,
                          @NonNull PsiFile file) {
    // TODO: Actually figure out the namespace.
    super(name, namespace, type, null, null);
    myOriginalTag = tag;
    myTagPointer = tag != null ? SmartPointerManager.createPointer(tag) : null;
    myFilePointer = SmartPointerManager.createPointer(file);
  }

  /**
   * Creates a new PsiResourceItem for a given {@link XmlTag}
   */
  @NonNull
  public static PsiResourceItem forXmlTag(@NonNull String name,
                                           @NonNull ResourceType type,
                                           @NotNull ResourceNamespace namespace,
                                           @NotNull XmlTag tag) {
    return new PsiResourceItem(name, type, namespace, tag, tag.getContainingFile());
  }

  /**
   * Creates a new PsiResourceItem for a given {@link PsiFile}
   */
  @NonNull
  public static PsiResourceItem forFile(@NonNull String name,
                                           @NonNull ResourceType type,
                                           @NotNull ResourceNamespace namespace,
                                           @NotNull PsiFile file) {
    return new PsiResourceItem(name, type, namespace, null, file);
  }

  @Override
  public FolderConfiguration getConfiguration() {
    PsiResourceFile source = (PsiResourceFile)super.getSource();

    PsiFile file = getPsiFile();

    // Temporary safety workaround
    if (source == null) {
      if (file != null) {
        PsiDirectory parent = file.getParent();
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
    PsiFile file = getPsiFile();
    PsiElement parent = source == null && file != null ? AndroidPsiUtils.getPsiParentSafely(file) : null;

    if (parent == null || !(parent instanceof PsiDirectory)) {
      return source;
    }

    String name = ((PsiDirectory)parent).getName();
    ResourceFolderType folderType = ResourceFolderType.getFolderType(name);
    FolderConfiguration configuration = FolderConfiguration.getConfigForFolder(name);
    int index = name.indexOf('-');
    String qualifiers = index == -1 ? "" : name.substring(index + 1);
    source = new PsiResourceFile(file, Collections.singletonList(this), qualifiers, folderType, configuration);
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
      if (myTagPointer == null) {
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
    assert myTagPointer != null;
    XmlTag tag = getTag();

    if (tag == null || !tag.isValid()) {
      return null;
    }

    ResourceValue value;
    switch (getType()) {
      case STYLE:
        String parent = getAttributeValue(tag, ATTR_PARENT);
        value = parseStyleValue(tag, new StyleResourceValue(getReferenceToSelf(), parent, null));
        break;
      case DECLARE_STYLEABLE:
        value = parseDeclareStyleable(tag, new DeclareStyleableResourceValue(getReferenceToSelf(), null, null));
        break;
      case ATTR:
        value = parseAttrValue(tag, new AttrResourceValue(getReferenceToSelf(), null));
        break;
      case ARRAY:
          value = parseArrayValue(tag, new ArrayResourceValue(getReferenceToSelf(), null) {
          // Allow the user to specify a specific element to use via tools:index
          @Override
          protected int getDefaultIndex() {
            String index = tag.getAttributeValue(ATTR_INDEX, TOOLS_URI);
            if (index != null) {
              return Integer.parseInt(index);
            }
            return super.getDefaultIndex();
          }
        });
        break;
      case PLURALS:
        value = parsePluralsValue(tag, new PluralsResourceValue(getReferenceToSelf(), null, null) {
          // Allow the user to specify a specific quantity to use via tools:quantity
          @Override
          public String getValue() {
            String quantity = tag.getAttributeValue(ATTR_QUANTITY, TOOLS_URI);
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
        value = parseTextValue(tag, new PsiTextResourceValue(getReferenceToSelf(), null, null, null));
        break;
      default:
        value = parseValue(tag, new ResourceValue(getReferenceToSelf(), null));
        break;
    }

    value.setNamespaceResolver(ResourceHelper.getNamespaceResolver(tag));
    return value;
  }

  @Nullable
  private static String getAttributeValue(@NonNull XmlTag tag, @NonNull String attributeName) {
    return tag.getAttributeValue(attributeName);
  }

  @NonNull
  private static ResourceValue parseDeclareStyleable(@NonNull XmlTag tag, @NonNull DeclareStyleableResourceValue declareStyleable) {
    for (XmlTag child : tag.getSubTags()) {
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
  private static ResourceValue parseStyleValue(@NonNull XmlTag tag, @NonNull StyleResourceValue styleValue) {
    for (XmlTag child : tag.getSubTags()) {
      String name = getAttributeValue(child, ATTR_NAME);
      if (!StringUtil.isEmpty(name)) {
        String value = ValueXmlHelper.unescapeResourceString(ResourceHelper.getTextContent(child), true, true);
        ItemResourceValue itemValue = new ItemResourceValue(styleValue.getNamespace(), name, value, styleValue.getLibraryName());
        itemValue.setNamespaceResolver(ResourceHelper.getNamespaceResolver(child));
        styleValue.addItem(itemValue);
      }
    }

    return styleValue;
  }

  @NonNull
  private static AttrResourceValue parseAttrValue(@NonNull XmlTag tag, @NonNull AttrResourceValue attrValue) {
    for (XmlTag child : tag.getSubTags()) {
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

  private static ResourceValue parseArrayValue(@NonNull XmlTag tag, @NonNull ArrayResourceValue arrayValue) {
    for (XmlTag child : tag.getSubTags()) {
      String text = ValueXmlHelper.unescapeResourceString(ResourceHelper.getTextContent(child), true, true);
      arrayValue.addElement(text);
    }

    return arrayValue;
  }

  private static ResourceValue parsePluralsValue(@NonNull XmlTag tag, @NonNull PluralsResourceValue value) {
    for (XmlTag child : tag.getSubTags()) {
      String quantity = child.getAttributeValue(ATTR_QUANTITY);
      if (quantity != null) {
        String text = ValueXmlHelper.unescapeResourceString(ResourceHelper.getTextContent(child), true, true);
        value.addPlural(quantity, text);
      }
    }

    return value;
  }

  @NonNull
  private static ResourceValue parseValue(@NonNull XmlTag tag, @NonNull ResourceValue value) {
    String text = ResourceHelper.getTextContent(tag);
    text = ValueXmlHelper.unescapeResourceString(text, true, true);
    value.setValue(text);

    return value;
  }

  @NonNull
  private static PsiTextResourceValue parseTextValue(@NonNull XmlTag tag, @NonNull PsiTextResourceValue value) {
    String text = ResourceHelper.getTextContent(tag);
    text = ValueXmlHelper.unescapeResourceString(text, true, true);
    value.setValue(text);

    return value;
  }

  @Nullable
  PsiFile getPsiFile() {
    return myFilePointer.getElement();
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
    return myTagPointer != null ? myTagPointer.getElement() : null;

  }

  /**
   * Returns true if this {@link PsiResourceItem} was originally pointing to the given tag.
   */
  public boolean wasTag(@NonNull XmlTag tag) {
    return tag == myOriginalTag || tag == getTag();
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
    XmlTag tag = getTag();
    PsiFile file = getPsiFile();
    return super.toString() + ": " + (tag != null ? ResourceHelper.getTextContent(tag) : "null" + (file != null ? ":" + file.getName() : ""));
  }

  private class PsiTextResourceValue extends TextResourceValue {
    public PsiTextResourceValue(ResourceReference reference, String textValue, String rawXmlValue, String libraryName) {
      super(reference, textValue, rawXmlValue, libraryName);
    }

    @Override
    public String getRawXmlValue() {
      XmlTag tag = getTag();

      if (tag != null && tag.isValid()) {
        if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
          return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> tag.getValue().getText());
        }
        return tag.getValue().getText();
      }
      else {
        return getValue();
      }
    }
  }
}
