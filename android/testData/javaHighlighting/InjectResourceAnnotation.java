package p1.p2;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MyActivity extends Activity {

  @InjectResource(R.id.text_view)
  private TextView myTextView1;

  @InjectResource(R.drawable.pic)
  private TextView myTextView2;

  @SomeAnnotation(R.drawable.pic)
  private TextView myTextView3;

  @SomeAnnotation(R.<error>drawable1</error>.pic1)
  private TextView myTextView4;

  private TextView <warning>myTextView5</warning>;

  @InjectResource
  private TextView <warning>myTextView6</warning>;

  @InjectResource(R.id.<error>)</error>
  private TextView <warning>myTextView7</warning>;

  @InjectResource(R1.<error>id</error>.text_view)
  private TextView <warning>myTextView8</warning>;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    System.out.println(myTextView1);
    System.out.println(myTextView2);
    System.out.println(myTextView3);
    System.out.println(myTextView4);
    System.out.println(myTextView5);
    System.out.println(myTextView6);
    System.out.println(myTextView7);
  }
}
