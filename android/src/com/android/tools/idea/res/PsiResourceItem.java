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

import com.android.ide.common.rendering.api.*;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ValueXmlHelper;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.AndroidPsiUtils;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;

import static com.android.SdkConstants.*;

public class PsiResourceItem implements ResourceItem {
  @NotNull private final String myName;
  @NotNull private final ResourceType myType;
  @NotNull private final ResourceNamespace myNamespace;
  @Nullable private ResourceValue myResourceValue;
  @Nullable private PsiResourceFile mySource;
  @Nullable private final SoftReference<XmlTag> myOriginalTag;
  @NotNull private final SoftReference<PsiFile> myOriginalFile;
  @Nullable private final Object smartPsiPointerLock;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  @Nullable private SmartPsiElementPointer<XmlTag> myTagPointer; // Guarded by smartPsiPointerLock if that is not null.
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  @Nullable private SmartPsiElementPointer<PsiFile> myFilePointer; // Guarded by smartPsiPointerLock if that is not null.

  private PsiResourceItem(@NotNull String name,
                          @NotNull ResourceType type,
                          @NotNull ResourceNamespace namespace,
                          @Nullable XmlTag tag,
                          @NotNull PsiFile file,
                          boolean calledFromPsiListener) {
    myName = name;
    myType = type;
    myNamespace = namespace;

    myOriginalTag = tag == null ? null : new SoftReference<>(tag);
    myOriginalFile = new SoftReference<>(file);
    if (calledFromPsiListener) {
      // Smart pointers have to be created asynchronously to avoid the "Smart pointers shouldn't be created during PSI changes" error.
      smartPsiPointerLock = new Object();
      Application application = ApplicationManager.getApplication();
      application.executeOnPooledThread(() -> application.runReadAction(this::createSmartPointers));
    }
    else {
      smartPsiPointerLock = null; // No locking required.
      createSmartPointers();
    }
  }

  private void createSmartPointers() {
    SmartPsiElementPointer<XmlTag> tagPointer = myOriginalTag == null ? null : createSmartPointer(myOriginalTag);
    SmartPsiElementPointer<PsiFile> filePointer = createSmartPointer(myOriginalFile);
    if (smartPsiPointerLock == null) {
      myTagPointer = tagPointer;
      myFilePointer = filePointer;
    } else {
      synchronized (smartPsiPointerLock) {
        myTagPointer = tagPointer;
        myFilePointer = filePointer;
      }
    }
  }

  @Nullable
  private static <T extends PsiElement> SmartPsiElementPointer<T> createSmartPointer(@NotNull SoftReference<T> elementReference) {
    T element = elementReference.get();
    if (element == null) {
      return null; // The PSI element has already been garbage collected.
    }
    return SmartPointerManager.createPointer(element);
  }

  /**
   * Creates a new PsiResourceItem for a given {@link XmlTag}.
   *
   * @param name the name of the resource
   * @param type the type of the resource
   * @param namespace the namespace of the resource
   * @param tag the XML tag to create the resource from
   * @param calledFromPsiListener true if the method was called from a PSI listener
   */
  @NotNull
  public static PsiResourceItem forXmlTag(@NotNull String name,
                                          @NotNull ResourceType type,
                                          @NotNull ResourceNamespace namespace,
                                          @NotNull XmlTag tag,
                                          boolean calledFromPsiListener) {
    return new PsiResourceItem(name, type, namespace, tag, tag.getContainingFile(), calledFromPsiListener);
  }

