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
package org.jetbrains.android.dom.converters;

import com.android.SdkConstants;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.utils.SdkUtils;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.EnumConverter;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.WrappingConverter;
import com.intellij.util.xml.impl.ConvertContextFactory;
import com.intellij.util.xml.impl.DomCompletionContributor;
import com.intellij.util.xml.impl.DomManagerImpl;
import com.intellij.util.xml.impl.GenericDomValueReference;
import java.util.ArrayList;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import com.android.tools.idea.res.IdeResourcesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidResourceReference extends AndroidResourceReferenceBase {
  private final GenericDomValue<ResourceValue> myValue;
  private boolean myIncludeDynamicFeatures;

  public AndroidResourceReference(@NotNull GenericDomValue<ResourceValue> value,
                                  @NotNull AndroidFacet facet,
                                  @NotNull ResourceValue resourceValue,
                                  boolean includeDynamicFeatures) {
    // Range is calculated in calculateDefaultRangeInElement.
    super(value, null, resourceValue, facet);
    myValue = value;
    myIncludeDynamicFeatures = includeDynamicFeatures;
  }

  @Override
  public boolean includeDynamicFeatures() {
    return myIncludeDynamicFeatures;
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    final Converter converter = getConverter();
    if (converter instanceof EnumConverter || converter == AndroidDomUtil.BOOLEAN_CONVERTER) {
      if (DomCompletionContributor.isSchemaEnumerated(getElement())) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    if (converter instanceof ResolvingConverter) {
      final ResolvingConverter resolvingConverter = (ResolvingConverter)converter;
      ArrayList<Object> result = new ArrayList<>();
      final ConvertContext convertContext = ConvertContextFactory.createConvertContext(myValue);
      for (Object variant : resolvingConverter.getVariants(convertContext)) {
        String name = converter.toString(variant, convertContext);
        if (name != null) {
          result.add(ElementPresentationManager.getInstance().createVariant(variant, name, resolvingConverter.getPsiElement(variant)));
        }
      }
      return result.toArray();
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    if (newElementName.startsWith(SdkConstants.NEW_ID_PREFIX)) {
      newElementName = IdeResourcesUtil.getResourceNameByReferenceText(newElementName);
    }
    ResourceValue value = myValue.getValue();
    assert value != null;
    final ResourceType resType = value.getType();
    if (resType != null && newElementName != null) {
      // todo: do not allow new value resource name to contain dot, because it is impossible to check if it file or value otherwise

      final String newResName;
      // Does renamed resource point to a file?
      ResourceFolderType folderType = FolderTypeRelationship.getNonValuesRelatedFolder(resType);
      if (folderType != null && newElementName.contains(".")) {
        // If it does, we need to chop off its extension when inserting the new value.
        newResName = SdkUtils.fileNameToResourceName(newElementName);
      }
      else {
        newResName = newElementName;
      }
      ResourceValue newValue = ResourceValue.parse(newResName, true, true, false);
      if (newValue == null || newValue.getPrefix() == '\0') {
        // Note: We're using value.getResourceType(), not resType.getName() here, because we want the "+" in the new name
        newValue = ResourceValue.referenceTo(value.getPrefix(), value.getPackage(), value.getResourceType(), newResName);
      }

      Converter converter = getConverter();

      if (converter != null) {
        @SuppressWarnings("unchecked") // These references are only created by converters that can handle ResourceValues.
        String newText = converter.toString(newValue, createConvertContext());
        if (newText != null) {
          return super.handleElementRename(newText);
        }
      }
    }
    return myValue.getXmlTag();
  }

  /**
   * Gets the {@link Converter} for {@link #myValue}.
   *
   * @see GenericDomValueReference#getConverter()
   */
  @Nullable
  private Converter getConverter() {
    return WrappingConverter.getDeepestConverter(myValue.getConverter(), myValue);
  }

  /**
   * Creates a {@link ConvertContext} for {@link #myValue}.
   *
   * @see GenericDomValueReference#getConvertContext()
   */
  @NotNull
  private ConvertContext createConvertContext() {
    return ConvertContextFactory.createConvertContext(DomManagerImpl.getDomInvocationHandler(myValue));
  }
}
