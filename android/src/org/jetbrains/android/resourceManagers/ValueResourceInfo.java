package org.jetbrains.android.resourceManagers;

import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public interface ValueResourceInfo extends Comparable<ValueResourceInfo> {
  @Nullable
  XmlAttributeValue computeXmlElement();

  @NotNull
  ResourceItem getResource();

  @NotNull
  default String getName() {
    return getResource().getName();
  }

  @NotNull
  default ResourceType getType() {
    return getResource().getType();
  }

  @NotNull
  VirtualFile getContainingFile();
}
