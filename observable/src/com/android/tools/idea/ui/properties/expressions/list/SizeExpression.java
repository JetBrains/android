package com.android.tools.idea.ui.properties.expressions.list;

import com.android.tools.idea.ui.properties.collections.ObservableList;
import com.android.tools.idea.ui.properties.expressions.integer.IntExpression;
import org.jetbrains.annotations.NotNull;

/**
 * An expression which returns the size of a list.
 */
public final class SizeExpression extends IntExpression {
  private final ObservableList<?> myList;

  public SizeExpression(@NotNull ObservableList<?> list) {
    super(list);
    myList = list;
  }

  @NotNull
  @Override
  public Integer get() {
    return myList.size();
  }
}
