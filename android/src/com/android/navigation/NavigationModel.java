/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.navigation;

import com.android.annotations.NonNull;

import java.util.ArrayList;

public class NavigationModel {
  public static class Event {
    public enum Operation {INSERT, UPDATE, DELETE}

    public final Operation operation;
    public final Class<?> operandType;

    private Event(@NonNull Operation operation, @NonNull Class operandType) {
      this.operation = operation;
      this.operandType = operandType;
    }

    private static Event of(@NonNull Operation operation, @NonNull Class operandType) {
      return new Event(operation, operandType);
    }

    public static Event insert(@NonNull Class operandType) {
      return of(Operation.INSERT, operandType);
    }

    public static Event update(@NonNull Class operandType) {
      return of(Operation.UPDATE, operandType);
    }

    public static Event delete(@NonNull Class operandType) {
      return of(Operation.DELETE, operandType);
    }
  }

  private final EventDispatcher<Event> listeners = new EventDispatcher<Event>();

  private final ArrayList<State> states = new ArrayList<State>();
  private final ArrayList<Transition> transitions = new ArrayList<Transition>();

  // todo change return type to List<State>
  public ArrayList<State> getStates() {
    return states;
  }

  public ArrayList<Transition> getTransitions() {
    return transitions;
  }

  public void addState(State state) {
    states.add(state);
    listeners.notify(Event.insert(State.class));
  }

  public void removeState(State state) {
    states.remove(state);
    for (Transition t : new ArrayList<Transition>(transitions)) {
      if (t.getSource().getState() == state || t.getDestination().getState() == state) {
        remove(t);
      }
    }
    listeners.notify(Event.delete(State.class));
  }

  private void updateStates(State state) {
    if (!states.contains(state)) {
      states.add(state);
    }
  }

  public boolean add(Transition transition) {
    boolean result = transitions.add(transition);
    // todo remove this
    updateStates(transition.getSource().getState());
    updateStates(transition.getDestination().getState());
    listeners.notify(Event.insert(Transition.class));
    return result;
  }

  public boolean remove(Transition transition) {
    boolean result = transitions.remove(transition);
    listeners.notify(Event.delete(Transition.class));
    return result;
  }

  public EventDispatcher<Event> getListeners() {
    return listeners;
  }

  // todo either bury the superclass's API or re-implement all of its destructive methods to post an update event
}
