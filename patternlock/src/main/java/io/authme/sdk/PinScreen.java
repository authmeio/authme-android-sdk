package io.authme.sdk;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.andrognito.pinlockview.IndicatorDots;
import com.andrognito.pinlockview.PinLockListener;
import com.andrognito.pinlockview.PinLockView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;

import io.authme.sdk.server.Callback;
import io.authme.sdk.server.Config;
import io.authme.sdk.server.PostData;

import static io.authme.sdk.server.Config.RESULT_FAILED;

public class PinScreen extends Activity {
    public static final String TAG = "PinLockView";

    private PinLockView mPinLockView;
    private IndicatorDots mIndicatorDots;
    private String pin1, pin2, logo, referenceId;
    private boolean firstPin;

    private TextView welcome;
    private ImageView imageView;
    private String action;
    private Config config;

    private PinLockListener mPinLockListener = new PinLockListener() {
        @Override
        public void onComplete(String pin) {
            if (TextUtils.equals(action, Config.SIGNUP_PIN)) {
                processSignup(pin);
            }
            else {
                processSignin(pin);
            }
        }

        @Override
        public void onEmpty() {

        }

        @Override
        public void onPinChange(int pinLength, String intermediatePin) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        action = getIntent().getAction();

        if (getIntent().hasExtra("statusbar")) {

            if (Build.VERSION.SDK_INT >= 21) {
                Window window = getWindow();
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                window.setStatusBarColor(Color.parseColor(getIntent().getStringExtra("statusbar")));
            }

        }

        if (getIntent().hasExtra("titlecolor")) {
            if (Build.VERSION.SDK_INT >= 19) {
                getActionBar().setBackgroundDrawable(new ColorDrawable(Color.parseColor(getIntent().getStringExtra("titlecolor"))));
            }
            else {
                getWindow().setTitleColor(Color.parseColor(getIntent().getStringExtra("titlecolor")));
            }
        }

        if (getIntent().hasExtra("titletext")) {
            if ( Build.VERSION.SDK_INT >= 19) {
                getActionBar().setTitle(getIntent().getStringExtra("titletext"));
            }
            else {
                getWindow().setTitle(getIntent().getStringExtra("titletext"));
            }
        }

        if (getIntent().hasExtra("logo")) {
            logo = getIntent().getStringExtra("logo");
        }

        if (getIntent().hasExtra("referenceId")) {
            referenceId = getIntent().getStringExtra("referenceId");
        }

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_pin_screen);


        imageView = (ImageView) findViewById(R.id.logo);

        if (!TextUtils.isEmpty(logo)) {
            try {
                Bitmap bitmap = BitmapFactory.decodeStream(PinScreen.this
                        .openFileInput(logo));
                imageView.setImageBitmap(bitmap);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        config = new Config(PinScreen.this);

        welcome = (TextView) this.findViewById(R.id.welcometext);

        mPinLockView = (PinLockView) findViewById(R.id.pin_lock_view);
        mIndicatorDots = (IndicatorDots) findViewById(R.id.indicator_dots);

        mPinLockView.attachIndicatorDots(mIndicatorDots);
        mPinLockView.setPinLockListener(mPinLockListener);
        //mPinLockView.setCustomKeySet(new int[]{2, 3, 1, 5, 9, 6, 7, 0, 8, 4});
        //mPinLockView.enableLayoutShuffling();

        mPinLockView.setPinLength(6);
        mPinLockView.setTextColor(ContextCompat.getColor(this, R.color.white));

        mIndicatorDots.setIndicatorType(IndicatorDots.IndicatorType.FILL_WITH_ANIMATION);

        if (TextUtils.equals(action, Config.SIGNIN_PIN)) {
            welcome.setText("Enter Your Pin");
        }

    }

    private void processSignup(String pin) {
        if (firstPin) {
            pin2 = pin;
            if (TextUtils.equals(pin1, pin2)) {
                Intent intent = new Intent();
                intent.putExtra("pin", pin);
                setResult(RESULT_OK, intent);
                PinScreen.this.finish();
            }
            else {
                welcome.setText("PINS don't matach!");
            }
        }
        else {
            pin1 = pin;
            firstPin = true;
            mPinLockView.resetPinLockView();
            welcome.setText("Confirm PIN");
        }
    }

    private void processSignin(String pin) {
        JSONObject request = new JSONObject();
        try {
            request.put("PackageName", getApplicationContext().getPackageName());
            request.put("User", config.getEmailId());
            request.put("Pin", pin);

            if (!TextUtils.isEmpty(referenceId)) {
                request.put("OrderId", referenceId);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        Callback callback = new Callback() {
            @Override
            public void onTaskExecuted(String response) {
                try {
                    JSONObject jsonObject = new JSONObject(response);
                    if (jsonObject.getInt("Status") == 200) {
                        String secretKey = jsonObject.getString("Key");
                        JSONObject data = jsonObject.getJSONObject("Data");
                        config.setSecretKey(secretKey);
                        Intent intent = new Intent();
                        intent.putExtra("response", data.toString());
                        setResult(RESULT_OK, intent);
                    }
                    else {
                        Intent intent = new Intent();
                        intent.putExtra("response", "failed to verify pin");
                        setResult(RESULT_FAILED, intent);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                    setResult(RESULT_FAILED);
                }
                PinScreen.this.finish();
            }
        };

        try {
            new PostData(callback, config.getApiKey()).runPost(config.getServerURL() + "api/otp", request.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
