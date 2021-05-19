package org.jetbrains.android.refactoring;

import static org.jetbrains.android.dom.AndroidResourceDomFileDescription.isFileInResourceFolderType;

import com.android.resources.ResourceFolderType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AndroidBaseLayoutRefactoringAction extends AndroidBaseXmlRefactoringAction {

  @Override
  protected boolean isEnabledForTags(@NotNull XmlTag[] tags) {
    for (XmlTag tag : tags) {
      if (getLayoutViewElement(tag) == null) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected boolean isMyFile(PsiFile file) {
    return file instanceof XmlFile && isFileInResourceFolderType((XmlFile)file, ResourceFolderType.LAYOUT);
  }

  @Nullable
  public static LayoutViewElement getLayoutViewElement(@NotNull XmlTag tag) {
    final DomElement domElement = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
    return domElement instanceof LayoutViewElement
           ? (LayoutViewElement)domElement
           : null;
  }
}
