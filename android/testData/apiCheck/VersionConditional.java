package p1.p2;

import android.os.Build;
import android.widget.GridLayout;

import static android.os.Build.VERSION;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES;
import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
import static android.os.Build.VERSION_CODES.JELLY_BEAN;

@SuppressWarnings("UnusedDeclaration")
public class Class {
  public void test(boolean priority) {
    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      new GridLayout(null).getOrientation(); // Not flagged
    } else {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null).<error descr="Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation">getOrientation</error>(); // Flagged
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      new GridLayout(null).getOrientation(); // Not flagged
    } else {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null).<error descr="Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation">getOrientation</error>(); // Flagged
    }

    if (SDK_INT >= ICE_CREAM_SANDWICH) {
      new GridLayout(null).getOrientation(); // Not flagged
    } else {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null).<error descr="Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation">getOrientation</error>(); // Flagged
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      new GridLayout(null).getOrientation(); // Not flagged
    } else {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null).<error descr="Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation">getOrientation</error>(); // Flagged
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null).<error descr="Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation">getOrientation</error>(); // Flagged
    } else {
      new GridLayout(null).getOrientation(); // Not flagged
    }

    if (Build.VERSION.SDK_INT >= 14) {
      new GridLayout(null).getOrientation(); // Not flagged
    } else {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null).<error descr="Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation">getOrientation</error>(); // Flagged
    }

    if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) {
      new GridLayout(null).getOrientation(); // Not flagged
    } else {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null).<error descr="Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation">getOrientation</error>(); // Flagged
    }

    // Nested conditionals
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      if (priority) {
        <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null).<error descr="Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation">getOrientation</error>(); // Flagged
      } else {
        <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null).<error descr="Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation">getOrientation</error>(); // Flagged
      }
    } else {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null).<error descr="Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation">getOrientation</error>(); // Flagged
    }

    // Nested conditionals 2
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      if (priority) {
        new GridLayout(null).getOrientation(); // Not flagged
      } else {
        new GridLayout(null).getOrientation(); // Not flagged
      }
    } else {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null); // Flagged
    }
  }

  public void test2(boolean priority) {
    if (android.os.Build.VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
      new GridLayout(null).getOrientation(); // Not flagged
    } else {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null); // Flagged
    }

    if (android.os.Build.VERSION.SDK_INT >= 16) {
      new GridLayout(null).getOrientation(); // Not flagged
    } else {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null); // Flagged
    }

    if (android.os.Build.VERSION.SDK_INT >= 13) {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null).<error descr="Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation">getOrientation</error>(); // Flagged
    } else {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null); // Flagged
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      new GridLayout(null).getOrientation(); // Not flagged
    } else {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null); // Flagged
    }

    if (SDK_INT >= JELLY_BEAN) {
      new GridLayout(null).getOrientation(); // Not flagged
    } else {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null); // Flagged
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      new GridLayout(null).getOrientation(); // Not flagged
    } else {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null); // Flagged
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null); // Flagged
    } else {
      new GridLayout(null).getOrientation(); // Not flagged
    }

    if (Build.VERSION.SDK_INT >= 16) {
      new GridLayout(null).getOrientation(); // Not flagged
    } else {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null); // Flagged
    }

    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
      new GridLayout(null).getOrientation(); // Not flagged
    } else {
      <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null); // Flagged
    }
  }
}