package src.nex3z.togglebuttongroup;


//Singleton AnswerManager ........

public class PollManager {
    private volatile static PollManager uniqueInstance;
    private boolean mCurrentDotsStateGreen = true;
    private boolean mIsRedGreenDots = false;
    private int mDotsState[];
    private boolean mAllGreensUsed = false;
    private boolean mAllRedsUsed = false;
    private boolean mIsSwapping = false;
    private int mMaxRed, mMaxGreen;


    private PollManager() {

    }


    // states for dots
    public void initializeDotStates(int size){
        mDotsState = new int[size];
    }

    public int[] getDotsStates(){
        return mDotsState;
    }

    public void setDotstates(int position, int value){
        mDotsState[position] = value;
    }

    // states for toggle
    public void setToggleState(boolean state){
        mCurrentDotsStateGreen = state;
    }

    public boolean getToggleState(){
        return mCurrentDotsStateGreen;
    }

    // helpers for dots
    public void setAreGreensOver(boolean state){
        mAllGreensUsed = state;
    }

    public boolean getAreGreensOver(){
        int used = getNumberGreensUsed();
        if(used == mMaxGreen)
            return true;
        else
            return false;
    }

    public void setAreRedsOver(boolean state){
        mAllRedsUsed = state;
    }

    public boolean getAreRedsOver(){
        int used = getNumberRedsUsed();
        if(used == mMaxRed)
            return true;
        else
            return false;
    }

    public int getNumberGreensUsed(){
        int counter = 0;
        for(int i=0;i<mDotsState.length;i++) {
            if(mDotsState[i]==1)
                counter++;
        }
        return counter;
    }

    public int getNumberRedsUsed(){
        int counter = 0;
        for(int i=0;i<mDotsState.length;i++) {
            if(mDotsState[i]==2)
                counter++;
        }
        return counter;
    }

    public int getmMaxRed(){
        return mMaxRed;
    }

    public void setmMaxRed(int max){
        mMaxRed = max;
    }

    public int getmMaxGreen(){
        return mMaxGreen;
    }

    public void setmMaxGreen(int max){
        mMaxGreen = max;
    }

    public void setFragment(boolean state){
        mIsRedGreenDots = state;
    }

    public boolean getFragment(){
        return mIsRedGreenDots;
    }


    public static PollManager getInstance() {
        if (uniqueInstance == null) {
            synchronized (PollManager.class) {
                if (uniqueInstance == null) {
                    uniqueInstance = new PollManager();
                }
            }
        }
        return uniqueInstance;
    }
}
