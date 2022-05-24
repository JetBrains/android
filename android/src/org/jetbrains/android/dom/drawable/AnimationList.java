package org.jetbrains.android.dom.drawable;

import com.intellij.util.xml.DefinesXml;
import java.util.List;
import org.jetbrains.android.dom.Styleable;

@DefinesXml
@Styleable("AnimationDrawable")
public interface AnimationList extends DrawableDomElement {
  List<AnimationListItem> getItems();
}
