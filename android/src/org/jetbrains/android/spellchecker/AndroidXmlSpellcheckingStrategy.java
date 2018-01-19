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
package org.jetbrains.android.spellchecker;

import com.android.SdkConstants;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.lint.client.api.DefaultConfiguration;
import com.android.tools.lint.detector.api.LintUtils;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.spellchecker.inspections.BaseSplitter;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.inspections.Splitter;
import com.intellij.spellchecker.inspections.TextSplitter;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.spellchecker.tokenizer.TokenizerBase;
import com.intellij.spellchecker.xml.XmlSpellcheckingStrategy;
import com.intellij.util.Consumer;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.android.dom.converters.*;
import org.jetbrains.android.dom.resources.ResourceNameConverter;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidXmlSpellcheckingStrategy extends XmlSpellcheckingStrategy {
  private final MyResourceReferenceTokenizer myResourceReferenceTokenizer = new MyResourceReferenceTokenizer();

  private final Tokenizer<XmlAttributeValue> myAttributeValueRenamingTokenizer = new Tokenizer<XmlAttributeValue>() {
    @Override
    public void tokenize(@NotNull XmlAttributeValue element, TokenConsumer consumer) {
      consumer.consumeToken(element, true, TextSplitter.getInstance());
    }
  };

  @Override
  public boolean isMyContext(@NotNull PsiElement element) {
    // The AndroidXmlSpellCheckingStrategy completely replaces the
    // default XML spell checking strategy (which happens to be
    // its super class, XmlSpellcheckingStrategy) by always returning
    // true. Since it's registered before the default strategy, this means
    // it always wins.
    //
    // There are two reasons we want to replace it:
    // (1) to specially handle resource references; these should not
    //     be typo-checked since they are not up to the user.
    //     (For local declarations, they'll be shown as typos in the
    //     name attribute in the item definition.
    // (2) to skip typo checking completely in files that are not in
    //     English. When you are editing a string in values-nb, the IDE should
    //     not be flagging those words against an English dictionary.
    //
    // Hardcoding this to English is not ideal, but we don't have a way to
    // check which language the dictionary/dictionaries correspond to,
    // and english.dic is included by default.

    boolean isAndroid = AndroidFacet.getInstance(element) != null;
    if (isAndroid) {
      return true;
    }

    if (isLintConfigElement(element)) {
      return true;
    }

    return false;
  }

  private static boolean isLintConfigElement(@NotNull PsiElement element) {
    // Skip baseline files and lint.xml files
    XmlFile file = PsiTreeUtil.getParentOfType(element, XmlFile.class);
    if (file != null) {
      if (file.getName().equals(DefaultConfiguration.CONFIG_FILE_NAME)) {
        return true;
      }
      XmlTag tag = file.getRootTag();
      if (tag != null) {
        String tagName = tag.getName();
        if (DefaultConfiguration.TAG_LINT.equals(tagName) || SdkConstants.TAG_ISSUES.equals(tagName)) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    if (isAttributeValueContext(element)) {
      return getAttributeValueTokenizer(element);
    }

    if (inEnglish(element)) {
      if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS) {
        // For XML text, we need to use our own tokenizer to properly handle escapes in the XML output in the same
        // way that AAPT would. But first, filter out common scenarios handled by super.getTokenizer before returning
        // the TEXT_TOKENIZER:
        PsiFile file = element.getContainingFile();
        //noinspection InstanceofIncompatibleInterface
        if (file == null || file.getLanguage() instanceof TemplateLanguage) {
          return EMPTY_TOKENIZER;
        }
        PsiElement injection = InjectedLanguageManager.getInstance(element.getProject()).findInjectedElementAt(element.getContainingFile(),
                                                                                                               element.getTextOffset());
        if (injection != null) {
          return EMPTY_TOKENIZER;
        }

        return AAPT_TOKENIZER;
      }

      return super.getTokenizer(element);
    }

    return EMPTY_TOKENIZER;
  }

  @NotNull
  public Tokenizer getAttributeValueTokenizer(PsiElement element) {
    assert element instanceof XmlAttributeValue;

    if (AndroidResourceUtil.isIdDeclaration((XmlAttributeValue)element)) {
      return myAttributeValueRenamingTokenizer;
    }
    PsiElement parent = element.getParent();

    if (parent instanceof XmlAttribute) {
      String value = ((XmlAttribute)parent).getValue();

      if (value != null) {
        GenericAttributeValue domValue = DomManager.getDomManager(parent.getProject()).getDomElement((XmlAttribute)parent);

        if (domValue != null) {
          Converter converter = domValue.getConverter();

          if (converter instanceof ResourceReferenceConverter) {
            return myResourceReferenceTokenizer;
          }
          else if (converter instanceof ConstantFieldConverter || converter instanceof AndroidPermissionConverter) {
            return EMPTY_TOKENIZER;
          }
          else if (converter instanceof ResourceNameConverter || converter instanceof AndroidPackageConverter) {
            return myAttributeValueRenamingTokenizer;
          }
        }
      }
    }
    return super.getTokenizer(element);
  }

  private static boolean isAttributeValueContext(@NotNull PsiElement element) {
    if (!(element instanceof XmlAttributeValue)) {
      return false;
    }
    PsiElement parent = element.getParent();
    parent = parent != null ? parent.getParent() : null;

    if (!(parent instanceof XmlTag)) {
      return false;
    }
    DomElement domElement = DomManager.getDomManager(element.getProject()).getDomElement((XmlTag)parent);
    if (domElement instanceof AndroidDomElement) {
      return inEnglish(element);
    }

    return false;
  }

  /**
   * @return {@code true} if the given element is in an XML file that is in an English resource. Manifest files are considered to be in
   * English, as are resources in base folders (unless a locale is explicitly defined on the root element).
   */
  private static boolean inEnglish(PsiElement element) {
    XmlFile file = PsiTreeUtil.getParentOfType(element, XmlFile.class);
    if (file != null) {
      String name = file.getName();
      if (name.equals(ANDROID_MANIFEST_XML)) {
        return true;
      }
      else if (name.equals("generated.xml")) {
        // Android Studio Workaround for issue https://code.google.com/p/android/issues/detail?id=76715
        // If this a generated file like this:
        //   ${project}/${module}/build/generated/res/generated/{test?}/${flavors}/${build-type}/values/generated.xml
        // ? If so, skip it.
        AndroidFacet facet = AndroidFacet.getInstance(file);
        VirtualFile virtualFile = file.getVirtualFile();
        if (facet != null && facet.requiresAndroidModel() && virtualFile != null) {
          AndroidModel androidModel = facet.getConfiguration().getModel();
          if (androidModel != null && androidModel.isGenerated(virtualFile)) {
            return false;
          }
        }
      }
      else if (isLintConfigElement(element)) {
        // lint config file: should not be spell checked
        return false;
      }
      PsiDirectory dir = file.getParent();
      if (dir != null) {
        String locale = LintUtils.getLocaleAndRegion(dir.getName());
        if (locale == null) {
          locale = getToolsLocale(file);
        }
        return locale == null || locale.startsWith("en") || locale.equals("b+en") || locale.startsWith("b+en+");
      }
    }

    return false;
  }

  @Nullable
  private static String getToolsLocale(XmlFile file) {
    // See if the root element specifies a locale to use
    XmlTag rootTag = file.getRootTag();
    if (rootTag != null) {
      return rootTag.getAttributeValue(ATTR_LOCALE, TOOLS_URI);
    }

    return null;
  }

  private static class MyResourceReferenceTokenizer extends XmlAttributeValueTokenizer {
    @Nullable
    private static AndroidResourceReferenceBase findResourceReference(PsiElement element) {
      for (PsiReference reference : element.getReferences()) {
        if (reference instanceof AndroidResourceReferenceBase) {
          return (AndroidResourceReferenceBase)reference;
        }
      }
      return null;
    }

    @Override
    public void tokenize(@NotNull XmlAttributeValue element, TokenConsumer consumer) {
      AndroidResourceReferenceBase reference = findResourceReference(element);

      if (reference != null) {
        if (reference.getResourceValue().getNamespace() == null) {
          consumer.consumeToken(element, true, TextSplitter.getInstance());
        }
        return;
      }

      // The super implementation already filters out hex color definitions like #001122, but it's limited to RGB colors, not ARGB.
      if (isColorString(element.getValue())) {
        return;
      }

      super.tokenize(element, consumer);
    }
  }

  private static boolean isColorString(@NotNull String s) {
    int length = s.length();
    // #rgb to #aarrggbb
    if (length < 4 || length > 9) {
      return false;
    }

    int i = 0;
    if (s.charAt(i++) != '#') {
      return false;
    }

    for (; i < length; i++) {
      if (!StringUtil.isHexDigit(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static final AaptXmlTextSplitter AAPT_SPLITTER = new AaptXmlTextSplitter();
  private static final Tokenizer<PsiElement> AAPT_TOKENIZER = new TokenizerBase<>(AAPT_SPLITTER);

  /**
   * Splitter which splits XML strings up into text chunks, such that for example "word\nword2" will be
   * be seen as the words "word" and "word2", not "word" and "nword2" as is the case with the PlainTextSplitter.
   */
  public static class AaptXmlTextSplitter extends BaseSplitter {

    @Override
    public void split(@Nullable String text, @NotNull TextRange range, Consumer<TextRange> consumer) {
      if (text == null || StringUtil.isEmpty(text)) {
        return;
      }

      final Splitter ps = PlainTextSplitter.getInstance();

      if (text.indexOf('\\') == -1) {
        ps.split(text, range, consumer);
      } else {
        // For now, just split by the escaped character
        int length = range.getEndOffset();
        int start = range.getStartOffset();
        while (true) {
          int end = text.indexOf('\\', start);
          if (end == -1) {
            end = length;
          }

          ps.split(text, new TextRange(start, end), consumer);

          start = end + 2; // +2: +1 to skip \\, +1 to skip the escaped character
          if (start >= length) {
            break;
          }

          // Ideally we'd also be able to handle a scenario like "Android\'s" and turn this into "Android's" for the
          // spell checker, but that's not possible with the splitter/tokenizer API; they can just segment characters
          // into tokens, not drop characters and combine into a single word.
        }
      }
    }
  }
}
