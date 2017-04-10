package io.authme.sdk;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
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

public class PinScreen extends AppCompatActivity {
    public static final String TAG = "PinLockView";

    private PinLockView mPinLockView;
    private IndicatorDots mIndicatorDots;
    private String pin1, pin2, logo;
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
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

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
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Callback callback = new Callback() {
            @Override
            public void onTaskExecuted(String response) {
                try {
                    JSONObject jsonObject = new JSONObject(response);
                    if (jsonObject.getInt("Status") == 201) {
                        JSONObject data = jsonObject.getJSONObject("Data");
                        String patternencoding = data.getString("PatternEncoding");
                        String secretKey = data.getString("Key");
                        config.setByteArray(patternencoding.toCharArray());
                        config.setSecretKey(secretKey);
                        setResult(RESULT_OK);
                    }
                    else {
                        setResult(RESULT_FAILED);
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
