package src.adux.votingapplib.adapters;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;
import src.adux.votingapplib.Interfaces.OnDollarViewClickedListener;
import src.adux.votingapplib.R;
import src.adux.votingapplib.widgets.CircleTextView;

public class DollarsViewAdapter extends RecyclerView.Adapter<DollarsViewAdapter.ViewHolder> {

    private List<Integer> mData;
    private LayoutInflater mInflater;
    private OnDollarViewClickedListener listener;
    private int mMax = 0;
    private int curr = 0;
    private int mDenomination = 10;

    private String mOptionNames = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    // data is passed into the constructor
    public DollarsViewAdapter(Context context, List<Integer> data, OnDollarViewClickedListener listener) {
        this.mInflater = LayoutInflater.from(context);
        this.mData = data;
        this.listener = listener;
    }

    // inflates the row layout from xml when needed
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.dollars_list_view_item, parent, false);
        return new ViewHolder(view);
    }

    // binds the data to the TextView in each row
    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {

        holder.mView.setCustomText(Character.toString(mOptionNames.charAt(position)));
        holder.mView.setSolidColor("#2979D6");
        holder.mView.setTextColor(Color.parseColor("#FFFFFF"));
        //holder.mView.setStrokeWidth(2);
       // holder.mView.setStrokeColor(Color.parseColor("#DDDDDD"));
        holder.mView.setCustomTextSize(24);

        holder.mPlus.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_plus, 0, 0, 0);

        holder.mMinus.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_minus, 0, 0, 0);

        holder.mReset.setCustomTextSize(20);
        holder.mReset.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_reset, 0, 0, 0);
        holder.mReset.setClickable(false);
        //holder.mReset.setEnabled(false);

        holder.myTextView.setText((mData.get(position)).toString());

        holder.mPlus.setOnClickListener(new View.OnClickListener() {
            final int[] currentValue = new int[1];
            @Override
            public void onClick(View view) {
                System.out.println("Adding 100 to position : "+position);
                currentValue[0] = Integer.parseInt(holder.myTextView.getText().toString());
                if((curr - mDenomination) >= 0){
                    holder.myTextView.setText(Integer.toString(currentValue[0] + mDenomination));
                    if(currentValue[0] + mDenomination > 0) {
                        holder.mReset.setEnabled(true);
                        holder.mReset.setClickable(true);
                        holder.mReset.setAlpha(1f);
                    }
                    setItem(currentValue[0] + mDenomination, position);
                    curr = curr - mDenomination;
                    listener.OnClickedPlus(curr);
                }


            }
        });
        holder.mMinus.setOnClickListener(new View.OnClickListener() {
            final int[] currentValue = new int[1];
            @Override
            public void onClick(View view) {
                System.out.println("Subtracting 100 from position : "+position);
                currentValue[0] = Integer.parseInt(holder.myTextView.getText().toString());
                if((curr + mDenomination) <= mMax && currentValue[0] > 0 && ((currentValue[0]- mDenomination) >= 0)){
                    holder.myTextView.setText(Integer.toString(currentValue[0] - mDenomination));
                    if((currentValue[0] - mDenomination) == 0) {
                        holder.mReset.setEnabled(false);
                        holder.mReset.setClickable(false);
                        holder.mReset.setAlpha(0.5f);
                    }
                    setItem(currentValue[0] - mDenomination, position);
                    curr = curr + mDenomination;
                    listener.OnClickedMinus(curr);
                }
            }
        });

        holder.mReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int value = getItem(position);
                curr = curr + value;
                setItem(0,position);
                listener.OnClickedReset(curr);
                holder.mReset.setEnabled(false);
                holder.mReset.setClickable(false);
                holder.mReset.setAlpha(0.5f);
            }
        });

    }

    // total number of rows
    @Override
    public int getItemCount() {
        return mData.size();
    }


    // stores and recycles views as they are scrolled off screen
    public class ViewHolder extends RecyclerView.ViewHolder{
        CircleTextView mView;
        TextView myTextView;
        CircleTextView mPlus, mMinus, mReset;
        ViewHolder(View itemView) {
            super(itemView);
            mView = (CircleTextView) itemView.findViewById(R.id.listview_circle);
            myTextView = (TextView) itemView.findViewById(R.id.listview_text);
            mPlus = (CircleTextView) itemView.findViewById(R.id.plus);
            mMinus = (CircleTextView) itemView.findViewById(R.id.minus);
            mReset = (CircleTextView) itemView.findViewById(R.id.reset);
        }

    }

    // convenience method for getting data at click position
    Integer getItem(int id) {
        return mData.get(id);
    }

    public void setItem(int value, int position){
        mData.set(position,value);
    }


    public void resetData(){
        for(int i=0; i<mData.size();i++){
            mData.set(i,0);
            curr = mMax;
        }
        listener.OnClickedMinus(curr);
    }

    public void setMax(int max) {
        mMax = max;
        curr = max;
    }

    public void setDenomination(int denomination) {
        mDenomination = denomination;
    }

    public List<Integer> getDollarData(){
        return mData;
    }

}
