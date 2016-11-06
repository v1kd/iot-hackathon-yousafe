package edu.asu.iot.sos;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.UUID;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleDataReceiver;
import com.getpebble.android.kit.util.PebbleDictionary;

import edu.asu.iot.sos.request.SendHelpTask;
import im.delight.android.location.SimpleLocation;

public class MainActivity extends Activity {

    private static final long DELAY_TIME_IN_SECONDS = 5;
	
	private static final UUID WATCHAPP_UUID = UUID.fromString("5e827189-ea3c-4ddf-8675-b0b7b6c9bb5a");
    private static final String TAG = MainActivity.class.getSimpleName();

	private static final Long
		KEY_BUTTON = ( long) 0,
		KEY_VIBRATE = (long) 1,
        KEY_LONG = (long)2,
		BUTTON_UP = (long)0,
		BUTTON_SELECT = (long)1,
		BUTTON_DOWN = (long)2;
    private static final String FALL_API_URL = "https://jwzvnmij80.execute-api.us-west-2.amazonaws.com/test/";
	
	private Handler fallHandler;
    private Handler timerHandler;
    private Handler sendRequestHandler;

    // start time when the fall has been called
    private Date startTimeOfFall;

    private boolean didHeFall = false;
    private PebbleDataReceiver appMessageReceiver;

    // all UI elements
    private Button cancelHelpButton;
    private TextView headTextView;
    private TextView helpTextView;
    private Button testFallButton;

    /**
     * Get executes after delay time to get help
     */
    private Runnable getHelpRunnable = new Runnable() {
        private static final String TAG = "MainActivity.Runnable";

        @Override
        public void run() {
            Log.d(TAG, String.format("Trying to get help after %s seconds delay time",
                    DELAY_TIME_IN_SECONDS));
            // trigger only when the person is still in fall state
            if (isDown()) getHelp();
        }
    };

