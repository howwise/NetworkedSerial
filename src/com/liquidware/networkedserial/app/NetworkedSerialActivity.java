/*
 * Copyright 2011 Chris Ladden chris.ladden@liquidware.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liquidware.networkedserial.app;

import java.io.IOException;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.liquidware.networkedserial.app.R;

public class NetworkedSerialActivity extends SerialPortActivity implements Event{
	private static final String TAG = "NetworkedSerialActivity";

	private static final String mLocalDir = Environment.getExternalStorageDirectory().toString() + "/out";

	private static final int CMD_SUCCESS  = -1;
	private static final int CMD_TIMEOUT  = -2;

	SendingThread mSendingThread;
	volatile byte[] mBuffer;
	static TextView mReception;
	EditText mPingIP;
	ScrollView mScroller;
	ProgressBar mProgressBar;
	ImageButton mImage1;
	ImageButton mImage2;
	Button mButtonSerial;
	Button mButtonBeep;
	TextView mTextViewIP;
	String mPingUrl;
	String mPingResponse;
	String mActiveCmd;
	TextView mBatteryStatus;
	TextView mUpTimeStatus;
	public static Button mButtonPing;
	volatile String mReceptionBuffer;
	volatile StringBuffer mStringBuffer;
	volatile String mExpectedResult;
	volatile boolean mIsExpectedResult;
	volatile boolean mShowSerialInput;
	volatile int mTimeout;
	Handler mHandler = new Handler();
	EventNotifier mNotifier;


	public void setUIDisabled() {
		mButtonSerial.setEnabled(false);
		mButtonPing.setEnabled(false);
		mButtonBeep.setEnabled(false);
		mProgressBar.setVisibility(ProgressBar.VISIBLE);
		mProgressBar.setProgress(0);
	}

	public void setUIEnabled() {
		mButtonSerial.setEnabled(true);
		mButtonPing.setEnabled(true);
		mButtonBeep.setEnabled(true);
		mProgressBar.setVisibility(ProgressBar.INVISIBLE);
		mProgressBar.setProgress(0);
	}

	private void setupKeyGuardPower() {
	    PowerManager.WakeLock wl;

        try {
            Log.w(TAG, "Disabling keyguard");
            KeyguardManager keyguardManager = (KeyguardManager)getSystemService(Activity.KEYGUARD_SERVICE);
            KeyguardLock lock = keyguardManager.newKeyguardLock(KEYGUARD_SERVICE);
            lock.disableKeyguard();
        } catch (Exception ex) {
            Log.e(TAG, ex.getStackTrace().toString());
            ex.printStackTrace();
        }

        try {
            Log.w(TAG, "Acquiring wake lock");
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "My Tag");
        } catch (Exception ex) {
            Log.e(TAG, ex.getStackTrace().toString());
            ex.printStackTrace();
        }
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        //WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.networked_serial_activity);
		mBuffer = new byte[1024];
		mStringBuffer = new StringBuffer(500000);
		mShowSerialInput = true;

		setupKeyGuardPower();

		mReception = (TextView) findViewById(R.id.TextViewReception);
		mScroller = (ScrollView) findViewById(R.id.scroller);
		mProgressBar = (ProgressBar) findViewById(R.id.ProgressBar1);
		mPingIP = (EditText) findViewById(R.id.EditTextPingIP);

		mButtonSerial = (Button)findViewById(R.id.ButtonSerial);
		mButtonSerial.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Toast.makeText(getApplicationContext(), "Performing serial send!", 1).show();

				if (mSerialPort != null) {
					new ExecuteCommandTask().execute("hello");
				} else {
					Toast.makeText(getApplicationContext(), "Error: serial not ready", 1).show();
				}
			}
		});

        mButtonBeep = (Button)findViewById(R.id.ButtonSound);
        mButtonBeep.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "Beep!", 1).show();

                AudioManager amanager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                int maxVolume = amanager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                amanager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0);

                MediaPlayer mp = new MediaPlayer();

                mp.setAudioStreamType(AudioManager.STREAM_ALARM); // this is important.

                try {
                    mp.setDataSource("/sdcard/Music/BeepStereo.wav");
                } catch (IllegalArgumentException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (SecurityException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IllegalStateException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                try {
                    mp.prepare();
                } catch (IllegalStateException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                mp.start();
            }
        });

		mButtonPing = (Button)findViewById(R.id.buttonPing);
		mButtonPing.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Toast.makeText(getApplicationContext(), "Performing ping", 1).show();

				mPingUrl = "http://" + mPingIP.getText().toString();
				mReception.append("Pinging server '" + mPingUrl + "'\n");

                if (mSerialPort != null) {
                    new ExecuteCommandTask().execute("ping");
                } else {
                    Toast.makeText(getApplicationContext(), "Error: serial not ready", 1).show();
                }
			}
		});

		mTextViewIP = (TextView)findViewById(R.id.NetworkIPTextView);
		mBatteryStatus = (TextView) findViewById(R.id.BatteryStatusTextView);
		mUpTimeStatus = (TextView) findViewById(R.id.UpTimeStatusTextView);
		this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		mNotifier = new EventNotifier(this);
		mUpdateTimeTask.run();
	}

    /**
     * A global application clock ticker
     */
    private final Runnable mUpdateTimeTask = new Runnable() {
        public void run() {
            long millisUpTime = SystemClock.uptimeMillis();

            Log.d(TAG,  "tick");
            mNotifier.onTimerTick(millisUpTime);

            //if (getCurrentMode() != MODE_EXIT)
            mHandler.postAtTime(this, millisUpTime + 1000);
        }
    };

	private class ExecuteCommandTask extends AsyncTask<String, Integer, Boolean> {

		@Override
        protected void onPreExecute() {
			setUIDisabled();
		}

		protected void send(String cmd) {
			/* Prepare the command */
			mReceptionBuffer = "";
			mStringBuffer.delete(0, mStringBuffer.length());
			mIsExpectedResult = false;
			mBuffer = cmd.getBytes();

			Log.d(TAG, "Sending '" + cmd + "'");
			mSendingThread = new SendingThread();
			mSendingThread.start();
		}

		protected void setTimeout(int ms) {
			mTimeout = ms;
		}

		protected int getTimeout() {
			return mTimeout;
		}

		protected boolean expect(String expected) {
			int ms_count = 0;
			mExpectedResult = expected;

			/* Wait for the response */
			while (!mIsExpectedResult) {

				Log.d(TAG, "Scanning for '" + mExpectedResult + "' " + ms_count);
				publishProgress(ms_count);

				mReceptionBuffer = mStringBuffer.toString();
				Log.d(TAG, "Response '" + mReceptionBuffer + "'");
				if (mReceptionBuffer.contains(mExpectedResult)) {
					mIsExpectedResult = true;
					Log.d(TAG, "Expect found!");
					publishProgress(CMD_SUCCESS);
					break;
				}

				SystemClock.sleep(100);
				ms_count = ms_count + 100;
				if (ms_count > getTimeout()) {
					Log.d(TAG, "Expect Timeout!");
					publishProgress(CMD_TIMEOUT);
					break;
				}
			}

			return mIsExpectedResult;
		}

		protected boolean send_cmd(String cmd, String expect) {
			boolean r;

			send(cmd + "\n");
			r = expect(expect);
			return r;
		}

		@Override
        protected Boolean doInBackground(String... cmd) {
			boolean r = true;

			mActiveCmd = cmd[0];

            if (mActiveCmd.equals("hello")) {
                int count = 10;
                setTimeout(5000);

                //Say hello to any serial terminals
                while (count-- > 0) {
                    if (send_cmd("Hello there, you serial device. Say hi.\r\n", "hi")) {
                        //Response found, talk to me.
                        setTimeout(20000);

                        String testText = "Rebellious subjects, enemies to peace,\r\n" +
                                        "Profaners of this neighbour-stained steel,--\r\n" +
                                        "Will they not hear? What, ho! you men, you beasts,\r\n" +
                                        "That quench the fire of your pernicious rage\r\n" +
                                        "With purple fountains issuing from your veins,\r\n" +
                                        "On pain of torture, from those bloody hands\r\n" +
                                        "Throw your mistemperd weapons to the ground,\r\n" +
                                        "And hear the sentence of your moved prince.\r\n" +
                                        "Three civil brawls, bred of an airy word,\r\n" +
                                        "By thee, old Capulet, and Montague,\r\n" +
                                        "Have thrice disturbd the quiet of our streets,\r\n" +
                                        "And made Veronas ancient citizens\r\n" +
                                        "Cast by their grave beseeming ornaments,\r\n" +
                                        "To wield old partisans, in hands as old,\r\n" +
                                        "Cankerd with peace, to part your cankerd hate:\r\n" +
                                        "If ever you disturb our streets again,\r\n" +
                                        "Your lives shall pay the forfeit of the peace.\r\n" +
                                        "For this time, all the rest depart away:\r\n" +
                                        "You Capulet; shall go along with me:\r\n" +
                                        "And, Montague, come you this afternoon,\r\n" +
                                        "To know our further pleasure in this case,\r\n" +
                                        "To old Free-town, our common judgment-place.\r\n" +
                                        "Once more, on pain of death, all men depart.\r\n\r\n";

                        if (send_cmd(testText + "Talk to me, you have 30 seconds.\r\nType exit to quit.\r","exit")) {
                            send("Goodbye.\r");
                            count = 0;
                            break;
                        }
                        send("Session timeout, goodbye.\r");
                        count = 0;
                        break;
                    }
                }

            } else if (mActiveCmd.equals("ping")) {
                mPingResponse = "";
                setTimeout(3000);

                mPingResponse = Util.pingHttpUrl(mPingUrl);
                publishProgress(1000);
                mPingResponse = Util.pingHttpUrl(mPingUrl);
                publishProgress(2000);
                mPingResponse = Util.pingHttpUrl(mPingUrl);
                publishProgress(3000);
            }
			return r;
		}

		@Override
        protected void onProgressUpdate(Integer... progress) {
			mProgressBar.setMax(getTimeout());
			mProgressBar.setProgress(progress[0]);
			if (mShowSerialInput && mActiveCmd.equals("hello"))
				mReception.setText(mReceptionBuffer);
			mScroller.smoothScrollTo(0, mReception.getBottom());

			//Handle the UI progress
			if (progress[0] == CMD_SUCCESS) {
				Toast.makeText(getApplicationContext(), "Success!", 1).show();
			} else if (progress[0] == CMD_TIMEOUT){
			    //do nothing
				//Toast.makeText(getApplicationContext(), "Error: timeout running command.", 1).show();
			} else {
				//just update the UI with some progress.
			    if (mActiveCmd.equals("ping")) {
			        mReception.append(mPingResponse);
			    }
			}
		}

		@Override
        protected void onPostExecute(Boolean result) {
			setUIEnabled();
		}
	}

	private class SendingThread extends Thread {
		@Override
		public void run() {
			if (mOutputStream == null)
				return;

			try {
				mOutputStream.write(mBuffer);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

    @Override
    protected void onDataReceived(final byte[] buffer, final int size) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (mStringBuffer == null)
					return;
				mStringBuffer.append(new String(buffer, 0, size));
			}
		});
	}

    public void onTimerTick(long millisUpTime) {
        Log.d(TAG, "Updating battery status");
        mBatteryStatus.setText(Util.getBatteryStatus());

        Log.d(TAG, "Updating network status");
        mTextViewIP.setText(Util.getLocalIpAddress());

        Log.d(TAG, "Uptime");
        mUpTimeStatus.setText("uptime=" + millisUpTime);
    }
}
