package com.android.navigation;

import java.util.ArrayList;

public class EventDispatcher<E> extends ArrayList<Listener<E>> implements Listener<E> {
  @Override
  public void notify(E event) {
    for (Listener<E> listener : this) {
      listener.notify(event);
    }
  }
}
