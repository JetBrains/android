package org.jetbrains.android.resourceManagers;

import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.LocalResourceRepository;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.resources.Item;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public final class ValueResourceInfoImpl implements ValueResourceInfo {
  private final ResourceItem myResource;
  private final VirtualFile myFile;
  private final Project myProject;

  ValueResourceInfoImpl(@NotNull ResourceItem resourceItem, @NotNull VirtualFile file, @NotNull Project project) {
    this.myResource = resourceItem;
    this.myFile = file;
    myProject = project;
  }

  @Override
  @NotNull
  public VirtualFile getContainingFile() {
    return myFile;
  }

  @Override
  @NotNull
  public String getName() {
    return myResource.getName();
  }

  @Override
  @NotNull
  public ResourceType getType() {
    return myResource.getType();
  }

  @Override
  public XmlAttributeValue computeXmlElement() {
    ResourceElement resDomElement = computeDomElement();
    return resDomElement != null ? resDomElement.getName().getXmlAttributeValue() : null;
  }

  @Nullable
  public ResourceElement computeDomElement() {
    PsiFile file = PsiManager.getInstance(myProject).findFile(myFile);
    if (!(file instanceof XmlFile)) {
      return null;
    }

    XmlTag tag = LocalResourceRepository.getItemTag(myProject, myResource);
    if (tag == null) {
      return null;
    }

    DomElement domElement = DomManager.getDomManager(myProject).getDomElement(tag);
    if (!(domElement instanceof ResourceElement)) {
      return null;
    }

    String resType = domElement instanceof Item
                           ? ((Item)domElement).getType().getStringValue()
                           : AndroidCommonUtils.getResourceTypeByTagName(tag.getName());
    if (!getType().getName().equals(resType)) {
      return null;
    }

    ResourceElement resDomElement = (ResourceElement)domElement;
    String resName = ((ResourceElement)domElement).getName().getStringValue();
    return getName().equals(resName) ? resDomElement : null;
  }

  @Override
  public int compareTo(@NotNull ValueResourceInfo other) {
    int delta = AndroidResourceUtil.compareResourceFiles(myFile, other.getContainingFile());
    if (delta != 0) {
      return delta;
    }

    delta = getType().compareTo(other.getType());
    if (delta != 0) {
      return delta;
    }

    return getName().compareTo(other.getName());
  }

  @Override
  public String toString() {
    return "ANDROID_RESOURCE: " + getType() + ", " + getName() + ", " + myFile.getPath();
  }
}
