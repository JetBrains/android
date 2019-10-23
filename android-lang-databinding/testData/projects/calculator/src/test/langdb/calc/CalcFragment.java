package test.langdb.calc;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import test.lanbdb.calc.databinding.FragmentCalcBinding;

public final class CalcFragment extends Fragment {
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
    FragmentCalcBinding binding = DataBindingUtil.inflate(inflater, R.layout.fragment_calc, container, false);
    binding.setCalc(new Calculator());
    return binding.getRoot();
  }
}
