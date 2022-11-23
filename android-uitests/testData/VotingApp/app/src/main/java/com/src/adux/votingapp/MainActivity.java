package com.src.adux.votingapp;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.Spanned;

import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import src.adux.votingapplib.models.PollAnswers.AnswerManager;
import src.adux.votingapplib.SurveyActivity;
import src.adux.votingapplib.models.QuestionsList;


import java.io.IOException;
import java.util.UUID;


import httplib.HttpGetQuestion;
import httplib.HttpPostAnswer;

import com.src.adux.votingapp.typeface.CustomTypefaceSpan;

import src.adux.votingapplib.models.Session;
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;


public class MainActivity extends AppCompatActivity {
    private static final int SURVEY_REQUEST = 1337;
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final String SERVER_URL = "https://us-central1-asux-survey-staging.cloudfunctions.net";
    private static final String EDITTEXT_SERVER_URL = "https://floop-survey.firebaseapp.com/question/";
    private Button mStartScanningButton;

    private String mSurvyLinkResult;
    private TextView mStartScanningText;
    private String mAccessCodeResult;
    private String mAccessCodeResultURL;
    private QuestionsList mQuestionsList;
    private RelativeLayout mLoadingLayout, mSurveyCodeLayout;
    private Session mSession;
    private String mDeepLink = "";
    private boolean type = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_edit_text);

        mLoadingLayout = (RelativeLayout) findViewById(R.id.progress);
        mSurveyCodeLayout = (RelativeLayout) findViewById(R.id.survey_code);
        mStartScanningText = (TextView) findViewById(R.id.textView);
        mSession = new Session(this.getApplication().getApplicationContext());

        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
                .setDefaultFontPath("fonts/google_sans_medium.ttf")
                .setFontAttrId(R.attr.fontPath)
                .build()
        );

        Typeface font = Typeface.createFromAsset(getAssets(), "fonts/google_sans_medium.ttf");
        SpannableString str = new SpannableString("  Feedback Loop");
        str.setSpan(new CustomTypefaceSpan("",font),0, str.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        str.setSpan(new RelativeSizeSpan(1.2f),0, str.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        getSupportActionBar().setTitle(str);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        int prevPID = prefs.getInt("APP_PROCESS_ID",0);
        int currPID = android.os.Process.myPid();
        if(prevPID == 0 || prevPID != currPID) {
            //System.out.println("&&&&&&&&&&&&& New id &&&&&&&&&&&&&");
            prefs.edit().putInt("APP_PROCESS_ID", android.os.Process.myPid()).commit();
            mSession.setUserID((UUID.randomUUID()).toString());
        }









        //*** uncomment / comment  for Edit Text Code Screen ******
        mStartScanningText.setText("Enter your survey access code.");
        mStartScanningButton = (Button) findViewById(R.id.send_code);



        mStartScanningButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(LOG_TAG,"Scanning code now");


                //method for Edit Text Code Screen ******
                sendCode();

            }
        });

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED){
            requestPermissions(new String[]{Manifest.permission.CAMERA},
                    100);
        }

        Intent intent = getIntent();
        Uri data = intent.getData();
        if(data != null){
            //System.out.println("&&&&&&&&&&&&& New id &&&&&&&&&&&&&" + data.getLastPathSegment());
            mDeepLink = data.getLastPathSegment();
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        getJson(SERVER_URL + "/question?id=" + mDeepLink);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            thread.start();
        }

    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        System.out.println("&&&&&&&&&&&&& New intent &&&&&&&&&&&&&");
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SURVEY_REQUEST) {
            if (resultCode == RESULT_OK) {
                if (!type) {
                    final String answers_json = AnswerManager.getInstance().get_answer();
                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            HttpPostAnswer httpPostClient = new HttpPostAnswer();
                            try {
                                Log.d("****", "****************** WE HAVE ANSWERS ******************");
                                Log.d(LOG_TAG, "Posting data : " + answers_json);
                                Log.d("****", "****************** WE HAVE ANSWERS ******************");
                                httpPostClient.post(SERVER_URL + "/answer", answers_json);
                            } catch (IOException e) {
                                e.printStackTrace();
                                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.cancel();
                                    }
                                };
                                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                builder.setTitle("Error")
                                        .setMessage("Some problem")
                                        .setPositiveButton(android.R.string.ok, listener)
                                        .show();
                            }
                        }
                    });
                    thread.start();
                }
            }
            else {
                Log.d("****", "****************** WE HAVE ANSWERS ******************");
                Log.d(LOG_TAG, "Survey : Already posted data : ");
                Log.d("****", "****************** WE HAVE ANSWERS ******************");
            }
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }



    private void getJson(String url) throws IOException {
        Log.d(LOG_TAG, "Getting response from URL : " + url);

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSurveyCodeLayout.setVisibility(View.GONE);
                mLoadingLayout.setVisibility(View.VISIBLE);
                //mStartScanningText.setTextColor(Color.parseColor("#5E5D5D"));
                mStartScanningButton.setBackgroundColor(Color.parseColor("#5E5D5D"));
            }
        });

        String response = null;
        try {
            HttpGetQuestion httpClient = new HttpGetQuestion();
            response = httpClient.run(url);
            Log.d(LOG_TAG, "Received Response : " + response);
            if(validateCode(response) == true) {
                Intent i_survey = new Intent(MainActivity.this, SurveyActivity.class);
                if(!mDeepLink.equals("")) {
                    i_survey.putExtra("hash", mDeepLink);
                    mDeepLink = "";
                } else {

                    i_survey.putExtra("hash", mSurvyLinkResult);
                }
                i_survey.putExtra("json_survey", response);
                startActivityForResult(i_survey, SURVEY_REQUEST);
            }
            else{
                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        };
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle("Invalid Code")
                                .setMessage("Looks like the survey access code is invalid. Please try again.")
                                .setPositiveButton(android.R.string.ok, listener)
                                .show();
                    }
                });
            }
        } catch(Exception e){
            e.printStackTrace();
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    };
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Network Connection Error")
                            .setMessage("Looks like you are not connected to the internet.")
                            .setPositiveButton(android.R.string.ok, listener)
                            .show();
                }
            });

        }
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLoadingLayout.setVisibility(View.GONE);
                mSurveyCodeLayout.setVisibility(View.VISIBLE);
                mStartScanningButton.setBackgroundColor(Color.parseColor("#2979D6"));
            }
        });
    }


    private boolean validateCode(String response){
        if(response == null || response.equalsIgnoreCase("") || response.contains("Error"))
            return false;
        return true;
    }




    private void sendCode() {
        Log.d("Scanning","again");

        final EditText accessCode = (EditText) findViewById(R.id.input_survey_code);
        //Toast.makeText(getApplicationContext(), "Access Code Entered: " + accessCode.getText().toString(), Toast.LENGTH_SHORT).show();
        Log.d("Scanning", accessCode.getText().toString());



        mAccessCodeResult = accessCode.getText().toString();
        //mAccessCodeResult = "-LErcRLPe8PUL-jGcDLa";
        mAccessCodeResultURL = EDITTEXT_SERVER_URL+mAccessCodeResult;

        if(accessCode.getText().toString().isEmpty()) {
            accessCode.setError(getString(R.string.validation_error));
            //Toast.makeText(this, "Please enter an access code ", Toast.LENGTH_SHORT).show();
            return;
        }
        else {

            type = false;
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {

                        Uri data = Uri.parse(mAccessCodeResultURL);
                        String link = data.getLastPathSegment();
                        mSurvyLinkResult = link;
                        System.out.println("&&&&&&&& "+link);

                        getJson(SERVER_URL + "/question?id=" + link);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            thread.start();


        }








    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater=getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);

    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId())
        {
            case R.id.version: {
                Toast.makeText(getApplicationContext(),"Version is :" + BuildConfig.VERSION_NAME, Toast.LENGTH_LONG).show();
                break;
            }
        }
        return true;
    }

}
