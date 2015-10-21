/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.fd;

import com.sun.jdi.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * When running with instant run, some of the internal aspects of the objects
 * are not user friendly. This class wraps an entry on the call stack which
 * when it detects such a case, it will re-route variables and positions to the
 * right place.
 */
public class InstantRunStackFrame implements StackFrame {
  private final StackFrame myFrame;

  /**
   * When an instant method is edited, the new version will translate
   * the "this" variable to an argument called "$this". We use
   * this indication to readjust the variable names.
   */
  private static String INSTANT_RUN_THIS = "$this";

  /**
   * When a class is modified, the new implementation has this suffix.
   */
  private static String INSTANT_RUN_CLASS_SUFFIX = "$override";

  /**
   * When a class is enhanced to support instant run, the redirection
   * part of the method as a special line number assigned to it.
   */
  private static int REDIRECTION_LINE_NUMBER = 0;

  public InstantRunStackFrame(StackFrame frame) {
    myFrame = frame;
  }


  private static class InvalidLocation implements Location {

    private final Location myLocation;

    public InvalidLocation(Location location) {
      myLocation = location;
    }

    @Override
    public ReferenceType declaringType() {
      return myLocation.declaringType();
    }

    @Override
    public Method method() {
      return myLocation.method();
    }

    @Override
    public long codeIndex() {
      return -1;
    }

    @Override
    public String sourceName() throws AbsentInformationException {
      throw new AbsentInformationException();
    }

    @Override
    public String sourceName(String s) throws AbsentInformationException {
      throw new AbsentInformationException();
    }

    @Override
    public String sourcePath() throws AbsentInformationException {
      throw new AbsentInformationException();
    }

    @Override
    public String sourcePath(String s) throws AbsentInformationException {
      throw new AbsentInformationException();
    }

    @Override
    public int lineNumber() {
      return -1;
    }

    @Override
    public int lineNumber(String s) {
      return -1;
    }

    @Override
    public int compareTo(Location location) {
      if (location instanceof InvalidLocation) {
        location = ((InvalidLocation)location).myLocation;
      }
      return myLocation.compareTo(location);
    }

    @Override
    public VirtualMachine virtualMachine() {
      return myLocation.virtualMachine();
    }
  }

  @Override
  public Location location() {
    Location location = myFrame.location();
    if (location.lineNumber() == REDIRECTION_LINE_NUMBER) {
      return new InvalidLocation(location);
    }
    return location;
  }

  @Override
  public ThreadReference thread() {
    return myFrame.thread();
  }

  @Override
  public ObjectReference thisObject() {
    ObjectReference object = myFrame.thisObject();
    if (object == null) {
      // We might be in an edited method
      try {
        LocalVariable newThis = myFrame.visibleVariableByName(INSTANT_RUN_THIS);
        if (newThis != null) {
          Value value = myFrame.getValue(newThis);
          if (value instanceof ObjectReference) {
            return (ObjectReference)value;
          }
        }
      }
      catch (AbsentInformationException e1) {
        // ignore
      }
    } else if (myFrame.location().declaringType().name().endsWith(INSTANT_RUN_CLASS_SUFFIX)) {
      // Avoid seeing the "$change" object's "this".
      return null;
    }
    return object;
  }

  @Override
  public List<LocalVariable> visibleVariables() throws AbsentInformationException {
    List<LocalVariable> variables = myFrame.visibleVariables();
    List<LocalVariable> newVariables = new ArrayList<LocalVariable>(variables.size());
    if (myFrame.location().lineNumber() != REDIRECTION_LINE_NUMBER) {
      for (LocalVariable variable : variables) {
        if (INSTANT_RUN_THIS.equals(variable.name())) continue;
        newVariables.add(variable);
      }
    }
    return newVariables;
  }

  @Override
  public LocalVariable visibleVariableByName(String s) throws AbsentInformationException {
    if (INSTANT_RUN_THIS.equals(s)) {
      return null;
    }
    return myFrame.visibleVariableByName(s);
  }

  @Override
  public Value getValue(LocalVariable localVariable) {
    return myFrame.getValue(localVariable);
  }

  @Override
  public Map<LocalVariable, Value> getValues(List<? extends LocalVariable> list) {
    return myFrame.getValues(list);
  }

  @Override
  public void setValue(LocalVariable localVariable, Value value) throws InvalidTypeException, ClassNotLoadedException {
    myFrame.setValue(localVariable, value);
  }

  @Override
  public List<Value> getArgumentValues() {
    return myFrame.getArgumentValues();
  }

  @Override
  public VirtualMachine virtualMachine() {
    return myFrame.virtualMachine();
  }
}
