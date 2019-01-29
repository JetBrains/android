package com.android.tools.profilers;

/**
 * This interface is used for any {@link JList} that uses the {@link DragAndDropListModel}. The list model relies on a unique identifier
 * for each object in the list.
 */
public interface DragAndDropModelListElement {

  /**
   * @return A unique id that helps identify this element in the {@link DragAndDropListModel}
   */
  int getId();
}
