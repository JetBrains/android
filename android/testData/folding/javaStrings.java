package p1.p2

import <fold text='...' expand='false'>android.app.Activity;
import android.os.Bundle;</fold>

public class MyActivity extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState)
  <fold text='{...}' expand='true'>{
    Activity c = this;
    String label = <fold text='"Application Name"' expand='false'>c.getString(R.string.app_name)</fold>;
    String label2 = <fold text='"This is a really really really long string, and the fol..."' expand='false'>getString(R.string.foobar)</fold>;
    String label3 = <fold text='"Vibration level is {10}."' expand='false'>getString(R.string.string_width_formatting, 10)</fold>;
    String label4 = <fold text='""' expand='false'>getString(R.string.empty)</fold>;
    String label5 = <fold text='"resolved"' expand='false'>c.getString(R.string.alias)</fold>;
    String label6 = getString(R.string.unknown);
    String label7 = <fold text='shortint: 1' expand='false'>getResources().getInteger(R.integer.shortint)</fold>;
    String label8 = <fold text='longerint: 1502' expand='false'>getResources().getInteger(R.integer.longerint)</fold>;
    // Regression test for b/295349275 - reference with cycle must terminate.
    String label9 = <fold text='"@string/cycle3"' expand='false'>getString(R.string.cycle)</fold>;
  }</fold>
}
<caret>
