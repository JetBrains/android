package android.app;

import util.*;

public class ActivityThread {
  private int myVariable;

    public void perform(int param) {
      myVariable = param + 1;
      Activity a = new Activity();
      UtilClass.utilMethod();
    }
}
