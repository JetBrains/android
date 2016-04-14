package org.jetbrains.android.dom.layout;

import com.android.tools.idea.rendering.PsiDataBindingResourceItem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.AndroidDomExtender;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.*;

public class AndroidLayoutUtil {
  private AndroidLayoutUtil() {
  }

  @NotNull
  public static List<String> getPossibleRoots(@NotNull AndroidFacet facet) {
    final List<String> result = new ArrayList<String>();
    result.add(VIEW_TAG);
    result.add(VIEW_MERGE);
    result.add(VIEW_FRAGMENT);
    result.addAll(AndroidDomUtil.removeUnambiguousNames(AndroidDomExtender.getViewClassMap(facet)));
    result.remove(VIEW);
    result.add(TAG_LAYOUT);
    return result;
  }

  @Nullable("invalid type")
  public static String getAlias(Import anImport) {
    String aliasValue = null;
    String typeValue = null;
    GenericAttributeValue<String> alias = anImport.getAlias();
    if (alias != null && alias.getXmlAttributeValue() != null) {
      aliasValue = alias.getXmlAttributeValue().getValue();
    }
    GenericAttributeValue<PsiClass> type = anImport.getType();
    if (type != null) {
      XmlAttributeValue value = type.getXmlAttributeValue();
      if (value != null) {
        typeValue = value.getValue();
      }
    }
    return getAlias(typeValue, aliasValue);
  }

  public static String getAlias(@NotNull PsiDataBindingResourceItem anImport) {
    return getAlias(anImport.getTypeDeclaration(), anImport.getExtra(ATTR_ALIAS));
  }

  private static String getAlias(@Nullable String type, @Nullable String alias) {
    if (alias != null || type == null) {
      return alias;
    }
    int i = type.lastIndexOf('.');
    int d = type.lastIndexOf('$');
    i = i > d ? i : d;
    if (i < 0) {
      return type;
    }
    // Return null in case of an invalid type.
    return type.length() > i + 1 ? type.substring(i + 1) : null;
  }
}
