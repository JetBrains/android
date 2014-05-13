package org.jetbrains.android.resourceManagers;

import com.android.resources.ResourceType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.resources.Item;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class ValueResourceInfoImpl extends ValueResourceInfoBase {
  private final Project myProject;
  private final int myOffset;

  ValueResourceInfoImpl(@NotNull String name, @NotNull ResourceType type, @NotNull VirtualFile file, @NotNull Project project, int offset) {
    super(name, type, file);
    myProject = project;
    myOffset = offset;
  }

  @Override
  public XmlAttributeValue computeXmlElement() {
    final ResourceElement resDomElement = computeDomElement();
    return resDomElement != null ? resDomElement.getName().getXmlAttributeValue() : null;
  }

  @Nullable
  public ResourceElement computeDomElement() {
    final PsiFile file = PsiManager.getInstance(myProject).findFile(myFile);

    if (!(file instanceof XmlFile)) {
      return null;
    }
    final XmlTag tag = PsiTreeUtil.findElementOfClassAtOffset(file, myOffset, XmlTag.class, true);

    if (tag == null) {
      return null;
    }
    final DomElement domElement = DomManager.getDomManager(myProject).getDomElement(tag);
    if (!(domElement instanceof ResourceElement)) {
      return null;
    }
    final String resType = domElement instanceof Item
                           ? ((Item)domElement).getType().getStringValue()
                           : AndroidCommonUtils.getResourceTypeByTagName(tag.getName());

    if (!myType.getName().equals(resType)) {
      return null;
    }
    final ResourceElement resDomElement = (ResourceElement)domElement;
    final String resName = ((ResourceElement)domElement).getName().getStringValue();
    return myName.equals(resName) ? resDomElement : null;
  }

  @Override
  protected int getSortingRank() {
    return 1;
  }

  @Override
  public int compareTo(@NotNull ValueResourceInfo other) {
    int delta = super.compareTo(other);
    if (delta != 0) {
      return delta;
    }
    assert other instanceof ValueResourceInfoImpl; // otherwise sorting rank should have ensured non-zero delta
    return myOffset - ((ValueResourceInfoImpl)other).myOffset;
  }
}