  /**
   * Creates a new PsiResourceItem for a given {@link PsiFile}.
   *
   * @param name the name of the resource
   * @param type the type of the resource
   * @param namespace the namespace of the resource
   * @param file the XML file to create the resource from
   * @param calledFromPsiListener true if the method was called from a PSI listener
   */
  @NotNull
  public static PsiResourceItem forFile(@NotNull String name,
                                        @NotNull ResourceType type,
                                        @NotNull ResourceNamespace namespace,
                                        @NotNull PsiFile file,
                                        boolean calledFromPsiListener) {
    return new PsiResourceItem(name, type, namespace, null, file, calledFromPsiListener);
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public ResourceType getType() {
    return myType;
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return myNamespace;
  }

  @Override
  @Nullable
  public String getLibraryName() {
    return null;
  }

  @Override
  @NotNull
  public ResourceReference getReferenceToSelf() {
    return new ResourceReference(myNamespace, myType, myName);
  }

  @Override
  @NotNull
  public FolderConfiguration getConfiguration() {
    PsiResourceFile source = getSource();
    assert source != null : "getConfiguration called on a PsiResourceItem with no source";
    return source.getFolderConfiguration();
  }

  @Override
  @NotNull
  public String getKey() {
    String qualifiers = getConfiguration().getQualifierString();
    if (!qualifiers.isEmpty()) {
      return myType.getName() + '-' + qualifiers + '/' + myName;
    }

    return myType.getName() + '/' + myName;
  }

  @Nullable
  public PsiResourceFile getSource() {
    if (mySource != null) {
      return mySource;
    }

    PsiFile psiFile = getPsiFile();
    if (psiFile == null) {
      return null;
    }

    PsiElement parent = AndroidPsiUtils.getPsiParentSafely(psiFile);
    if (!(parent instanceof PsiDirectory)) {
      return null;
    }

    String name = ((PsiDirectory)parent).getName();
    ResourceFolderType folderType = ResourceFolderType.getFolderType(name);
    if (folderType == null) {
      return null;
    }

    FolderConfiguration configuration = FolderConfiguration.getConfigForFolder(name);
    if (configuration == null) {
      return null;
    }

    // PsiResourceFile constructor sets the source of this item.
    return new PsiResourceFile(psiFile, Collections.singletonList(this), folderType, configuration);
  }

  public void setSource(@Nullable PsiResourceFile source) {
    mySource = source;
  }

  /**
   * GETTER WITH SIDE EFFECTS that registers we have taken an interest in this value
   * so that if the value changes we will get a resource changed event fire.
   */
  @Override
  @Nullable
  public ResourceValue getResourceValue() {
    if (myResourceValue == null) {
      XmlTag tag = getTag();
      if (tag == null) {
        PsiResourceFile source = getSource();
        assert source != null : "getResourceValue called on a PsiResourceItem with no source";
        // Density based resource value?
        ResourceType type = getType();
        Density density = type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP ? getFolderDensity() : null;

        VirtualFile virtualFile = source.getVirtualFile();
        String path = virtualFile == null ? null : VfsUtilCore.virtualToIoFile(virtualFile).getAbsolutePath();
        if (density != null) {
          myResourceValue = new DensityBasedResourceValue(getReferenceToSelf(), path, density, null);
        } else {
          myResourceValue = new ResourceValue(getReferenceToSelf(), path, null);
        }
      } else {
        myResourceValue = parseXmlToResourceValue(tag);
      }
    }

    return myResourceValue;
  }

  @Override
  @Nullable
  public File getFile() {
    PsiFile psiFile = getPsiFile();
    if (psiFile == null) {
      return null;
    }

    VirtualFile virtualFile = psiFile.getVirtualFile();
    return virtualFile == null ? null : VfsUtilCore.virtualToIoFile(virtualFile);
  }

  @Override
  public boolean isFileBased() {
    return myOriginalTag == null;
  }

  @Nullable
  private Density getFolderDensity() {
    FolderConfiguration configuration = getConfiguration();
    DensityQualifier densityQualifier = configuration.getDensityQualifier();
    if (densityQualifier != null) {
      return densityQualifier.getValue();
    }
    return null;
  }

  @Nullable
  private ResourceValue parseXmlToResourceValue(@Nullable XmlTag tag) {
    if (tag == null || !tag.isValid()) {
      return null;
    }

    ResourceValue value;
    switch (myType) {
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
  private static String getAttributeValue(@NotNull XmlTag tag, @NotNull String attributeName) {
    return tag.getAttributeValue(attributeName);
  }

  @NotNull
  private ResourceValue parseDeclareStyleable(@NotNull XmlTag tag, @NotNull DeclareStyleableResourceValue declareStyleable) {
    for (XmlTag child : tag.getSubTags()) {
      String name = getAttributeValue(child, ATTR_NAME);
      if (!StringUtil.isEmpty(name)) {
        ResourceUrl url = ResourceUrl.parseAttrReference(name);
        if (url != null) {
          ResourceReference resolvedAttr = url.resolve(getNamespace(), ResourceHelper.getNamespaceResolver(tag));
          if (resolvedAttr != null) {
            AttrResourceValue attr = parseAttrValue(child, new AttrResourceValue(resolvedAttr, null));
            declareStyleable.addValue(attr);
          }
        }
      }
    }

    return declareStyleable;
  }

  @NotNull
  private static ResourceValue parseStyleValue(@NotNull XmlTag tag, @NotNull StyleResourceValue styleValue) {
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

  @NotNull
  private static AttrResourceValue parseAttrValue(@NotNull XmlTag tag, @NotNull AttrResourceValue attrValue) {
    for (XmlTag child : tag.getSubTags()) {
      String name = getAttributeValue(child, ATTR_NAME);
      if (name != null) {
        String value = getAttributeValue(child, ATTR_VALUE);
        if (value != null) {
          try {
            // Use Long.decode to deal with hexadecimal values greater than 0x7FFFFFFF.
            attrValue.addValue(name, Long.decode(value).intValue());
          } catch (NumberFormatException e) {
            // Ignore the invalid value.
          }
        }
      }
    }

    return attrValue;
  }

  @NotNull
  private static ResourceValue parseArrayValue(@NotNull XmlTag tag, @NotNull ArrayResourceValue arrayValue) {
    for (XmlTag child : tag.getSubTags()) {
      String text = ValueXmlHelper.unescapeResourceString(ResourceHelper.getTextContent(child), true, true);
      arrayValue.addElement(text);
    }

    return arrayValue;
  }

  @NotNull
  private static ResourceValue parsePluralsValue(@NotNull XmlTag tag, @NotNull PluralsResourceValue value) {
    for (XmlTag child : tag.getSubTags()) {
      String quantity = child.getAttributeValue(ATTR_QUANTITY);
      if (quantity != null) {
        String text = ValueXmlHelper.unescapeResourceString(ResourceHelper.getTextContent(child), true, true);
        value.addPlural(quantity, text);
      }
    }

    return value;
  }

  @NotNull
  private static ResourceValue parseValue(@NotNull XmlTag tag, @NotNull ResourceValue value) {
    String text = ResourceHelper.getTextContent(tag);
    text = ValueXmlHelper.unescapeResourceString(text, true, true);
    value.setValue(text);

    return value;
  }

  @NotNull
  private static PsiTextResourceValue parseTextValue(@NotNull XmlTag tag, @NotNull PsiTextResourceValue value) {
    String text = ResourceHelper.getTextContent(tag);
    text = ValueXmlHelper.unescapeResourceString(text, true, true);
    value.setValue(text);

    return value;
  }

  @Nullable
  public XmlTag getTag() {
    if (smartPsiPointerLock == null) {
      return myOriginalTag == null ? null : myTagPointer == null ? myOriginalTag.get() : myTagPointer.getElement();
    } else {
      synchronized (smartPsiPointerLock) {
        return myOriginalTag == null ? null : myTagPointer == null ? myOriginalTag.get() : myTagPointer.getElement();
      }
    }
  }

  @Nullable
  PsiFile getPsiFile() {
    if (smartPsiPointerLock == null) {
      return myFilePointer == null ? myOriginalFile.get() : myFilePointer.getElement();
    } else {
      synchronized (smartPsiPointerLock) {
        return myFilePointer == null ? myOriginalFile.get() : myFilePointer.getElement();
      }
    }
  }

  /**
   * Returns true if this {@link PsiResourceItem} was originally pointing to the given tag.
   */
  public boolean wasTag(@NotNull XmlTag tag) {
    return myOriginalTag != null && tag == myOriginalTag.get() || tag == getTag();
  }

  /** Clears the cached value, if any, and returns true if the value was cleared. */
  public boolean recomputeValue() {
    if (myResourceValue == null) {
      return false;
    }

    // Force recompute in getResourceValue.
    myResourceValue = null;
    return true;
  }

  @Override
  public boolean equals(Object o) {
    // Only reference equality; we need to be able to distinguish duplicate elements which can happen during editing
    // for incremental updating to handle temporarily aliasing items.
    return this == o;
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public String toString() {
    ToStringHelper helper = MoreObjects.toStringHelper(this)
        .add("name", myName)
        .add("namespace", myNamespace)
        .add("type", myType);
    XmlTag tag = getTag();
    if (tag != null) {
      helper.add("tag", ResourceHelper.getTextContent(tag));
    }
    PsiFile file = getPsiFile();
    if (file != null) {
      helper.add("file", file.getName());
    }
    return helper.toString();
  }

  private class PsiTextResourceValue extends TextResourceValue {
    public PsiTextResourceValue(ResourceReference reference, String textValue, String rawXmlValue, String libraryName) {
      super(reference, textValue, rawXmlValue, libraryName);
    }

    @Override
    public String getRawXmlValue() {
      XmlTag tag = getTag();

      if (tag == null || !tag.isValid()) {
        return getValue();
      }

      if (ApplicationManager.getApplication().isReadAccessAllowed()) {
        return tag.getValue().getText();
      }

      return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> tag.getValue().getText());
    }
  }
}
