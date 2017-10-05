/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.idea.uibuilder.scout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Generates the list of valid constraint sets for a given list of widgets and returns
 * the best
 */

public class ConstraintSetGenerator {

  private int myNumberOfWidgets;
  private ScoutWidget[] myWidgetRecs;
  private ArrayList<WidgetPossibleConnections> myConnectionList;
  private ArrayList<ArrayList<ConstrainedWidget>> myValidWidgets;
  private ArrayList<ConstraintSet> myConstraintSets;
  public int myParentWidth = 1000;
  public int myParentHeight = 900;

  public ConstraintSetGenerator(ScoutWidget[] rectangles) {
    this.myNumberOfWidgets = rectangles.length - 1;
    this.myWidgetRecs = rectangles;
  }

  /**
   * Class to generate all the combinations of length myWidgetNumber
   * in a prioritized order
   * For position i, the maximum value is myMaxPhases[i]
   */
  private class CombinationGenerator {
    private ArrayList<int[]> myGenerated;
    private int[] myMaxPhases;
    private int myPhase;
    private int myWidgetNumber;
    private int myCurrent;

    public CombinationGenerator(int widgetNumber, int[] maxPhases) {
      myPhase = 1;
      myWidgetNumber = widgetNumber;
      myCurrent = 0;
      myMaxPhases = maxPhases;
      generateFirstPhase();
    }

    private void generateFirstPhase() {
      myGenerated = new ArrayList<>();
      myGenerated.add(new int[myWidgetNumber]);
      while (true) {
        int[] tempComb = myGenerated.get(myGenerated.size() - 1).clone();
        boolean carry = true;
        int pos = 0;
        while (carry) {
          if (pos == myWidgetNumber) {
            break;
          }
          if (tempComb[pos] == 1) {
            tempComb[pos] = 0;
            pos++;
          }
          else if (myMaxPhases[pos] != 1) {
            tempComb[pos] = 1;
            carry = false;
          }
        }
        if (carry == true) {
          break;
        }
        myGenerated.add(tempComb);
      }
    }

    public int[] nextPhasedCombination() {
      if (myCurrent > myGenerated.size() - 1) {
        generateNewPhase();
      }
      int[] comb = myGenerated.size() > 0 ? myGenerated.get(myCurrent) : null;
      myCurrent++;
      return comb;
    }

    public void generateNewPhase() {
      myPhase++;
      ArrayList<int[]> newGen = new ArrayList<>(myGenerated);
      ArrayList<int[]> aux = new ArrayList<>();
      for (int i = 0; i < myWidgetNumber; i++) {
        if (myMaxPhases[i] <= myPhase) {
          continue;
        }
        for (int[] existing : newGen) {
          int[] temp = existing.clone();
          temp[i] = myPhase;
          if (!aux.stream().anyMatch(
            p -> Arrays.equals(p, temp))) {
            aux.add(temp);
          }
        }
        newGen.addAll(aux);
        aux.clear();
      }
      newGen.subList(0, myGenerated.size()).clear();
      myGenerated = newGen;
      myCurrent = 0;
    }
  }

  /**
   * For every widget it generates the list of possible valid connection and
   * then generates all valid constraint sets.
   *
   * @return best constraint set
   */
  public ConstraintSet findConstraintSet() {
    this.myConnectionList = new ArrayList<WidgetPossibleConnections>();
    for (int i = 1; i < this.myWidgetRecs.length; i++) {
      WidgetPossibleConnections possibleConnections = new WidgetPossibleConnections(this.myWidgetRecs[i]);
      this.myConnectionList.add(possibleConnections);
      if (!possibleConnections.getWidget().isGuideline()) {
        possibleConnections.generateAllConnections(this.myWidgetRecs);
      }
    }

    this.myValidWidgets = new ArrayList<ArrayList<ConstrainedWidget>>();
    ArrayList<ConstrainedWidget> tempValid;
    for (WidgetPossibleConnections widget : this.myConnectionList) {
      tempValid = getValidConnectionCombinations(widget);
      Collections.sort(tempValid, (a, b) -> b.compareTo(a));
      myValidWidgets.add(tempValid);
    }

    generateConstraintSets();
    for (ConstraintSet set : myConstraintSets) {
      set.calculateError();
    }
    Collections.sort(myConstraintSets, (a, b) -> b.compareTo(a));
    return myConstraintSets.get(0);
  }

  /**
   * For debugging purposes, prints the list of potential connections for each widget
   */
  void printLists() {
    for (WidgetPossibleConnections wid : this.myConnectionList) {
      System.out.println(wid.toString());
    }
  }

  /**
   * Gets the valid bounded widget forms and generates all valid constrained sets
   * that don't generate loops. Returns the one with the smallest margin sum.
   */
  void generateConstraintSets() {
    myConstraintSets = new ArrayList<ConstraintSet>();

    int[] minValid = new int[myNumberOfWidgets];
    double totalCombinations = 1;
    for (int i = 0; i < myNumberOfWidgets; i++) {
      totalCombinations *= myValidWidgets.get(i).size();
      minValid[i] = myValidWidgets.get(i).size();
    }

    CombinationGenerator gen = new CombinationGenerator(myNumberOfWidgets, minValid);

    long startTime = System.currentTimeMillis();
    long totalTime = 0;
    double explored = 0;

    int[] combination = gen.nextPhasedCombination();
    ArrayList<ConstraintSet> generated = new ArrayList<>();
    while (combination != null) {
      generated.add(new ConstraintSet(combination, myValidWidgets, myWidgetRecs[0]));
      totalTime = System.currentTimeMillis() - startTime;
      if (totalTime > 2000) {
        break;
      }
      combination = gen.nextPhasedCombination();
    }
    explored = generated.size() / totalCombinations;
    System.out.println("Percentage of combinations explored: " + Double.toString(explored));
    for (ConstraintSet tempSet : generated) {
      if (tempSet.validate()) {
        myConstraintSets.add(tempSet);
      }
    }
  }

  /**
   * For a widget, generates all the valid bounded forms based on its possible
   * connection lists
   *
   * @param widget
   * @return list of constrained widgets
   */
  ArrayList<ConstrainedWidget> getValidConnectionCombinations(WidgetPossibleConnections possibleConnections) {
    ArrayList<ConstrainedWidget> validWidgets = new ArrayList<>();

    if (possibleConnections.getWidget().isGuideline()) {
      ConstrainedWidget conWidget = new ConstrainedWidget(possibleConnections.getWidget());
      validWidgets.add(conWidget);
      return validWidgets;
    }

    for (Connection base : possibleConnections.baseline) {
      for (Connection south : possibleConnections.south) {
        for (Connection east : possibleConnections.east) {
          for (Connection west : possibleConnections.west) {
            for (Connection north : possibleConnections.north) {
              if (east.destWidget() == Connection.NO_CONNECTION &&
                  west.destWidget() == Connection.NO_CONNECTION) {
                continue;
              }
              if (base.destWidget() == Connection.NO_CONNECTION &&
                  north.destWidget() == Connection.NO_CONNECTION &&
                  south.destWidget() == Connection.NO_CONNECTION) {
                continue;
              }
              if (base.destWidget() != Connection.NO_CONNECTION &&
                  (north.destWidget() != Connection.NO_CONNECTION ||
                   south.destWidget() != Connection.NO_CONNECTION)) {
                continue;
              }
              ConstrainedWidget conWidget = new ConstrainedWidget(north, south, east, west, base, possibleConnections.getWidget());
              validWidgets.add(conWidget);
            }
          }
        }
      }
    }
    return validWidgets;
  }
}