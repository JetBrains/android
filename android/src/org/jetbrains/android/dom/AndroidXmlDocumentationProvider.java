package org.jetbrains.android.dom;

import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.idea.javadoc.AndroidJavaDocRenderer;
import com.android.utils.Pair;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.resourceManagers.ValueResourceInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.*;
import static com.intellij.psi.xml.XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER;
import static com.intellij.psi.xml.XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidXmlDocumentationProvider implements DocumentationProvider {
  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (element instanceof LazyValueResourceElementWrapper) {
      final ValueResourceInfo info = ((LazyValueResourceElementWrapper)element).getResourceInfo();
      return "value resource '" + info.getName() + "' [" + info.getContainingFile().getName() + "]";
    }
    return null;
  }

  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (originalElement instanceof XmlToken) {
      XmlToken token = (XmlToken)originalElement;
      if (token.getTokenType() == XML_ATTRIBUTE_VALUE_START_DELIMITER) {
        PsiElement next = token.getNextSibling();
        if (next instanceof XmlToken) {
          token = (XmlToken)next;
        }
      }

      if (token.getTokenType() == XML_ATTRIBUTE_VALUE_TOKEN) {
        String value = token.getText();
        if (value.startsWith(ANDROID_PREFIX)) {
          // TODO: Support framework resources
          return null;
        }
        Pair<ResourceType,String> pair = ResourceRepository.parseResource(value);
        if (pair != null) {
          ResourceType type = pair.getFirst();
          String name = pair.getSecond();
          return generateDoc(originalElement, type, name);
        } else {
          // See if it's in a resource file definition: This allows you to invoke
          // documentation on <string name="cursor_here">...</string>
          // and see the various translations etc of the string
          XmlAttribute attribute = PsiTreeUtil.getParentOfType(originalElement, XmlAttribute.class, false);
          if (attribute != null && ATTR_NAME.equals(attribute.getName())) {
            XmlTag tag = attribute.getParent();
            String typeName = tag.getName();
            if (TAG_ITEM.equals(typeName)) {
              typeName = tag.getAttributeValue(ATTR_TYPE);
              if (typeName == null) {
                return null;
              }
            }
            ResourceType type = ResourceType.getEnum(typeName);
            if (type != null) {
              return generateDoc(originalElement, type, value);
            }
          }
        }
      }
    } else if (originalElement != null && element instanceof LazyValueResourceElementWrapper) {
      LazyValueResourceElementWrapper wrapper = (LazyValueResourceElementWrapper)element;
      ResourceType type = wrapper.getResourceInfo().getType();
      String name = wrapper.getResourceInfo().getName();

      return generateDoc(originalElement, type, name);
    }

    return null;
  }

  @Nullable
  private static String generateDoc(PsiElement originalElement, ResourceType type, String name) {
    Module module = ModuleUtilCore.findModuleForPsiElement(originalElement);
    if (module == null) {
      return null;
    }

    return AndroidJavaDocRenderer.render(module, type, name);
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return null;
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return null;
  }
}
