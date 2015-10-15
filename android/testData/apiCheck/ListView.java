<error descr="The SDK platform-tools version ((16.0.2)) is too old  to check APIs compiled with API 17; please update">package p1.p2;</error>

import android.content.Context;
import android.util.AttributeSet;
import android.widget.AbsListView;
import android.widget.ListAdapter;
import android.widget.ListView;

public class Class {
  private class MyAbsListView extends AbsListView {
    private MyAbsListView(Context context, AttributeSet attrs, int defStyle) {
      super(context, attrs, defStyle);
    }

    @Override
    public ListAdapter getAdapter() {
      return null;
    }

    @Override
    public void setSelection(int i) {
      if (this.<error descr="Call requires API level 11 (current min is 1): android.widget.AbsListView#getChoiceMode">getChoiceMode</error>() != ListView.CHOICE_MODE_NONE) {
      }
      if (<error descr="Call requires API level 11 (current min is 1): android.widget.AbsListView#getChoiceMode">getChoiceMode</error>() != ListView.CHOICE_MODE_NONE) {
      }
      if (super.<error descr="Call requires API level 11 (current min is 1): android.widget.AbsListView#getChoiceMode">getChoiceMode</error>() != ListView.CHOICE_MODE_NONE) {
      }
      AbsListView view = (AbsListView) getEmptyView();
      if (view.<error descr="Call requires API level 11 (current min is 1): android.widget.AbsListView#getChoiceMode">getChoiceMode</error>() != ListView.CHOICE_MODE_NONE) {
      }
    }
  }

  private class MyListView extends ListView {
    private MyListView(Context context, AttributeSet attrs, int defStyle) {
      super(context, attrs, defStyle);
    }

    @Override
    public ListAdapter getAdapter() {
      return null;
    }

    @Override
    public void setSelection(int i) {
      if (this.getChoiceMode() != ListView.CHOICE_MODE_NONE) {
      }
      if (getChoiceMode() != ListView.CHOICE_MODE_NONE) {
      }
      if (super.getChoiceMode() != ListView.CHOICE_MODE_NONE) {
      }
      ListView view = (ListView) getEmptyView();
      if (view.getChoiceMode() != ListView.CHOICE_MODE_NONE) {
      }
    }
  }
}
