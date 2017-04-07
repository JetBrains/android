/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.sherpa.scout;

import com.android.tools.sherpa.drawing.ViewTransform;
import android.support.constraint.solver.widgets.ConstraintWidget;
import android.support.constraint.solver.widgets.Guideline;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.util.ArrayList;

/**
 * Main code for inferring the probability of a group
 */
public class ScoutGroupInference {
    private static final boolean DEBUG = false;
    private static Rectangle[] debugDraw; // used for visual debugging
    private static Rectangle[] debugGap; // used for visual debugging
    private static Rectangle debugBestRect; // used for visual debugging

    /**
     * Compute all possible positions for the north, south, east, and west borders of a group
     *
     * @param list  list of widgets
     * @param north fills with the positions of the top of widget
     * @param south fills with the positions of the bottom of the widgets
     * @param west  fills with the positions of the left of the widgets
     * @param east  fills with the positions of the right of the widgets
     */
    private static void allPositions(ScoutWidget[] list, int[] north, int[] south, int[] west,
            int[] east) {

        for (int i = 1; i < list.length; i++) { // do not consider the root
            int k = i - 1;
            north[k] = list[i].mConstraintWidget.getY();
            west[k] = list[i].mConstraintWidget.getX();
            south[k] = list[i].mConstraintWidget.getHeight() + north[k];
            east[k] = list[i].mConstraintWidget.getWidth() + west[k];
        }
    }

    /**
     * This computes a list of widgets that should be in a table
     * TODO support outputting more that one table
     *
     * @param list
     * @return list of widgets to be put into a table
     */
    public static ConstraintWidget[] computeGroups(ScoutWidget[] list) {
        list = removeGuidelines(list);
        Rectangle[] rectangles = widgetsToRectangles(list);
        if (DEBUG) {
            System.out.println("widgets = " + list.length);
        }
        int[] north = new int[list.length - 1];
        int[] south = new int[list.length - 1];
        int[] west = new int[list.length - 1];
        int[] east = new int[list.length - 1];
        int count = 0;
        allPositions(list, north, south, west, east);
        north = Utils.sortUnique(north);
        south = Utils.sortUnique(south);
        east = Utils.sortUnique(east);
        west = Utils.sortUnique(west);
        ArrayList<ScoutCandidateGroup> candidatesList = new ArrayList<>();
        Rectangle groupRectangle = new Rectangle();

        // for every potential bounding rectangle computed a candidate group
        // with some early rejection (to small etc)
        for (int n : north) {
            n -= 1;
            groupRectangle.y = n;
            for (int s : south) {
                s += 1;
                if (n >= s) continue;
                groupRectangle.height = s - n;
                for (int w : west) {
                    w -= 1;
                    groupRectangle.x = w;
                    for (int e : east) {
                        e += 1;
                        if (w >= e) continue;
                        groupRectangle.width = e - w;
                        ScoutCandidateGroup candidateGroup =
                                ScoutCandidateGroup.create(groupRectangle, list, rectangles);
                        if (candidateGroup != null) {
                            candidatesList.add(candidateGroup);
                        }

                    }
                }
            }
        }
        if (DEBUG) {
            System.out.println("found " + candidatesList.size() + " candidates");
        }

        ScoutCandidateGroup[] candidates = candidatesList.toArray(new ScoutCandidateGroup[0]);

        // eliminate candidates based various criteria
        for (int i = 0; i < candidates.length; i++) {
            ScoutCandidateGroup candidate = candidates[i];
            if (candidate == null) {
                continue;
            }
            for (int j = i + 1; j < candidates.length; j++) {
                if (candidates[j] == null) {
                    continue;
                }
                //  if it contain the same objects remove the bigger
                if (candidate.mContainSet.equals(candidates[j].mContainSet)) {
                    if (candidate.mGroupArea > candidates[j].mGroupArea) {
                        candidatesList.remove(candidate);
                        candidates[i] = null;
                    } else {
                        candidatesList.remove(candidates[j]);
                        candidates[j] = null;
                    }
                    continue;
                }

                // if one in the other and the outer has greater % widgets / area remove inner
                if (candidate.contains(candidates[j])) {
                    float outerFraction = candidate.fractionFilled();
                    float innerFraction = candidates[j].fractionFilled();
                    if (outerFraction > innerFraction) {
                        candidatesList.remove(candidates[j]);
                        candidates[j] = null;
                        continue;
                    }
                }

                // if one in the other and the outer has greater % widgets / area remove inner
                if (candidates[j].contains(candidate)) {
                    float outerFraction = candidates[j].fractionFilled();
                    float innerFraction = candidate.fractionFilled();
                    if (outerFraction > innerFraction) {  // outside is a better fit than the inside
                        candidatesList.remove(candidate);
                        candidates[i] = null;
                    }
                }
            }
        }
        if (DEBUG) {
            System.out.println("down to " + candidatesList.size() + " candidates");
        }
        // Compute the gaps slightly expensive so do this after major reduction of candidates
        for (ScoutCandidateGroup candidate : candidatesList) {
            candidate.computeGapAreas();
        }

        // remove ones that are below some threshold
        for (int i = 0; i < candidatesList.size(); ) {
            ScoutCandidateGroup candidate = candidatesList.get(i);
            if (!candidate.viable()) {
                candidatesList.remove(candidate);
            } else {
                i++;
            }
        }
        if (DEBUG) {
            System.out.println("down to " + candidatesList.size() + " candidates");
        }
        debugDraw = new Rectangle[candidatesList.size()];
        count = 0;
        if (candidatesList.isEmpty()) {
            debugDraw = null;
            debugGap = null;
            debugBestRect = null;
            return null;
        }
        ScoutCandidateGroup bestCandidate = candidatesList.get(0);
        float bestRatio = bestCandidate.calcProb();
        for (ScoutCandidateGroup candidate : candidatesList) {
            float ratio = candidate.calcProb();
            if (bestRatio < ratio) {
                bestCandidate = candidate;
                bestRatio = ratio;
            }
        }
        if (DEBUG) {
            debugGap = bestCandidate.computeGaps();
            debugBestRect = bestCandidate.mRect;

            for (ScoutCandidateGroup candidate : candidatesList) {
                String s = (bestCandidate == candidate) ? " *" : " ";

                Utils.fwPrint(" " + candidate.mWest + "," + candidate.mNorth + ",  ", 20);
                Utils.fwPrint(candidate.mRect.width + " x " + candidate.mRect.height + "  ", 20);
                Utils.fwPrint(" Group: " + candidate.mGroupArea, 20);
                Utils.fwPrint(" Widgets: " + candidate.mWidgetArea + "(" + candidate.mCount + ")",
                        20);
                Utils.fwPrint(
                        " Gap: " + candidate.mGapArea + "(" + candidate.computeGaps().length + ")",
                        20);
                System.out.println(" fill " +
                        (100 * candidate.calcProb()) + s);
                debugDraw[count++] = candidate.mRect;
            }
            System.out.println("found " + candidatesList.size() + " big candidates");
        }
        if (bestCandidate.mValidTable) {
            return bestCandidate.buildList(list).toArray(new ConstraintWidget[0]);
        }
        return null;
    }

