package org.jetbrains.android.dom;

import com.android.SdkConstants;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlCompletionExtension;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidXmlCompletionExtension extends XmlCompletionExtension {
  @Nullable
  @Override
  public LookupElement setupAttributeLookupElement(@NotNull XmlTag contextTag,
                                                   @NotNull XmlAttributeDescriptor descriptor,
                                                   @NotNull String name,
                                                   @NotNull LookupElementBuilder elementBuilder) {
    if (!(descriptor instanceof NamespaceAwareXmlAttributeDescriptor)) {
      return null;
    }
    final String namespace = ((NamespaceAwareXmlAttributeDescriptor)descriptor).getNamespace(contextTag);

    if (!SdkConstants.NS_RESOURCES.equals(namespace)) {
      return null;
    }
    final int idx = name.indexOf(':');

    if (idx < 0) {
      return null;
    }
    final String localName = name.substring(idx + 1);

    if (localName.length() == 0) {
      return null;
    }
    int priority = 0;
    final String layoutPrefix = "layout_";

    if (localName.startsWith(layoutPrefix)) {
      final String localSuffix = localName.substring(layoutPrefix.length());

      if (localSuffix.length() > 0) {
        elementBuilder = elementBuilder.withLookupString(localSuffix);
      }
      priority += 100;
    }
    if (descriptor.isRequired()) {
      priority += 100;
    }
    return priority > 0
           ? PrioritizedLookupElement.withPriority(elementBuilder, priority)
           : elementBuilder;
  }

  @Override
  public boolean isMyContext(@NotNull XmlTag context) {
    return AndroidFacet.getInstance(context) != null;
  }
}
