package org.jetbrains.android;

import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.tools.idea.res.psi.ResourceReferencePsiElement.RESOURCE_CONTEXT_ELEMENT;

import com.android.SdkConstants;
import com.android.annotations.concurrency.AnyThread;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.psi.ResourceReferencePsiElement;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import org.jetbrains.android.facet.AndroidFacet;
import com.android.tools.idea.res.IdeResourcesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * When performing FindUsages or Refactor Renaming on Android Resources, further processing is performed:
 * <ul>
 *     <li>A {@link ResourceReferencePsiElement} is created based on the selected element.</li>
 *     <li>A `context` element is attached to the {@link ResourceReferencePsiElement} to later find the relevant Resource Repository.</li>
 *     <li>In some XML files, the caret is moved to resolve to the nearby Resource Reference, as described in
 *     {@link #findValueResourceTagInContext}.</li>
 * </ul>
 */
public class AndroidUsagesTargetProvider implements UsageTargetProvider {

  @AnyThread
  @Override
  public UsageTarget[] getTargets(@NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement contextElement = file.findElementAt(editor.getCaretModel().getOffset());
    if (contextElement == null) {
      return UsageTarget.EMPTY_ARRAY;
    }
    PsiElement targetElement = TargetElementUtil.findTargetElement(editor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    ResourceReferencePsiElement resourceReferencePsiElement = null;
    if (targetElement != null) {
      resourceReferencePsiElement = ResourceReferencePsiElement.create(targetElement);
    }
    if (resourceReferencePsiElement == null) {
      // The user has selected an element that does not resolve to a resource, check whether they have selected something nearby and if we
      // can assume the correct resource if any. The allowed matches are:
      // XmlTag of a values resource. eg. <col${caret}or name="... />
      // XmlValue of a values resource if it is not a reference to another resource. eg. <color name="foo">#12${caret}3456</color>
      resourceReferencePsiElement = IdeResourcesUtil.getResourceElementFromSurroundingValuesTag(contextElement);
    }
    if (resourceReferencePsiElement == null) {
      return UsageTarget.EMPTY_ARRAY;
    }
    resourceReferencePsiElement.putCopyableUserData(RESOURCE_CONTEXT_ELEMENT, contextElement);
    return new UsageTarget[]{new PsiElement2UsageTargetAdapter(resourceReferencePsiElement)};
  }

  @AnyThread
  @Nullable
  @Override
  public UsageTarget[] getTargets(@NotNull PsiElement psiElement) {
    if (psiElement instanceof ResourceReferencePsiElement) {
      return UsageTarget.EMPTY_ARRAY;
    }
    ResourceReferencePsiElement referencePsiElement = ResourceReferencePsiElement.create(psiElement);
    if (referencePsiElement == null) {
      return UsageTarget.EMPTY_ARRAY;
    }
    referencePsiElement.putCopyableUserData(RESOURCE_CONTEXT_ELEMENT, psiElement);
    return new UsageTarget[]{new PsiElement2UsageTargetAdapter(referencePsiElement)};
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

    if (!IdeResourcesUtil.isInResourceSubdirectory(file, ResourceFolderType.VALUES.getName())) {
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

    // For Style Item tags, we want to find usages based on the result of TargetElementUtil rather that the surrounding XmlTag, except for
    // renaming where the legacy pipeline is required to still function.
    XmlTag parentTag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (tag != null &&
        parentTag != null &&
        SdkConstants.TAG_ITEM.equals(tag.getName()) &&
        SdkConstants.TAG_STYLE.equals(parentTag.getName()) &&
        !rename) {
      return null;
    }
    final XmlTag rootTag = ((XmlFile)file).getRootTag();
    if (rootTag == null || !TAG_RESOURCES.equals(rootTag.getName())) {
      return null;
    }
    return tag;
  }
}
