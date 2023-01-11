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
package com.android.tools.idea.uibuilder.handlers.constraint.model;

import java.util.ArrayList;

/**
 * Guideline
 */
public class Guideline extends ConstraintWidget {
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;

    private ConstraintAnchor mAnchor = mTop;
    private int mOrientation = HORIZONTAL;

    private final Rectangle mHead = new Rectangle();

  public Guideline() {
        mAnchors.clear();
        mAnchors.add(mAnchor);
        final int count = mListAnchors.length;
        for (int i = 0; i < count; i++) {
            mListAnchors[i] = mAnchor;
        }
    }

    public Rectangle getHead() {
      int headSize = 8;
      mHead.setBounds(getDrawX() - headSize, getDrawY() - 2 * headSize, 2 * headSize,
                      2 * headSize);
        if (getOrientation() == HORIZONTAL) {
            mHead.setBounds(getDrawX() - 2 * headSize,
                            getDrawY() - headSize,
                            2 * headSize, 2 * headSize);
        }
        return mHead;
    }

    public void setOrientation(int orientation) {
        if (mOrientation == orientation) {
            return;
        }
        mOrientation = orientation;
        mAnchors.clear();
        if (mOrientation == VERTICAL) {
            mAnchor = mLeft;
        } else {
            mAnchor = mTop;
        }
        mAnchors.add(mAnchor);
        final int count = mListAnchors.length;
        for (int i = 0; i < count; i++) {
            mListAnchors[i] = mAnchor;
        }
    }

    public ConstraintAnchor getAnchor() {
        return mAnchor;
    }

    /**
     * Specify the xml type for the container
     *
     * @return
     */
    @Override
    public String getType() {
        return "Guideline";
    }

    public int getOrientation() {
        return mOrientation;
    }

    @Override
    public ConstraintAnchor getAnchor(ConstraintAnchorConstants.Type anchorType) {
        switch (anchorType) {
            case LEFT:
            case RIGHT: {
                if (mOrientation == VERTICAL) {
                    return mAnchor;
                }
            }
            break;
            case TOP:
            case BOTTOM: {
                if (mOrientation == HORIZONTAL) {
                    return mAnchor;
                }
            }
            break;
            case BASELINE:
            case CENTER:
            case CENTER_X:
            case CENTER_Y:
            case NONE:
                return null;
        }
        throw new AssertionError(anchorType.name());
    }

    @Override
    public ArrayList<ConstraintAnchor> getAnchors() {
      return super.getAnchors();
    }
}
