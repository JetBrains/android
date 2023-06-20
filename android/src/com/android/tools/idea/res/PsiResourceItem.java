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

import static com.android.SdkConstants.ATTR_FORMAT;
import static com.android.SdkConstants.ATTR_INDEX;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_QUANTITY;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.TAG_ENUM;
import static com.android.SdkConstants.TAG_FLAG;
import static com.android.SdkConstants.TOOLS_URI;

import com.android.ide.common.rendering.api.ArrayResourceValueImpl;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.AttrResourceValueImpl;
import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.DensityBasedResourceValueImpl;
import com.android.ide.common.rendering.api.PluralsResourceValueImpl;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl;
import com.android.ide.common.rendering.api.StyleResourceValueImpl;
import com.android.ide.common.rendering.api.StyleableResourceValueImpl;
import com.android.ide.common.rendering.api.TextResourceValueImpl;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ValueXmlHelper;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.resources.base.RepositoryConfiguration;
import com.android.tools.idea.AndroidPsiUtils;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import java.lang.ref.WeakReference;
import java.util.EnumSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resource item backed by an {@link XmlTag} PSI element.
 */
public final class PsiResourceItem implements ResourceItem {
  @NotNull private final String myName;
  @NotNull private final ResourceType myType;

  @NotNull private final ResourceFolderRepository myOwner;
  @Nullable private ResourceValue myResourceValue;
  @Nullable private PsiResourceFile mySourceFile;
  @Nullable private final SmartPsiElementPointer<PsiFile> myFilePointer;
  @Nullable private final SmartPsiElementPointer<XmlTag> myTagPointer;
  /**
   * This weak reference is kept exclusively for the {@link #wasTag(XmlTag)} method. Once the original
   * tag is garbage collected, the {@link #wasTag(XmlTag)} method will return false for any tag except
   * the one pointed to by {@link #myTagPointer}.
   */
  @Nullable private final WeakReference<XmlTag> myOriginalTag;

  private PsiResourceItem(@NotNull String name,
                          @NotNull ResourceType type,
                          @NotNull ResourceFolderRepository owner,
                          @Nullable XmlTag tag,
                          @NotNull PsiFile file) {
    myName = name;
    myType = type;
    myOwner = owner;

    SmartPointerManager pointerManager = SmartPointerManager.getInstance(file.getProject());
    myFilePointer = pointerManager.createSmartPsiElementPointer(file);
    myTagPointer = tag == null ? null : pointerManager.createSmartPsiElementPointer(tag, file);
    myOriginalTag = tag == null ? null : new WeakReference<>(tag);
  }

  /**
   * Creates a new PsiResourceItem for a given {@link XmlTag}.
   *
   * @param name the name of the resource
   * @param type the type of the resource
   * @param owner the owning resource repository
   * @param tag the XML tag to create the resource from
   */
  @NotNull
  public static PsiResourceItem forXmlTag(@NotNull String name,
                                          @NotNull ResourceType type,
                                          @NotNull ResourceFolderRepository owner,
                                          @NotNull XmlTag tag) {
    return new PsiResourceItem(name, type, owner, tag, tag.getContainingFile());
  }

