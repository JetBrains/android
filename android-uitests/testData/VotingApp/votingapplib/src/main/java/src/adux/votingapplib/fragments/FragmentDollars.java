package src.adux.votingapplib.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;

import httplib.HttpPostAnswer;
import src.adux.votingapplib.Interfaces.OnDollarViewClickedListener;
import src.adux.votingapplib.R;
import src.adux.votingapplib.SurveyActivity;
import src.adux.votingapplib.SurveysActivity;
import src.adux.votingapplib.adapters.DollarsViewAdapter;
import src.adux.votingapplib.models.PollAnswers.AnswerDollars.AnswerDollars;
import src.adux.votingapplib.models.PollAnswers.AnswerManager;
import src.adux.votingapplib.models.Question;
import src.adux.votingapplib.models.Session;
import src.adux.votingapplib.widgets.CircleTextView;
import src.nex3z.togglebuttongroup.PollManager;
import src.nex3z.togglebuttongroup.SingleSelectToggleGroup;

public class FragmentDollars extends Fragment implements OnDollarViewClickedListener {

    private static final String LOG_TAG = FragmentDollars.class.getSimpleName();
    private Question mQuestion;
    private FragmentActivity mContext;
    private Button mSubmitButton;
    private TextView mSubtitle;
    private TextView mTitle;
    private SingleSelectToggleGroup mDenominationToggleButtons;
    private String mOptionNames ="ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String SERVER_URL = "https://us-central1-asux-survey.cloudfunctions.net";

    private AnswerDollars mAnswerDollars;
    private String mQid;
    private String mUid;
    private String mType = "";
    private Session mSession;
    private DollarsViewAdapter mDollarListViewAdapter;
    private RecyclerView mRecylcerView;
    private CircleTextView mReset;
    int mCurrentValue = 100;
    int mMaxValue = 100;
    ArrayList<Integer> mInitDollars = new ArrayList<Integer>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.fragment_dollars, container, false);
        mDenominationToggleButtons = (SingleSelectToggleGroup) rootView.findViewById(R.id.dots_color);
        mSubmitButton = (Button) rootView.findViewById(R.id.button_continue);
        mTitle = (TextView) rootView.findViewById(R.id.dots_title);
        mSubtitle = (TextView) rootView.findViewById(R.id.dots_subtitle);
        mSession = new Session(this.getContext());

        mRecylcerView = (RecyclerView)rootView.findViewById(R.id.my_recycler_view);
        mRecylcerView.setLayoutManager(new LinearLayoutManager(this.getContext()));
        mRecylcerView.setClickable(false);

        mSubmitButton.setAlpha(0.5f);

        mReset = (CircleTextView) rootView.findViewById(R.id.reset);
        //mReset.setCustomText("");
        //mReset.setSolidColor(7);
        //mReset.setTextColor(Color.WHITE);
        mReset.setCustomTextSize(20);
        mReset.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_reset, 0, 0, 0);

        AnswerManager.getInstance().reset();
        PollManager.getInstance().setFragment(false);
        PollManager.getInstance().setToggleState(true);

        mQid = getArguments().getString("hash");
        mType = getArguments().getString("type");