    /**
     * Updates the time of the post delayed
     */
    private Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isDown()) return;
            Date currentTime = new Date();
            // calculate diff
            long timeDiff = Math.abs(
                    (currentTime.getTime() - startTimeOfFall.getTime()) / 1000
            );
            // remaining seconds
            long remainingSeconds = DELAY_TIME_IN_SECONDS - timeDiff;
            remainingSeconds = Math.max(remainingSeconds, 0);
            String message = remainingSeconds < 3 ? "Your help is on the way"
                    :  String.format("You will get help in %d seconds", remainingSeconds);
            updateTimerText(message);
            Log.d(TAG, String.format("Updating helper text with time %d", remainingSeconds));
            // only call when greater than zero
            if (remainingSeconds > 0) timerHandler.postDelayed(updateTimeRunnable, 1000);
        }
    };

    /**
     * Update helper text
     * @param text
     */
    private void updateTimerText(String text) {
        helpTextView.setText(text);
    }

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// Customize ActionBar
		ActionBar actionBar = getActionBar();
		actionBar.setTitle("YouSafe");
		actionBar.setBackgroundDrawable(new
                ColorDrawable(getResources().getColor(R.color.actionbar_orange)));
        findAllUIElements();

        // add event listeners
        addEventListeners();

        // create handlers
        createHandlers();
	}

    /**
     * Create handlers
     */
    private void createHandlers() {
        fallHandler = new Handler();
        timerHandler = new Handler();
        sendRequestHandler = new Handler();
    }

    /**
     * Get all UI elements
     */
    private void findAllUIElements() {
        // all the UI elements
        cancelHelpButton = (Button) findViewById(R.id.button_cancel_help);
        headTextView = (TextView) findViewById(R.id.safe_text);
        helpTextView = (TextView) findViewById(R.id.safe_text_sub);
        testFallButton = (Button) findViewById(R.id.button_test_fall);
    }

    @Override
	protected void onResume() {
		super.onResume();
		// Define AppMessage behavior
		if(appMessageReceiver == null) {
            appMessageReceiver = initAppMessageReceiver();
			// Add AppMessage capabilities
			PebbleKit.registerReceivedDataHandler(this, appMessageReceiver);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		// Unregister AppMessage reception
		if(appMessageReceiver != null) {
			unregisterReceiver(appMessageReceiver);
            appMessageReceiver = null;
		}
	}

    /**
     * add event listeners
     */
    private void addEventListeners() {
        cancelHelpButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                // cancel help
                Log.d(TAG, "Clicked on cancel button");
                cancelHelp();
            }
        });

        // remove this event listener at the end
        testFallButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Has fallen down now");
                fallenDown();
            }
        });
    }

    /**
     * Process fall down
     */
    private void fallenDown() {
        // already down do not do anything
        if (this.isDown()) return;
        // process fall down now
        this.setFallingDown();
        // update the start falling down time
        this.setStartTimeOfFall();
        // display cancel and help button
        this.showCancelAndTimerText();

        // remove all callbacks first
        removeAllHandlerCallbacks();
        // start the post delay handler now
        // wait for sometime and send help
        fallHandler.postDelayed(getHelpRunnable, DELAY_TIME_IN_SECONDS * 1000);
        // also start the timer handler
        timerHandler.post(updateTimeRunnable);
    }

    private void showCancelAndTimerText() {
        Log.d(TAG, "Showing cancel and timer text");
        helpTextView.setText("You are about to receive help");
        helpTextView.setVisibility(View.VISIBLE);
        // cancel button
        cancelHelpButton.setVisibility(View.VISIBLE);
    }

    private void hideCancelAndTimerText() {
        Log.d(TAG, "Hiding cancel and timer text");
        helpTextView.setText("");
        helpTextView.setVisibility(View.GONE);
        cancelHelpButton.setVisibility(View.GONE);
    }

    /**
     * Set the fall time
     */
    private void setStartTimeOfFall() {
        this.startTimeOfFall = new Date();
    }

    /**.
     * Get pebble data receiver
     * @return pebbleDataReceiver
     */
    private PebbleDataReceiver initAppMessageReceiver () {
        return new PebbleDataReceiver(WATCHAPP_UUID) {

            @Override
            public void receiveData(Context context, int transactionId, PebbleDictionary data) {
                // Always ACK
                PebbleKit.sendAckToPebble(context, transactionId);

                Long button = data.getInteger(KEY_BUTTON.intValue());
                Long otherButton = data.getInteger(KEY_LONG.intValue());
                Log.d(TAG, "json val: " + data.toJsonString());
                // What message was received?
                if (BUTTON_UP.equals(button) || BUTTON_SELECT.equals(otherButton)) {
                    fallenDown();
                }
                /* if(data.getInteger(KEY_BUTTON) != null) {
                    // KEY_BUTTON was received, determine which button
                    final int button = data.getInteger(KEY_BUTTON).intValue();
                    fallenDown();
                    Log.d(TAG, data.getInteger(KEY_BUTTON).toString());

						//  Update UI on correct thread
						handler.post(new Runnable() {

							@Override
							public void run() {
								switch(button) {
								case BUTTON_UP:
									whichButtonView.setText("UP");
									break;
								case BUTTON_SELECT:
									whichButtonView.setText("SELECT");
									break;
								case BUTTON_DOWN:
									whichButtonView.setText("DOWN");
									break;
								default:
									Toast.makeText(getApplicationContext(), "Unknown button: " + button, Toast.LENGTH_SHORT).show();
									break;
								}
							}

						});
                }*/
            }
        };
    }

    private void getHelp () {
        Log.d(TAG, "Getting help now");
        // help should be on the way
        setGettingHelpText();
        SimpleLocation location = new SimpleLocation(this, true);
        sendLocationAndRequestHelp(location.getLatitude(), location.getLongitude());
        // only after getting help unset falling down
    }

    private void sendLocationAndRequestHelp(double latitude, double longitude) {
        Log.d(TAG, "Sending lat: " + latitude + ", long: " + longitude);
        // sendRequestHandler.post(sendLocationRunnable);
        SendHelpTask task = new SendHelpTask(this);
        task.execute(FALL_API_URL);
    }

    private void setGettingHelpText() {
    }

    private void removeAllHandlerCallbacks() {
        fallHandler.removeCallbacks(getHelpRunnable);
        timerHandler.removeCallbacks(updateTimeRunnable);
    }

    private void cancelHelp() {
        Log.d(TAG, "Cancelled help");
        // remove all callbacks
        removeAllHandlerCallbacks();
        this.unsetFallingDown();
        // hide cancel button and the set view
        hideCancelAndTimerText();
    }

    private void setFallingDown() {
        this.didHeFall = true;
    }

    private void unsetFallingDown() {
        this.didHeFall = false;
    }

    private boolean isDown() {
        return didHeFall;
    }

    public void onRequestComplete(String s) {
        // completed the request
        unsetFallingDown();
        // remove cancel help and reset the initial text of helper function
        hideCancelAndTimerText();
        Log.d(TAG, "Request done, response from server: " + s);
    }
}
