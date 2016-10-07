package org.jetbrains.android;

import com.android.resources.ResourceFolderType;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.TAG_RESOURCES;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidUsagesTargetProvider implements UsageTargetProvider {
  @Override
  public UsageTarget[] getTargets(Editor editor, PsiFile file) {
    if (editor == null || file == null) {
      return UsageTarget.EMPTY_ARRAY;
    }

    final XmlTag tag = findValueResourceTagInContext(editor, file, false);
    return tag != null
           ? new UsageTarget[]{new PsiElement2UsageTargetAdapter(tag)}
           : UsageTarget.EMPTY_ARRAY;
  }

  @Override
  public UsageTarget[] getTargets(PsiElement psiElement) {
    return UsageTarget.EMPTY_ARRAY;
  }

  /**
   *
   * The rename parameter should be set to true when the method is called from a rename handler.
   * <ul>
   *   <li>Check if file is XML resource file in values/ resource folder, returns null if false</li>
   *   <li>Check whether root tag is &lt;resources&gt;, returns null if false</li>
   *   <li>Check whether element at caret is an XMLToken with type XML_DATA_CHARACTERS, returns null if true</li>
   *   <li>Check whether token at caret is a reference to the parent of the current element, returns null if true</li>
   *   <li>Return XmlTag parent for element at the cursor if exists</li>
   * </ul>
   */
  @Nullable
  public static XmlTag findValueResourceTagInContext(@NotNull Editor editor, @NotNull PsiFile file, boolean rename) {
    if (!(file instanceof XmlFile)) {
      return null;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return null;
    }

    if (!AndroidResourceUtil.isInResourceSubdirectory(file, ResourceFolderType.VALUES.getName())) {
      return null;
    }

    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    // If searching for the XML data, the target should not be the XML tag.
    // This doesn't apply to rename, because AndroidRenameHandler handles it differently.
    // It needs the tag not to be null to call either one of performValueResourceRenaming or performResourceReferenceRenaming methods.
    if (!rename && element instanceof XmlToken && XmlTokenType.XML_DATA_CHARACTERS.equals(((XmlToken)element).getTokenType())) {
      return null;
    }

    final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    // If searching for the parent of a resource, the target shouldn't be the resource tag, but the resource parent instead
    if (element instanceof XmlToken && XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN.equals(((XmlToken)element).getTokenType()) && tag != null) {
      XmlAttribute parentAttribute = tag.getAttribute("parent");
      final String parentValue = parentAttribute != null ? parentAttribute.getValue() : null;
      if (parentValue != null && parentValue.equals(element.getText())) {
        return null;
      }
    }

    final XmlTag rootTag = ((XmlFile)file).getRootTag();
    if (rootTag == null || !TAG_RESOURCES.equals(rootTag.getName())) {
      return null;
    }
    return tag;
  }
}
