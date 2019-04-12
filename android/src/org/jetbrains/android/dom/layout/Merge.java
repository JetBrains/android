package org.jetbrains.android.dom.layout;

import com.intellij.util.xml.DefinesXml;
import java.util.List;

/**
 * The merge tag contains a collection of {@link View}s, essentially a stub-in replacement for a
 * view container, like a linear layout, etc. (See also {@link LayoutElement})
 * <p>
 * If an external layout {@link Include}s a layout rooted by a merge tag, the merge tag's children will
 * be added directly into the parent container that they were included from.
 * <p>
 * See also: <a href="https://developer.android.com/training/improving-layouts/reusing-layouts#Merge">Official docs</a>
 */
@DefinesXml
public interface Merge extends LayoutElement {
  List<View> getViews();
}