    /**
     * Take a list of InferWidgets and return a list of Rectangles
     *
     * @param list
     * @return
     */
    private static Rectangle[] widgetsToRectangles(ScoutWidget[] list) {
        Rectangle[] ret = new Rectangle[list.length];
        for (int i = 0; i < ret.length; i++) {
            int x = list[i].mConstraintWidget.getX();
            int y = list[i].mConstraintWidget.getY();
            int w = list[i].mConstraintWidget.getWidth();
            int h = list[i].mConstraintWidget.getHeight();
            ret[i] = new Rectangle(x, y, w, h);
        }
        return ret;
    }

    /**
     * Filter ScoutWidget's of GuideLine objects
     *
     * @param list
     * @return
     */
    private static ScoutWidget[] removeGuidelines(ScoutWidget[] list) {
        ArrayList<ScoutWidget> al = new ArrayList<>();
        for (ScoutWidget aList : list) {
            if (aList.mConstraintWidget instanceof Guideline) {
                continue;
            }
            al.add(aList);
        }
        return al.toArray(new ScoutWidget[al.size()]);
    }

    /**
     * Allow for the visual debugging of candidate rectangles
     * ScoutGroupInference.paintDebug(g,viewMargin,mViewTransform);
     *
     * @param g
     * @param viewMargin
     * @param viewTransform
     */
    public static void paintDebug(Graphics g, int viewMargin, ViewTransform viewTransform) {
        if (!DEBUG) {
            return;
        }
        if (debugDraw != null) {
            g.setColor(Color.GREEN.darker());
            for (Rectangle r : debugDraw) {
                int x = viewMargin + viewTransform.getSwingX(r.x);
                int y = viewMargin + viewTransform.getSwingY(r.y);
                int wid = viewTransform.getSwingDimension(r.width);
                int hei = viewTransform.getSwingDimension(r.height);
                if (r != null) {
                    g.drawRect(x, y, wid, hei);
                }
            }
            g.setColor(Color.RED);
            {
                Rectangle r = debugBestRect;
                int x = viewMargin + viewTransform.getSwingX(r.x);
                int y = viewMargin + viewTransform.getSwingY(r.y);
                int wid = viewTransform.getSwingDimension(r.width);
                int hei = viewTransform.getSwingDimension(r.height);
                if (r != null) {
                    g.drawRect(x, y, wid, hei);
                }
            }
            g.setColor(new Color(0x50909022, true));

            for (Rectangle r : debugGap) {
                int x = viewMargin + viewTransform.getSwingX(r.x);
                int y = viewMargin + viewTransform.getSwingY(r.y);
                int wid = viewTransform.getSwingDimension(r.width);
                int hei = viewTransform.getSwingDimension(r.height);
                if (r != null) {
                    g.fillRect(x, y, wid, hei);
                }
            }
        }
    }

}