  /**
   * Creates a new PsiResourceItem for a given {@link PsiFile}.
   *
   * @param name the name of the resource
   * @param type the type of the resource
   * @param owner the owning resource repository
   * @param file the XML file to create the resource from
   */
  @NotNull
  public static PsiResourceItem forFile(@NotNull String name,
                                        @NotNull ResourceType type,
                                        @NotNull ResourceFolderRepository owner,
                                        @NotNull PsiFile file) {
    return new PsiResourceItem(name, type, owner, null, file);
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
  public ResourceFolderRepository getRepository() {
    return myOwner;
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return myOwner.getNamespace();
  }

  @Override
  @Nullable
  public String getLibraryName() {
    return null;
  }

  @Override
  @NotNull
  public ResourceReference getReferenceToSelf() {
    return new ResourceReference(getNamespace(), myType, myName);
  }

  @Override
  @NotNull
  public FolderConfiguration getConfiguration() {
    PsiResourceFile source = getSourceFile();
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
  public PsiResourceFile getSourceFile() {
    if (mySourceFile != null) {
      return mySourceFile;
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

    FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForFolder(name);
    if (folderConfiguration == null) {
      return null;
    }

    // PsiResourceFile constructor sets the source of this item.
    return new PsiResourceFile(psiFile, ImmutableList.of(this), folderType, new RepositoryConfiguration(myOwner, folderConfiguration));
  }

  public void setSourceFile(@Nullable PsiResourceFile sourceFile) {
    mySourceFile = sourceFile;
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
        PsiResourceFile source = getSourceFile();
        assert source != null : "getResourceValue called on a PsiResourceItem with no source";
        // Density based resource value?
        ResourceType type = getType();
        Density density = type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP ? getFolderDensity() : null;

        VirtualFile virtualFile = source.getVirtualFile();
        String path = virtualFile == null ? null : VfsUtilCore.virtualToIoFile(virtualFile).getAbsolutePath();
        if (density != null) {
          myResourceValue = new DensityBasedResourceValueImpl(getNamespace(), myType, myName, path, density, null);
        } else {
          myResourceValue = new ResourceValueImpl(getNamespace(), myType, myName, path, null);
        }
      } else {
        myResourceValue = parseXmlToResourceValueSafe(tag);
      }
    }

    return myResourceValue;
  }

  @Override
  @Nullable
  public PathString getSource() {
    PsiFile psiFile = getPsiFile();
    if (psiFile == null) {
      return null;
    }

    VirtualFile virtualFile = psiFile.getVirtualFile();
    return virtualFile == null ? null : new PathString(VfsUtilCore.virtualToIoFile(virtualFile));
  }

  @Override
  public boolean isFileBased() {
    return myTagPointer == null;
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
  private ResourceValue parseXmlToResourceValueSafe(@Nullable XmlTag tag) {
    Application application = ApplicationManager.getApplication();
    if (application.isReadAccessAllowed()) {
      return parseXmlToResourceValue(tag);
    }
    return application.runReadAction((Computable<ResourceValue>)() -> parseXmlToResourceValue(tag));
  }

  @Nullable
  private ResourceValue parseXmlToResourceValue(@Nullable XmlTag tag) {
    if (tag == null || !tag.isValid()) {
      return null;
    }

    ResourceValueImpl value;
    switch (myType) {
      case STYLE:
        String parent = getAttributeValue(tag, ATTR_PARENT);
        value = parseStyleValue(tag, new StyleResourceValueImpl(getNamespace(), myName, parent, null));
        break;
      case STYLEABLE:
        value = parseDeclareStyleable(tag, new StyleableResourceValueImpl(getNamespace(), myName, null, null));
        break;
      case ATTR:
        value = parseAttrValue(tag, new AttrResourceValueImpl(getNamespace(), myName, null));
        break;
      case ARRAY:
        value = parseArrayValue(tag, new ArrayResourceValueImpl(getNamespace(), myName, null) {
          // Allow the user to specify a specific element to use via tools:index
          @Override
          protected int getDefaultIndex() {
            String index = ReadAction.compute(() -> tag.getAttributeValue(ATTR_INDEX, TOOLS_URI));
            if (index != null) {
              return Integer.parseInt(index);
            }
            return super.getDefaultIndex();
          }
        });
        break;
      case PLURALS:
        value = parsePluralsValue(tag, new PluralsResourceValueImpl(getNamespace(), myName, null, null) {
          // Allow the user to specify a specific quantity to use via tools:quantity
          @Override
          public String getValue() {
            String quantity = ReadAction.compute(() -> tag.getAttributeValue(ATTR_QUANTITY, TOOLS_URI));
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
        value = parseTextValue(tag, new PsiTextResourceValue(getNamespace(), myName, null, null, null));
        break;
      default:
        value = parseValue(tag, new ResourceValueImpl(getNamespace(), myType, myName, null));
        break;
    }

    value.setNamespaceResolver(IdeResourcesUtil.getNamespaceResolver(tag));
    return value;
  }

  @Nullable
  private static String getAttributeValue(@NotNull XmlTag tag, @NotNull String attributeName) {
    return tag.getAttributeValue(attributeName);
  }

  @NotNull
  private StyleableResourceValueImpl parseDeclareStyleable(@NotNull XmlTag tag,
                                                           @NotNull StyleableResourceValueImpl declareStyleable) {
    for (XmlTag child : tag.getSubTags()) {
      String name = getAttributeValue(child, ATTR_NAME);
      if (!StringUtil.isEmpty(name)) {
        ResourceUrl url = ResourceUrl.parseAttrReference(name);
        if (url != null) {
          ResourceReference resolvedAttr = url.resolve(getNamespace(), IdeResourcesUtil.getNamespaceResolver(tag));
          if (resolvedAttr != null) {
            AttrResourceValue attr = parseAttrValue(child, new AttrResourceValueImpl(resolvedAttr, null));
            declareStyleable.addValue(attr);
          }
        }
      }
    }

    return declareStyleable;
  }

  @NotNull
  private static StyleResourceValueImpl parseStyleValue(@NotNull XmlTag tag, @NotNull StyleResourceValueImpl styleValue) {
    for (XmlTag child : tag.getSubTags()) {
      String name = getAttributeValue(child, ATTR_NAME);
      if (!StringUtil.isEmpty(name)) {
        String value = ValueXmlHelper.unescapeResourceString(IdeResourcesUtil.getTextContent(child), true, true);
        StyleItemResourceValueImpl itemValue =
            new StyleItemResourceValueImpl(styleValue.getNamespace(), name, value, styleValue.getLibraryName());
        itemValue.setNamespaceResolver(IdeResourcesUtil.getNamespaceResolver(child));
        styleValue.addItem(itemValue);
      }
    }

    return styleValue;
  }

  @NotNull
  private static AttrResourceValueImpl parseAttrValue(@NotNull XmlTag attrTag, @NotNull AttrResourceValueImpl attrValue) {
    attrValue.setDescription(getDescription(attrTag));

    Set<AttributeFormat> formats = EnumSet.noneOf(AttributeFormat.class);
    String formatString = getAttributeValue(attrTag, ATTR_FORMAT);
    if (formatString != null) {
      formats.addAll(AttributeFormat.parse(formatString));
    }

    for (XmlTag child : attrTag.getSubTags()) {
      String tagName = child.getName();
      if (TAG_ENUM.equals(tagName)) {
        formats.add(AttributeFormat.ENUM);
      } else if (TAG_FLAG.equals(tagName)) {
        formats.add(AttributeFormat.FLAGS);
      }

      String name = getAttributeValue(child, ATTR_NAME);
      if (name != null) {
        Integer numericValue = null;
        String value = getAttributeValue(child, ATTR_VALUE);
        if (value != null) {
          try {
            // Use Long.decode to deal with hexadecimal values greater than 0x7FFFFFFF.
            numericValue = Long.decode(value).intValue();
          } catch (NumberFormatException ignored) {
          }
        }
        attrValue.addValue(name, numericValue, getDescription(child));
      }
    }

    attrValue.setFormats(formats);

    return attrValue;
  }

  @Nullable
  private static String getDescription(@NotNull XmlTag tag) {
    XmlComment comment = XmlUtil.findPreviousComment(tag);
    if (comment != null) {
      String text = comment.getCommentText();
      return text.trim();
    }
    return null;
  }

  @NotNull
  private static ArrayResourceValueImpl parseArrayValue(@NotNull XmlTag tag, @NotNull ArrayResourceValueImpl arrayValue) {
    for (XmlTag child : tag.getSubTags()) {
      String text = ValueXmlHelper.unescapeResourceString(IdeResourcesUtil.getTextContent(child), true, true);
      arrayValue.addElement(text);
    }

    return arrayValue;
  }

  @NotNull
  private static PluralsResourceValueImpl parsePluralsValue(@NotNull XmlTag tag, @NotNull PluralsResourceValueImpl value) {
    for (XmlTag child : tag.getSubTags()) {
      String quantity = child.getAttributeValue(ATTR_QUANTITY);
      if (quantity != null) {
        String text = ValueXmlHelper.unescapeResourceString(IdeResourcesUtil.getTextContent(child), true, true);
        value.addPlural(quantity, text);
      }
    }

    return value;
  }

  @NotNull
  private static ResourceValueImpl parseValue(@NotNull XmlTag tag, @NotNull ResourceValueImpl value) {
    String text = IdeResourcesUtil.getTextContent(tag);
    text = ValueXmlHelper.unescapeResourceString(text, true, true);
    value.setValue(text);

    return value;
  }

  @NotNull
  private static PsiTextResourceValue parseTextValue(@NotNull XmlTag tag, @NotNull PsiTextResourceValue value) {
    String text = IdeResourcesUtil.getTextContent(tag);
    text = ValueXmlHelper.unescapeResourceString(text, true, true);
    value.setValue(text);

    return value;
  }

  /**
   * The returned {@link XmlTag} element is guaranteed to be valid if it is not null.
   */
  @Nullable
  public XmlTag getTag() {
    return validElementOrNull(myTagPointer == null ? null : myTagPointer.getElement());
  }

  /**
   * The returned {@link PsiFile} object is guaranteed to be valid if it is not null.
   */
  @Nullable
  public PsiFile getPsiFile() {
    return validElementOrNull(myFilePointer == null ? null : myFilePointer.getElement());
  }

  private static <E extends PsiElement> E validElementOrNull(@Nullable E psiElement) {
    return psiElement == null || !psiElement.isValid() ? null : psiElement;
  }

  /**
   * Returns true if this {@link PsiResourceItem} was originally or is currently pointing to the given tag.
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
    // Only reference equality; we need to be able to distinguish duplicate elements which can
    // happen during editing for incremental updating to handle temporarily aliasing items.
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
        .add("namespace", getNamespace())
        .add("type", myType);
    XmlTag tag = getTag();
    if (tag != null) {
      if (ApplicationManager.getApplication().isReadAccessAllowed()) {
        helper.add("tag", IdeResourcesUtil.getTextContent(tag));
      }
      else {
        helper.add("tag", ReadAction.compute(() -> IdeResourcesUtil.getTextContent(tag)));
      }
    }
    PathString file = getSource();
    if (file != null) {
      helper.add("file", file.getParentFileName() + '/' + file.getFileName());
    }
    return helper.toString();
  }

  private class PsiTextResourceValue extends TextResourceValueImpl {
    PsiTextResourceValue(@NotNull ResourceNamespace namespace, @NotNull String name,
                         @Nullable String textValue, @Nullable String rawXmlValue, @Nullable String libraryName) {
      super(namespace, name, textValue, rawXmlValue, libraryName);
    }

    @Override
    public String getRawXmlValue() {
      return ReadAction.compute(() -> {
        XmlTag tag = getTag();

        if (tag == null || !tag.isValid()) {
          return getValue();
        }

        return tag.getValue().getText();
      });
    }
  }
}
