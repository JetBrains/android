// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.dom.converters;

import com.android.SdkConstants;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import com.intellij.util.xml.impl.ConvertContextFactory;
import com.intellij.util.xml.impl.DomCompletionContributor;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author coyote
 */
public class AndroidResourceReference extends AndroidResourceReferenceBase {
  private final GenericDomValue<ResourceValue> myValue;

  public AndroidResourceReference(@NotNull GenericDomValue<ResourceValue> value,
                                  @NotNull AndroidFacet facet,
                                  @NotNull ResourceValue resourceValue) {
    // Range is calculated in calculateDefaultRangeInElement.
    super(value, null, resourceValue, facet);
    myValue = value;
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    final Converter converter = WrappingConverter.getDeepestConverter(myValue.getConverter(), myValue);
    if (converter instanceof EnumConverter || converter == AndroidDomUtil.BOOLEAN_CONVERTER) {
      if (DomCompletionContributor.isSchemaEnumerated(getElement())) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
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
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    if (newElementName.startsWith(SdkConstants.NEW_ID_PREFIX)) {
      newElementName = AndroidResourceUtil.getResourceNameByReferenceText(newElementName);
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
        newResName = AndroidCommonUtils.getResourceName(resType.getName(), newElementName);
      }
      else {
        newResName = newElementName;
      }
      ResourceValue newValue = ResourceValue.parse(newResName, true, true, false);
      if (newValue == null || newValue.getPrefix() == '\0') {
        // Note: We're using value.getResourceType(), not resType.getName() here, because we want the "+" in the new name
        newValue = ResourceValue.referenceTo(value.getPrefix(), value.getPackage(), value.getResourceType(), newResName);
      }

      myValue.setValue(newValue);
    }
    return myValue.getXmlTag();
  }
}
