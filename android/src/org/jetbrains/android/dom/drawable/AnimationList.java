package org.jetbrains.android.dom.drawable;

import com.intellij.util.xml.DefinesXml;
import org.jetbrains.android.dom.Styleable;

import java.util.List;

@DefinesXml
@Styleable("AnimationDrawable")
public interface AnimationList extends DrawableDomElement {
  List<AnimationListItem> getItems();
}