//        mUid = Settings.Secure.getString(getContext().getContentResolver(),
//                Settings.Secure.ANDROID_ID);

        mSession = new Session(this.getContext());
        mUid = mSession.getUserID();
        mAnswerDollars = new AnswerDollars();
        mAnswerDollars.setQid(mQid);
        mAnswerDollars.setUid(mUid);


        mSubmitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mCurrentValue != 0){
                    mContext.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            };
                            AlertDialog.Builder builder = new AlertDialog.Builder(FragmentDollars.this.getActivity());
                            builder.setTitle("Dollars remaining")
                                    .setMessage("You need to use up all your dollars to submit.")
                                    .setPositiveButton(android.R.string.ok, listener)
                                    .show();
                        }
                    });
                }
                else {

                    if (mType == null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(FragmentDollars.this.getActivity());
                        builder.setTitle(
                                "Submit response?")
                                .setMessage("Do you wish to submit your response?")
                                .setCancelable(false)
                                .setPositiveButton("Ok",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog,
                                                                int id) {
                                                //mResponse.setDollars(mDollarListViewAdapter.getDollarData());
                                                mAnswerDollars.setDollars(mDollarListViewAdapter.getDollarData());
                                                System.out.println("Answer: " + new Gson().toJson(mAnswerDollars, AnswerDollars.class));
                                                AnswerManager.getInstance().put_answer2(new Gson().toJson(mAnswerDollars, AnswerDollars.class));
                                                ((SurveyActivity) mContext).event_survey_completed(AnswerManager.getInstance());
                                            }
                                        })
                                .setNegativeButton("Cancel",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog,
                                                                int id) {
                                                dialog.cancel();
                                            }
                                        }).create().show();
                    }
                    //((SurveyActivity) mContext).go_to_next();
                    else {
                        postAnswer();
                        ((SurveysActivity) mContext).go_to_next();
                    }
                }
            }
        });

        return rootView;
    }

    private void collect_data() {
        int checkedId = mDenominationToggleButtons.getCheckedId();
        if(checkedId != -1) {
                int max = mQuestion.getNumberOfItems();
                int firstChild = mDenominationToggleButtons.getChildAt(0).getId();
                //mAnswerSingleChoice.setResponse(sanitize_data(checkedId,max,firstChild));
                int value = sanitize_data(checkedId,max,firstChild);
                if(value == 1)
                    mDollarListViewAdapter.setDenomination(1);
                else if(value == 2)
                    mDollarListViewAdapter.setDenomination(10);
        }
    }

    private int sanitize_data(int choice,int max, int firstChild){
            if((choice - (firstChild -1)) % max == 0)
                return max;
            else
                return (choice - (firstChild -1)) % max;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mContext = getActivity();
        mQuestion = (Question) getArguments().getSerializable("data");

        mTitle.setText(mQuestion != null ? "Value remaining : "+mCurrentValue+ "/"+mMaxValue : "");
        mSubtitle.setText(mQuestion != null ? "Choose a denomination and assign to items": "");

        mAnswerDollars.setQtype(mQuestion.getQuestionType());

        for(int i = 0; i < mQuestion.getNumberOfItems(); i++){
            mInitDollars.add(0);
        }



        mDollarListViewAdapter = new DollarsViewAdapter(this.getContext(), mInitDollars, this);
        mDollarListViewAdapter.setMax(mMaxValue);
        mRecylcerView.setAdapter(mDollarListViewAdapter);


        mReset.setOnClickListener(new View.OnClickListener() {
            final int[] currentValue = new int[1];
            @Override
            public void onClick(View view) {
                System.out.println("Resetting all : ");
                mDollarListViewAdapter.resetData();
            }
        });

        mDenominationToggleButtons.setOnCheckedChangeListener(new SingleSelectToggleGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(SingleSelectToggleGroup group, int checkedId) {
                Log.v(LOG_TAG, "onCheckedChanged(): checkedId = " + checkedId);
                collect_data();
            }
        });
    }

    private void postAnswer(){
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
                    AlertDialog.Builder builder = new AlertDialog.Builder(FragmentDollars.this.getActivity());
                    builder.setTitle("Error")
                            .setMessage("Some problem")
                            .setPositiveButton(android.R.string.ok, listener)
                            .show();
                }
            }
        });
        thread.start();
    }

    @Override
    public void OnClickedPlus(int value) {
        mCurrentValue = value;
        if(value == 0){
            String first = Integer.toString(value);
            String next = "<font color='#EE0000'>"+first+"</font>";
            mTitle.setText(Html.fromHtml("Value remaining : "+ next+ "/"+mMaxValue));
            mSubmitButton.setAlpha(1f);
        }
        else
            mTitle.setText("Value remaining : "+value+ "/"+mMaxValue);
        mDollarListViewAdapter.notifyDataSetChanged();
    }

    @Override
    public void OnClickedMinus(int value) {
        mCurrentValue = value;
        if(mCurrentValue > 0)
            mSubmitButton.setAlpha(0.5f);
        mTitle.setText("Value remaining : "+value+ "/"+mMaxValue);
        mDollarListViewAdapter.notifyDataSetChanged();
    }

    @Override
    public void OnClickedReset(int value) {
        mCurrentValue = value;
        mTitle.setText("Value remaining : "+value+ "/"+mMaxValue);
        mSubmitButton.setAlpha(0.5f);
        mDollarListViewAdapter.notifyDataSetChanged();
    }
}