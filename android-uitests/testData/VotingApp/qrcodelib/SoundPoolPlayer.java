package qrcodelib;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;

import com.src.adux.votingapp.R;

import java.util.HashMap;

public class SoundPoolPlayer {

    private SoundPool mShortPlayer= null;
    private HashMap mSounds = new HashMap();

    public SoundPoolPlayer(Context pContext) {
        this.mShortPlayer = new SoundPool(4, AudioManager.STREAM_MUSIC, 0);
        mSounds.put(R.raw.bleep, this.mShortPlayer.load(pContext, R.raw.bleep, 1));
    }

    public void playShortResource(int piResource) {
        int iSoundId = (Integer) mSounds.get(piResource);
        this.mShortPlayer.play(iSoundId, 0.99f, 0.99f, 0, 0, 1);
    }

    public void release() {
        this.mShortPlayer.release();
    }
}
