package com.android.tools.idea.observable.ui;

import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.core.IntProperty;
import javax.swing.JSpinner;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jetbrains.annotations.NotNull;


// TODO(b/76009501): remove when the old welcome wizard will be removed

/**
 * {@link AbstractProperty} that wraps a {@link JSpinner} and exposes its value.
 */
@Deprecated
public final class DeprecatedSpinnerValueProperty extends IntProperty implements ChangeListener {
  @NotNull private final JSpinner mySpinner;

  public DeprecatedSpinnerValueProperty(@NotNull JSpinner spinner) {
    mySpinner = spinner;
    mySpinner.addChangeListener(this);
  }

  @Override
  protected void setDirectly(@NotNull Integer value) {
    mySpinner.setValue(value);
  }

  @Override
  public void stateChanged(ChangeEvent e) {
    notifyInvalidated();
  }

  @NotNull
  @Override
  public Integer get() {
    return (Integer)mySpinner.getValue();
  }
}

