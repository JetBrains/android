package org.jetbrains.android.spellchecker;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.spellchecker.inspections.TextSplitter;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.spellchecker.xml.XmlSpellcheckingStrategy;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.android.dom.converters.AndroidResourceReferenceBase;
import org.jetbrains.android.dom.converters.ConstantFieldConverter;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidXmlSpellcheckingStrategy extends XmlSpellcheckingStrategy {
  private final MyResourceReferenceTokenizer myResourceReferenceTokenizer = new MyResourceReferenceTokenizer();

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    assert element instanceof XmlAttributeValue;
    final PsiElement parent = element.getParent();

    if (parent instanceof XmlAttribute) {
      final String value = ((XmlAttribute)parent).getValue();

      if (value != null) {
        final GenericAttributeValue domValue = DomManager.getDomManager(
          parent.getProject()).getDomElement((XmlAttribute)parent);

        if (domValue != null) {
          final Converter converter = domValue.getConverter();

          if (converter instanceof ResourceReferenceConverter) {
            return myResourceReferenceTokenizer;
          }
          else if (converter instanceof ConstantFieldConverter) {
            return EMPTY_TOKENIZER;
          }
        }
      }
    }
    return super.getTokenizer(element);
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement element) {
    if (!super.isMyContext(element)) {
      return false;
    }
    if (!(element instanceof XmlAttributeValue)) {
      return false;
    }
    PsiElement parent = element.getParent();
    parent = parent != null ? parent.getParent() : null;

    if (!(parent instanceof XmlTag)) {
      return false;
    }
    final DomElement domElement = DomManager.getDomManager(
      element.getProject()).getDomElement((XmlTag)parent);
    return domElement instanceof AndroidDomElement;
  }

  private static class MyResourceReferenceTokenizer extends XmlAttributeValueTokenizer {
    private static AndroidResourceReferenceBase findResourceReference(PsiElement element) {
      for (PsiReference reference : element.getReferences()) {
        if (reference instanceof AndroidResourceReferenceBase) {
          return (AndroidResourceReferenceBase)reference;
        }
      }
      return null;
    }

    public void tokenize(@NotNull final XmlAttributeValue element, final TokenConsumer consumer) {
      final AndroidResourceReferenceBase reference = findResourceReference(element);

      if (reference != null) {
        if (reference.getResourceValue().getPackage() == null) {
          consumer.consumeToken(element, true, TextSplitter.getInstance());
        }
        return;
      }
      super.tokenize(element, consumer);
    }
  }
}
