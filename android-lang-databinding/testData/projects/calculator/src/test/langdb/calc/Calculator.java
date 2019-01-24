package test.langdb.calc;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.ObservableField;

public final class Calculator {
  public enum Op {
    REPLACE(new OpHandler() {
      @Override
      public double handle(double a, double b) {
        return b;
      }
    }),
    ADD(new OpHandler() {
      @Override
      public double handle(double a, double b) {
        return a + b;
      }
    }),
    SUB(new OpHandler() {
      @Override
      public double handle(double a, double b) {
        return a - b;
      }
    }),
    MUL(new OpHandler() {
      @Override
      public double handle(double a, double b) {
        return a * b;
      }
    }),
    DIV(new OpHandler() {
      @Override
      public double handle(double a, double b) {
        return a / b;
      }
    });

    private final OpHandler handler;

    Op(@NonNull OpHandler handler) {
      this.handler = handler;
    }

    public double handle(double a, double b) {
      return handler.handle(a, b);
    }
  }

  private interface OpHandler {
    double handle(double a, double b);
  }

  private final String m_field = "TEST";
  private final ObservableField<String> display = new ObservableField<>("0");
  private final ObservableBoolean allCleared = new ObservableBoolean(true);

  private final StringBuffer wholePartStr = new StringBuffer();
  private final StringBuffer fracPartStr = new StringBuffer();
  @NonNull private StringBuffer currPartStr = wholePartStr;
  private Op op;
  private double accum;


  public Calculator() {
    clearAll();
    updateDisplay();
  }

  @NonNull
  public String getField() { return m_field; }

  /**
   * The current display - either the current accumulated answer or the current value the user is
   * typing in.
   */
  @NonNull
  public ObservableField<String> getDisplay() { return display; }

  /**
   * Whether or not this calculator is in a purely cleared state (vs. when they just cleared
   * once, which leaves the accumulated value but resets the value the user is editing)
   */
  @NonNull
  public ObservableBoolean getAllCleared() { return allCleared; }

  /**
   * Handle clearing the calculator.
   *
   * Note: The behavior of this method depends on the calculator's current state! If the user is
   * in the middle of modifying the current value, then this just resets it back to 0. Otherwise,
   * this clears everything, resetting the calculator back to a clean state.
   */
  public void clear() {
    if (wholePartStr.length() > 0) {
      clearParts();
    }
    else {
      clearAll();
    }
    updateDisplay();
  }

  private void updateDisplay() {
    if (wholePartStr.length() == 0) {
      display.set("0");
      return;
    }

    if (currPartStr == wholePartStr) {
      display.set(wholePartStr.toString());
      return;
    }

    display.set(wholePartStr.toString() + "." + fracPartStr.toString());
  }

  private void clearParts() {
    wholePartStr.replace(0, wholePartStr.length(), "");
    fracPartStr.replace(0, fracPartStr.length(), "");
    currPartStr = wholePartStr;
    allCleared.set(accum == 0);
  }

  private void clearAll() {
    clearParts();
    op = Op.REPLACE;
    accum = 0;
    allCleared.set(true);
  }

  public void enterDigit(int digit) {
    if (digit == 0 && wholePartStr.length() == 0) {
      return;
    }

    currPartStr.append(digit);
    allCleared.set(false);
    updateDisplay();
  }

  public void enterDecimal() {
    if (currPartStr == fracPartStr) {
      return;
    }

    if (wholePartStr.length() == 0) {
      wholePartStr.append(0); // "0.###" looks better than ".###"
    }
    currPartStr = fracPartStr;
    allCleared.set(false);

    updateDisplay();
  }

  public void setOperation(@NonNull Op op) {
    calculateAnswer();
    this.op = op;
  }

  /**
   * Calculate the answer, and update the calculator's state so it's ready to enter new values.
   */
  public void calculateAnswer() {
    String currValueStr = display.get();
    assert (currValueStr != null);

    if (currValueStr.isEmpty()) {
      currValueStr = "0";
    }

    accum = op.handle(accum, Double.valueOf(currValueStr));
    op = Op.REPLACE;

    long wholePart = (long)accum;
    double fracPart = accum - wholePart;

    if (fracPart == 0) {
      display.set(Long.toString(wholePart));
    }
    else {
      display.set(Double.toString(accum));
    }

    clearParts();
  }
}
