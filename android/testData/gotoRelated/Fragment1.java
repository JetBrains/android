package p1.p2;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class MyFragment extends Fragment {
  // <caret>
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    f(R.layout.layout2);
    int p1 = R.layout.layout;
    int p2 = R.layout.layout1;
    return inflater.inflate(R.layout.layout, container);
  }

  void f(int n) {
  }
}
