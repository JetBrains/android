package org.jetbrains.android.refactoring;

import com.android.resources.ResourceType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.converters.AndroidResourceReferenceBase;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

class ViewStyleUsageData implements StyleUsageData {
  private final XmlTag myTag;
  private final GenericAttributeValue<ResourceValue> myStyleAttribute;
  private final AndroidResourceReferenceBase myReference;

  ViewStyleUsageData(@NotNull XmlTag tag,
                     @NotNull GenericAttributeValue<ResourceValue> styleAttribute,
                     @NotNull AndroidResourceReferenceBase reference) {
    myTag = tag;
    myStyleAttribute = styleAttribute;
    myReference = reference;
  }

  @Override
  @Nullable
  public PsiFile getFile() {
    return myTag.getContainingFile();
  }

  @Override
  public void inline(@NotNull Map<AndroidAttributeInfo, String> attributeValues, @Nullable StyleRefData parentStyleRef) {
    for (Map.Entry<AndroidAttributeInfo, String> entry : attributeValues.entrySet()) {
      final AndroidAttributeInfo info = entry.getKey();
      final String localName = info.getName();
      final String namespace = info.getNamespace();

      if (myTag.getAttribute(localName, namespace) == null) {
        myTag.setAttribute(localName, namespace, entry.getValue());
      }
    }
    myStyleAttribute.setValue(parentStyleRef != null
                              ? ResourceValue.referenceTo('@', parentStyleRef.getStylePackage(), ResourceType.STYLE.getName(),
                                                          parentStyleRef.getStyleName())
                              : null);
  }

  @Override
  @NotNull
  public AndroidResourceReferenceBase getReference() {
    return myReference;
  }
}
