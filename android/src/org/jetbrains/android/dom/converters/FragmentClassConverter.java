package org.jetbrains.android.dom.converters;

import static com.android.SdkConstants.CLASS_FRAGMENT;
import static com.android.AndroidXConstants.CLASS_V4_FRAGMENT;

public class FragmentClassConverter extends PackageClassConverter {
  public FragmentClassConverter() {
    super(CLASS_V4_FRAGMENT.newName(), CLASS_FRAGMENT, CLASS_V4_FRAGMENT.oldName());
  }
}
