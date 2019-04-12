package org.jetbrains.android.dom.converters;

import static com.android.SdkConstants.CLASS_FRAGMENT;
import static com.android.SdkConstants.CLASS_V4_FRAGMENT;

/**
 * @author Eugene.Kudelevsky
 */
public class FragmentClassConverter extends PackageClassConverter {
  public FragmentClassConverter() {
    super(CLASS_FRAGMENT, CLASS_V4_FRAGMENT.oldName(), CLASS_V4_FRAGMENT.newName());
  }
}
