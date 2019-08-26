package org.jetbrains.android.resourceManagers;

import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.utils.HashCodes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.resources.Attr;
import org.jetbrains.android.dom.resources.Item;
import org.jetbrains.android.dom.resources.ResourceElement;
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

  public ValueResourceInfoImpl(@NotNull ResourceItem resourceItem, @NotNull VirtualFile file, @NotNull Project project) {
    this.myResource = resourceItem;
    this.myFile = file;
    myProject = project;
  }

  @Override
  @NotNull
  public ResourceItem getResource() {
    return myResource;
  }

  @Override
  @NotNull
  public VirtualFile getContainingFile() {
    return myFile;
  }

  @Override
  @Nullable
  public XmlAttributeValue computeXmlElement() {
    if (myResource.getType().equals(ResourceType.ATTR)) {
      Attr attrElement = computeAttrElement();
      return attrElement != null ? attrElement.getName().getXmlAttributeValue() : null;
    }
    else {
      ResourceElement resDomElement = computeDomElement();
      return resDomElement != null ? resDomElement.getName().getXmlAttributeValue() : null;
    }
  }

  @Nullable
  private Attr computeAttrElement() {
    PsiFile file = PsiManager.getInstance(myProject).findFile(myFile);
    if (!(file instanceof XmlFile)) {
      return null;
    }

    XmlTag tag = AndroidResourceUtil.getItemTag(myProject, myResource);
    if (tag == null) {
      return null;
    }

    DomElement domElement = DomManager.getDomManager(myProject).getDomElement(tag);
    if (!(domElement instanceof Attr)) {
      return null;
    }
    ResourceReference resourceReference = ((Attr)domElement).getName().getValue();
    if (resourceReference == null) {
      return null;
    }
    String resName = resourceReference.getName();
    return getName().equals(resName) ? (Attr)domElement : null;
  }

  @Nullable
  public ResourceElement computeDomElement() {
    PsiFile file = PsiManager.getInstance(myProject).findFile(myFile);
    if (!(file instanceof XmlFile)) {
      return null;
    }

    XmlTag tag = AndroidResourceUtil.getItemTag(myProject, myResource);
    if (tag == null) {
      return null;
    }

    DomElement domElement = DomManager.getDomManager(myProject).getDomElement(tag);
    if (!(domElement instanceof ResourceElement)) {
      return null;
    }

    String resType = domElement instanceof Item
                     ? ((Item)domElement).getType().getStringValue()
                     : ResourceType.fromXmlTagName(tag.getName()).getName();
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
  public boolean equals(Object obj) {
    if (obj instanceof ValueResourceInfo) {
      ValueResourceInfo other = (ValueResourceInfo) obj;
      return myFile.equals(other.getContainingFile()) &&
             getType().equals(other.getType()) &&
             getName().equals(other.getName());
    }
    return super.equals(obj);
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(myFile.hashCode(), getType().hashCode(), getName().hashCode());
  }

  @Override
  public String toString() {
    return "ANDROID_RESOURCE: " + getType() + ", " + getName() + ", " + myFile.getPath();
  }
}
