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
package com.android.tools.adtui.imagediff;

import com.android.tools.adtui.AccordionLayout;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class AccordionEntriesRegistrar extends ImageDiffEntriesRegistrar {

  public AccordionEntriesRegistrar() {
    registerVerticalAccordion();
    registerHorizontalAccordion();
    registerVerticalAccordionExpand();
    registerVerticalAccordionCollapse();
    registerVerticalAccordionMinimize();
    registerHorizontalAccordionExpand();
    registerHorizontalAccordionCollapse();
    registerHorizontalAccordionMinimize();
  }

  private void registerVerticalAccordion() {
    register(new AccordionImageDiffEntry("accordion_vertical_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a vertical accordion
        createAccordion(AccordionLayout.Orientation.VERTICAL);

        // Add two vertical segments
        generateSegments(2, AccordionLayout.Orientation.VERTICAL, 0, PREFERRED_SIZE, Integer.MAX_VALUE);
      }

    });
  }

  private void registerHorizontalAccordion() {
    register(new AccordionImageDiffEntry("accordion_horizontal_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a horizontal accordion
        createAccordion(AccordionLayout.Orientation.HORIZONTAL);

        // Add two vertical segments
        generateSegments(2, AccordionLayout.Orientation.HORIZONTAL, 0, PREFERRED_SIZE, Integer.MAX_VALUE);
      }

    });
  }

  private void registerVerticalAccordionExpand() {
    register(new AccordionImageDiffEntry("accordion_vertical_expand_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a vertical accordion
        AccordionLayout accordion = createAccordion(AccordionLayout.Orientation.VERTICAL);

        // Add three vertical segments
        generateSegments(3, AccordionLayout.Orientation.VERTICAL, 0, PREFERRED_SIZE, Integer.MAX_VALUE);

        // Make sure the accordion contains 3 segments
        assert mySegments.size() == 3;

        // Expand the middle segment
        accordion.toggleMaximize(mySegments.get(1));
      }
    });
  }

  private void registerVerticalAccordionCollapse() {
    register(new AccordionImageDiffEntry("accordion_vertical_collapse_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a vertical accordion
        AccordionLayout accordion = createAccordion(AccordionLayout.Orientation.VERTICAL);

        // Add three vertical segments
        generateSegments(3, AccordionLayout.Orientation.VERTICAL, 0, PREFERRED_SIZE, Integer.MAX_VALUE);

        // Make sure the accordion contains 3 segments
        assert mySegments.size() == 3;

        // Expand the middle segment
        accordion.toggleMaximize(mySegments.get(1));

        // Collapse the middle segment
        accordion.toggleMaximize(mySegments.get(1));
      }
    });
  }

  private void registerVerticalAccordionMinimize() {
    register(new AccordionImageDiffEntry("accordion_vertical_minimize_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a vertical accordion
        AccordionLayout accordion = createAccordion(AccordionLayout.Orientation.VERTICAL);

        // Add three vertical segments
        generateSegments(3, AccordionLayout.Orientation.VERTICAL, 0, PREFERRED_SIZE, Integer.MAX_VALUE);

        // Make sure the accordion contains 3 segments
        assert mySegments.size() == 3;

        // Minimize the middle segment
        accordion.toggleMinimize(mySegments.get(1));
      }
    });
  }

  private void registerHorizontalAccordionExpand() {
    register(new AccordionImageDiffEntry("accordion_horizontal_expand_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a horizontal accordion
        AccordionLayout accordion = createAccordion(AccordionLayout.Orientation.HORIZONTAL);

        // Add three horizontal segments
        generateSegments(3, AccordionLayout.Orientation.HORIZONTAL, 0, PREFERRED_SIZE, Integer.MAX_VALUE);

        // Make sure the accordion contains 3 segments
        assert mySegments.size() == 3;

        // Expand the middle segment
        accordion.toggleMaximize(mySegments.get(1));
      }
    });
  }

  private void registerHorizontalAccordionCollapse() {
    register(new AccordionImageDiffEntry("accordion_horizontal_collapse_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a horizontal accordion
        AccordionLayout accordion = createAccordion(AccordionLayout.Orientation.HORIZONTAL);

        // Add three horizontal segments
        generateSegments(3, AccordionLayout.Orientation.HORIZONTAL, 0, PREFERRED_SIZE, Integer.MAX_VALUE);

        // Make sure the accordion contains 3 segments
        assert mySegments.size() == 3;

        // Expand the middle segment
        accordion.toggleMaximize(mySegments.get(1));

        // Collapse the middle segment
        accordion.toggleMaximize(mySegments.get(1));
      }
    });
  }

  private void registerHorizontalAccordionMinimize() {
    register(new AccordionImageDiffEntry("accordion_horizontal_minimize_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a horizontal accordion
        AccordionLayout accordion = createAccordion(AccordionLayout.Orientation.HORIZONTAL);

        // Add three horizontal segments
        generateSegments(3, AccordionLayout.Orientation.HORIZONTAL, 0, PREFERRED_SIZE, Integer.MAX_VALUE);

        // Make sure the accordion contains 3 segments
        assert mySegments.size() == 3;

        // Minimize the middle segment
        accordion.toggleMinimize(mySegments.get(1));
      }
    });
  }

  private static abstract class AccordionImageDiffEntry extends AnimatedComponentImageDiffEntry {

    /**
     * List of colors to be used in the segments to distinguish them from each other.
     */
    private static final Color[] SEGMENT_COLORS = {Color.BLUE, Color.RED, Color.BLACK};

    protected static final int PREFERRED_SIZE = 100;

    private int mySegmentColorsIndex;

    protected List<JPanel> mySegments;

    private AccordionImageDiffEntry(String baselineFilename) {
      super(baselineFilename);
    }

    @Override
    protected void setUp() {
      mySegments = new ArrayList<>();
      mySegmentColorsIndex = 0;
    }

    @Override
    protected void generateTestData() {
      // The goal is to test the accordion layout itself, not its content.
      // Therefore, generating a simple coloured panel for each accordion segment, instead of complex data, is enough.
      for (JPanel component : mySegments) {
        component.setBackground(SEGMENT_COLORS[mySegmentColorsIndex++]);
        mySegmentColorsIndex %= SEGMENT_COLORS.length;
      }
    }

    protected AccordionLayout createAccordion(AccordionLayout.Orientation orientation) {
      AccordionLayout accordion = new AccordionLayout(myContentPane, orientation);
      myComponents.add(accordion);
      myContentPane.setLayout(accordion);
      return accordion;
    }

    protected void generateSegments(int segmentsCount,
                                    AccordionLayout.Orientation orientation,
                                    int minSize,
                                    int preferredSize,
                                    int maxSize) {
      for (int i = 0; i < segmentsCount; i++) {
        JPanel panel = new JPanel();
        setUpSegmentContent(panel, orientation, minSize, preferredSize, maxSize);
        myContentPane.add(panel);
        mySegments.add(panel);
      }
    }

    private static void setUpSegmentContent(JPanel content,
                                            AccordionLayout.Orientation orientation,
                                            int minSize,
                                            int preferredSize,
                                            int maxSize) {
      if (orientation == AccordionLayout.Orientation.VERTICAL) {
        content.setMinimumSize(new Dimension(0, minSize/2));
        content.setPreferredSize(new Dimension(0, preferredSize/2));
        content.setMaximumSize(new Dimension(0, maxSize/2));
      }
      else {
        content.setMinimumSize(new Dimension(minSize, 0));
        content.setPreferredSize(new Dimension(preferredSize, 0));
        content.setMaximumSize(new Dimension(maxSize, 0));
      }
      content.setBorder(BorderFactory.createLineBorder(Color.GRAY));
    }
  }
}

