package org.jetbrains.android;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenameXmlAttributeProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidApplicationPackageRenameProcessor extends RenamePsiElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    final PsiFile psiFile = element.getContainingFile();

    if (!(psiFile instanceof XmlFile)) {
      return false;
    }
    final AndroidFacet facet = AndroidFacet.getInstance(psiFile);

    if (facet == null) {
      return false;
    }
    final VirtualFile vFile = psiFile.getVirtualFile();

    if (vFile == null || !vFile.equals(AndroidRootUtil.getManifestFile(facet))) {
      return false;
    }
    return AndroidRenameHandler.isPackageAttributeInManifest(element.getProject(), element);
  }

  @Override
  public void renameElement(PsiElement element, String newName, UsageInfo[] usages, @Nullable RefactoringElementListener listener)
    throws IncorrectOperationException {
    final PsiFile file = element.getContainingFile();

    if (!(file instanceof XmlFile)) {
      return;
    }
    final Map<GenericAttributeValue, PsiClass> attr2class = buildAttr2ClassMap((XmlFile)file);

    new RenameXmlAttributeProcessor().renameElement(element, newName, usages, listener);

    for (Map.Entry<GenericAttributeValue, PsiClass> e : attr2class.entrySet()) {
      //noinspection unchecked
      e.getKey().setValue(e.getValue());
    }
  }

  private static Map<GenericAttributeValue, PsiClass> buildAttr2ClassMap(@NotNull XmlFile file) {
    final Map<GenericAttributeValue, PsiClass> map = new HashMap<GenericAttributeValue, PsiClass>();
    final DomManager domManager = DomManager.getDomManager(file.getProject());

    file.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlAttribute(XmlAttribute attribute) {
        final GenericAttributeValue domAttrValue = domManager.getDomElement(attribute);

        if (domAttrValue != null) {
          final Object value = domAttrValue.getValue();

          if (value instanceof PsiClass) {
            map.put(domAttrValue, (PsiClass)value);
          }
        }
      }
    });
    return map;
  }
}
