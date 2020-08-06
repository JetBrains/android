package org.jetbrains.android.dom.layout;

import static com.android.SdkConstants.VIEW_FRAGMENT;

import com.android.resources.ResourceFolderType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.SingleRootResourceDomFileDescription;
import org.jetbrains.annotations.NotNull;

public class FragmentLayoutDomFileDescription extends SingleRootResourceDomFileDescription<Fragment> {

  public FragmentLayoutDomFileDescription() {
    super(Fragment.class, VIEW_FRAGMENT, ResourceFolderType.LAYOUT);
  }

  static boolean hasFragmentRootTag(@NotNull XmlFile file) {
    final XmlTag rootTag = file.getRootTag();
    return rootTag != null && VIEW_FRAGMENT.equals(rootTag.getName());
  }
}
