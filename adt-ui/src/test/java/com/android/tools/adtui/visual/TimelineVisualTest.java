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

package com.android.tools.adtui.visual;

import com.android.annotations.NonNull;
import com.android.tools.adtui.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TimelineVisualTest extends VisualTest {

  private TimelineComponent mTimeline;

  private EventData mEvents;

  private TimelineData mData;

  @Override
  protected List<Animatable> createComponentsList() {
    mEvents = new EventData();
    mData = new TimelineData(2, 2000);
    mTimeline = new TimelineComponent(mData, mEvents, 1.0f, 10.0f, 1000.0f, 10.0f);
    return Collections.singletonList(mTimeline);
  }

  static void changeStreamSize(AtomicInteger streamSize,
                               int newStreamSize,
                               JButton addStreamButton,
                               JButton removeStreamButton,
                               JButton addLabelSharingButton,
                               int maxSize,
                               int minSize) {
    streamSize.set(newStreamSize);
    addStreamButton.setEnabled(newStreamSize < maxSize);
    removeStreamButton.setEnabled(newStreamSize > minSize);
    addLabelSharingButton.setEnabled(newStreamSize + 1 < maxSize);
  }

  static Component createEventButton(final int type, final EventData events, final AtomicInteger variance) {
    final String start = "Start " + (variance != null ? "blocking " : "") + "event type " + type;
    final String stop = "Stop event type " + type;
    return VisualTest.createButton(start, new ActionListener() {
      EventData.Event event = null;
      int var = 0;

      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        JButton button = (JButton)actionEvent.getSource();
        if (event != null) {
          event.stop(System.currentTimeMillis());
          event = null;
          if (variance != null) {
            variance.set(var);
          }
          button.setText(start);
        }
        else {
          event = events.start(System.currentTimeMillis(), type);
          if (variance != null) {
            var = variance.get();
            variance.set(0);
          }
          button.setText(stop);
        }
      }
    });
  }


  @Override
  public String getName() {
    return "Timeline";
  }

  @Override
  protected void populateUi(@NonNull JPanel panel) {
    final AtomicInteger streamSize = new AtomicInteger(2);
    final AtomicInteger variance = new AtomicInteger(10);
    final AtomicInteger delay = new AtomicInteger(100);
    final AtomicInteger type = new AtomicInteger(0);
    final List<String> labelSharingStreams = new ArrayList<String>();
    final int maxNumStreams = 10;
    Thread updateDataThread = new Thread() {
      @Override
      public void run() {
        super.run();
        try {
          float[] values = new float[maxNumStreams];
          while (true) {
            int v = variance.get();
            int numStreams = streamSize.get();
            for (int i = 0; i < numStreams; i++) {
              float delta = (float)Math.random() * variance.get() - v * 0.5f;
              values[i] = delta + values[i];
            }
            float[] valuesCopy = new float[numStreams];
            System.arraycopy(values, 0, valuesCopy, 0, numStreams);
            for (int i = 0; i < numStreams; i++) {
              valuesCopy[i] = Math.abs(valuesCopy[i]);
            }
            synchronized (mData) {
              int oldStreams = mData.getStreamCount();
              for (int i = oldStreams; i < numStreams; i++) {
                mData.addStream("Data " + i);
              }
              for (int i = numStreams; i < oldStreams; i++) {
                mData.removeStream("Data " + i);
              }
              mData.add(System.currentTimeMillis(), type.get() + (v == 0 ? 1 : 0), valuesCopy);

            }
            Thread.sleep(delay.get());
          }
        }
        catch (InterruptedException e) {
        }
      }
    };
    updateDataThread.start();

    mTimeline.configureStream(0, "Data 0", new Color(0x78abd9));
    mTimeline.configureStream(1, "Data 1", new Color(0xbaccdc));

    mTimeline.configureUnits("@");
    mTimeline.configureEvent(1, 0, UIManager.getIcon("Tree.leafIcon"), new Color(0x92ADC6), new Color(0x2B4E8C), false);
    mTimeline.configureEvent(2, 1, UIManager.getIcon("Tree.leafIcon"), new Color(255, 191, 176), new Color(76, 14, 29), true);
    mTimeline.configureType(1, TimelineComponent.Style.SOLID);
    mTimeline.configureType(2, TimelineComponent.Style.DASHED);

    TimelineComponent.Listener listener = new TimelineComponent.Listener() {
      private final Color[] COLORS =
        {Color.decode("0xe6550d"), Color.decode("0xfd8d3c"), Color.decode("0x31a354"), Color.decode("0x74c476")};

      @Override
      public void onStreamAdded(int stream, String id) {
        mTimeline.configureStream(stream, id, COLORS[stream % COLORS.length]);
        synchronized (labelSharingStreams) {
          if (labelSharingStreams.contains(id)) {
            int streamIndex = labelSharingStreams.indexOf(id);
            String anotherStreamId = labelSharingStreams.get(streamIndex % 2 == 0 ? streamIndex + 1 : streamIndex - 1);
            boolean combined = mTimeline.linkStreams(id, anotherStreamId);
            if (combined) {
              labelSharingStreams.remove(id);
              labelSharingStreams.remove(anotherStreamId);
            }
          }
        }
      }
    };
    mTimeline.addListener(listener);

    final JPanel controls = VisualTest.createControlledPane(panel, mTimeline);

    controls.add(VisualTest.createVariableSlider("Delay", 10, 5000, new VisualTests.Value() {
      @Override
      public void set(int v) {
        delay.set(v);
      }

      @Override
      public int get() {
        return delay.get();
      }
    }));
    controls.add(VisualTest.createVariableSlider("Variance", 0, 50, new VisualTests.Value() {
      @Override
      public void set(int v) {
        variance.set(v);
      }

      @Override
      public int get() {
        return variance.get();
      }
    }));
    controls.add(VisualTest.createVariableSlider("Type", 0, 2, new VisualTests.Value() {
      @Override
      public void set(int v) {
        type.set(v);
      }

      @Override
      public int get() {
        return type.get();
      }
    }));
    controls.add(createEventButton(1, mEvents, variance));
    controls.add(createEventButton(1, mEvents, null));
    controls.add(createEventButton(2, mEvents, variance));
    final JButton addStreamButton = VisualTest.createButton("Add a stream");
    final JButton removeStreamButton = VisualTest.createButton("Remove a stream");
    final JButton addLinkedStreamsButton = VisualTest.createButton("Add label sharing streams");
    removeStreamButton.setEnabled(false);
    addStreamButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        changeStreamSize(streamSize, streamSize.get() + 1, addStreamButton, removeStreamButton, addLinkedStreamsButton, maxNumStreams, 2);
      }
    });
    removeStreamButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        changeStreamSize(streamSize, streamSize.get() - 1, addStreamButton, removeStreamButton, addLinkedStreamsButton, maxNumStreams, 2);
      }
    });
    addLinkedStreamsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        int size = streamSize.get();
        changeStreamSize(streamSize, size + 2, addStreamButton, removeStreamButton, addLinkedStreamsButton, maxNumStreams, 2);
        labelSharingStreams.add("Data " + size);
        labelSharingStreams.add("Data " + (size + 1));
      }
    });
    controls.add(addStreamButton);
    controls.add(addLinkedStreamsButton);
    controls.add(removeStreamButton);
    controls.add(VisualTest.createCheckbox("Stack streams", new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        mTimeline.setStackStreams(e.getStateChange() == ItemEvent.SELECTED);
      }
    }, true));

    controls.add(new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE), new Dimension(300, Integer.MAX_VALUE)));
    panel.add(mTimeline, BorderLayout.CENTER);
  }
}
