package test.db.witherrors;

import android.app.Activity;
import androidx.databinding.DataBindingUtil;
import android.os.Bundle;

import test.db.witherrors.databinding.ActivityMainBinding;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        User user = new User();
        user.firstName.set("John");
        user.lastName.set("Doe");
        binding.setUser(user);
    }
}
