package org.jetbrains.android.dom.resources;

import java.util.List;

public interface Plurals extends ResourceElement {
  List<PluralsItem> getItems();
}
