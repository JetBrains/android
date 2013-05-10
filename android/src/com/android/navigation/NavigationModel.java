package com.android.navigation;

import java.util.ArrayList;

public class NavigationModel extends ArrayList<Transition> {
  private static final Void NON_EVENT = null;

  public final EventDispatcher<Void> listeners = new EventDispatcher<Void>();

  @Override
  public boolean add(Transition transition) {
    boolean result = super.add(transition);
    listeners.notify(NON_EVENT);
    return result;
  }

  @Override
  public boolean remove(Object o) {
    boolean result = super.remove(o);
    listeners.notify(NON_EVENT);
    return result;
  }

  // todo either bury the superclass's API or re-implement all of its destructive methods to post an update event
}
