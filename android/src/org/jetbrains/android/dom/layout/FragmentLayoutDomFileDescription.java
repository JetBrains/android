package org.jetbrains.android.dom.layout;

import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.VIEW_FRAGMENT;

public class FragmentLayoutDomFileDescription extends LayoutDomFileDescription<Fragment> {

  public FragmentLayoutDomFileDescription() {
    super(Fragment.class, VIEW_FRAGMENT);
  }

  @Override
  public boolean isMyFile(@NotNull XmlFile file, @Nullable Module module) {
    return super.isMyFile(file, module) && hasFragmentRootTag(file);
  }

  static boolean hasFragmentRootTag(@NotNull XmlFile file) {
    final XmlTag rootTag = file.getRootTag();
    return rootTag != null && VIEW_FRAGMENT.equals(rootTag.getName());
  }
}

