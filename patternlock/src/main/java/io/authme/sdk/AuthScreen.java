/*
 *   Copyright 2017 Authme ID Services Pvt. Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.authme.sdk;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.InvalidKeyException;

import io.authme.sdk.server.Callback;
import io.authme.sdk.server.Config;


public class AuthScreen extends Activity {

    private static final int ENTER_PATTERN = 2, CREATE_PATTERN = 1;
    private static final String AUTHMEIO = "AUTHMEIO";
    private String referenceId, userId, hash, otp, action;
    private Config config;

    public AuthScreen() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        config = new Config(AuthScreen.this);
        action = getIntent().getStringExtra("action");
        referenceId = getIntent().getStringExtra("referenceId");
        userId = getIntent().getStringExtra("userId");
        otp = getIntent().getStringExtra("otp");
        startExecution();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(io.authme.sdk.R.menu.menu_intermediate, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == io.authme.sdk.R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void startExecution() {
        if (TextUtils.equals(action, "signin")) {
            String stringArray = config.getPatternString();
            if (TextUtils.isEmpty(stringArray)) {

            }
            else {
                char[] charArray = stringArray.toCharArray();
                Intent intent = new Intent(AuthScreen.this, LockPatternActivity.class);
                intent.setAction(LockPatternActivity.ACTION_COMPARE_PATTERN);
                intent.putExtra(LockPatternActivity.EXTRA_PATTERN, charArray);
                startActivityForResult(intent, ENTER_PATTERN);
            }

        }
        else if (TextUtils.equals(action, "signup")) {

        }
        else {
            Toast.makeText(getApplicationContext(), "Action Not Recognized", Toast.LENGTH_LONG).show();
            endActivity(RESULT_CANCELED);
            return;
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CREATE_PATTERN: {
                switch (resultCode) {
                    case RESULT_OK:
                        char[] pattern = data.getCharArrayExtra(LockPatternActivity.EXTRA_PATTERN);
                        config.setByteArray(pattern);
                        endActivity(CREATE_PATTERN);
                        break;
                    case RESULT_CANCELED:
                        endActivity(RESULT_CANCELED);
                        break;
                    default:
                        break;
                }
            }
            break;

            case ENTER_PATTERN: {
                switch (resultCode) {
                    case RESULT_OK:
                        try {
                            postAuthResult(data.getStringExtra(LockPatternActivity.PATTERN_JSON));
                        } catch (JSONException | InvalidKeyException e) {
                            Toast.makeText(AuthScreen.this, "Failed to post result: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                        endActivity(ENTER_PATTERN);
                        break;
                    case RESULT_CANCELED:
                        clearJson();
                        endActivity(RESULT_CANCELED);
                        break;
                    case LockPatternActivity.RESULT_FAILED:
                        Toast.makeText(AuthScreen.this, "Failed to identify", Toast.LENGTH_SHORT).show();
                        clearJson();
                        endActivity(RESULT_CANCELED);
                        break;
                    case LockPatternActivity.RESULT_FORGOT_PATTERN:
                        Toast.makeText(AuthScreen.this, "Forgot pattern", Toast.LENGTH_SHORT).show();
                        clearJson();
                        break;
                }
            }
            break;

            default:
                break;

        }
    }

    private void clearJson() {
        endActivity(RESULT_CANCELED);
    }

    private void postAuthResult(String biggerJson) throws InvalidKeyException, JSONException {

        callOtpVerify();

        callSensorVerify(biggerJson);

    }

    private void callOtpVerify() {
        JSONObject otpObject = new JSONObject();
        try {
            otpObject.put("User", userId);
            otpObject.put("Otp", otp);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Callback otp_callback = new Callback() {
            @Override
            public void onTaskExecuted(String response) {

            }
        };

        io.authme.sdk.server.PostData postOtp = new io.authme.sdk.server.PostData(otp_callback);

        try {
            postOtp.runPost(io.authme.sdk.server.Config.PROD_SERVER_URL + "api/otp", otpObject.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void callSensorVerify(String biggerJson) throws InvalidKeyException, JSONException {
        JSONObject jsonObject = new JSONObject();

        if (!TextUtils.isEmpty(biggerJson)) {

            try {
                jsonObject = new JSONObject(biggerJson);
                jsonObject.put("OrderId", referenceId);
                jsonObject.put("User", userId);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            return;
        }

        jsonObject.put("Otp", otp);

        Callback callback = new Callback() {
            @Override
            public void onTaskExecuted(String response) {

            }
        };

        io.authme.sdk.server.PostData postData = new io.authme.sdk.server.PostData(callback);
        Log.d("AUTHMEIO", "Post data to sensor api : " + jsonObject);
        try {
            postData.runPost(io.authme.sdk.server.Config.PROD_SERVER_URL + "api/sensor", jsonObject.toString());
        } catch (IOException e) {
            Log.e(AUTHMEIO, "Failed to post result to sensor: ", e);
        }
    }

    private void endActivity(int result) {
        setResult(result);
        AuthScreen.this.finish();
    }

}