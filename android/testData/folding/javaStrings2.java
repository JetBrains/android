package p1.p2;

import <fold text='...' expand='false'>android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.util.List;</fold>

public class MyActivity2 extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState) <fold text='{}' expand='true'>{
  }</fold>

  private List<String> mSuggestedTerms;
  View.OnClickListener mSearchTermClickListener;

  public View getView(int position, View convertView, ViewGroup parent) <fold text='{...}' expand='true'>{
    ViewGroup viewGroup = (ViewGroup) LayoutInflater.from(parent.getContext()).inflate(
      R.layout.mylayout, null);
    assert viewGroup != null;
    Button button = (Button) viewGroup.getChildAt(0);
    assert button != null;
    String searchText = mSuggestedTerms.get(position);
    button.setText(searchText);
    button.setOnClickListener(mSearchTermClickListener);
    button.setContentDescription(<fold text='"Click to search for {searchText}"' expand='false'>getResources().getString(R.string.content_description_search_text, searchText)</fold>);
    button.setContentDescription(<fold text='"Third: {42} Repeated: {42} First: {firstArg} Second: {s..."' expand='false'>getResources().getString(R.string.formatting_key, "firstArg", "secondArg",
                                                                                                                                                   42, "fourth")</fold>);
    button.setContentDescription(<fold text='"Escaped: \%s First: {searchText} Invalid: %20$s"' expand='false'>getResources().getString(R.string.formatting_key2, searchText, "second")</fold>);
    button.setContentDescription(<fold text='"Third: {true} Repeated: {true} First: {null} Second: {a}"' expand='false'>getResources().getString(R.string.formatting_key, null, 'a', true)</fold>);
    button.setText(<fold text='"Application Name"' expand='false'>R.string.app_name</fold>);
    return viewGroup;
  }</fold>
}
<caret>