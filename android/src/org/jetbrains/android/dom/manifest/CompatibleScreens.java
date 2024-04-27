package org.jetbrains.android.dom.manifest;

import com.intellij.util.xml.SubTagList;

import java.util.List;

public interface CompatibleScreens extends ManifestElement {
  @SubTagList("screen")
  List<CompatibleScreensScreen> getScreens();
}
