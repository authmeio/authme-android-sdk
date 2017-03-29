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
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.UUID;

import io.authme.sdk.server.Callback;
import io.authme.sdk.server.Config;
import io.authme.sdk.server.PostData;

import static io.authme.sdk.server.Config.INVALID_CONFIG;
import static io.authme.sdk.server.Config.LOGIN_PATTERN;
import static io.authme.sdk.server.Config.PIN_SET;
import static io.authme.sdk.server.Config.RESET_PATTERN;
import static io.authme.sdk.server.Config.RESULT_FAILED;
import static io.authme.sdk.server.Config.SIGNUP_PATTERN;


public class AuthScreen extends Activity {

    private static final String AUTHMEIO = "AUTHMEIO";
    private String referenceId, resetKey = null, statusbar = null,
            titlecolor = null, titletext = null,
            logo = null, email = null, pin = null;
    private Config config;

    public AuthScreen() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        config = new Config(AuthScreen.this);

        email = getIntent().getStringExtra("email");

        if (!Config.isValidEmail(email)) {
            Toast.makeText(getApplicationContext(), "Invalid Email", Toast.LENGTH_LONG)
                    .show();
            return;
        }

        if (!config.isValidConfig()) {
            endActivity(INVALID_CONFIG);
            return;
        }

        if (getIntent().hasExtra("statusbar")) {
            statusbar = getIntent().getStringExtra("statusbar");
        }

        if (getIntent().hasExtra("titlecolor")) {
            titlecolor = getIntent().getStringExtra("titlecolor");
        }

        if (getIntent().hasExtra("titletext")) {
            titletext = getIntent().getStringExtra("titletext");
        }

        if (getIntent().hasExtra("logo")) {
            logo = getIntent().getStringExtra("logo");
        }

        if (getIntent().hasExtra("referenceId")) {
            referenceId = getIntent().getStringExtra("referenceId");
        }
        else {
            referenceId = UUID.randomUUID().toString();
        }

        if (getIntent().hasExtra("resetKey")) {
            resetKey = getIntent().getStringExtra("resetKey");
            resetFlow();
            return;
        }

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

        checkIfuserExists(new Callback() {
            @Override
            public void onTaskExecuted(String response) {
                startLockScreen(response);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SIGNUP_PATTERN: {
                switch (resultCode) {
                    case RESULT_OK:
                        char[] pattern = data.getCharArrayExtra(LockPatternActivity.EXTRA_PATTERN);
                        config.setByteArray(pattern);
                        startPinActivity();
                        break;
                    case RESULT_CANCELED:
                        endActivity(RESULT_CANCELED);
                        break;
                    default:
                        break;
                }
            }
            break;

            case LOGIN_PATTERN: {
                switch (resultCode) {
                    case RESULT_OK:
                        try {
                            postAuthResult(data.getStringExtra(LockPatternActivity.PATTERN_JSON));
                        } catch (JSONException | InvalidKeyException e) {
                            Toast.makeText(AuthScreen.this, "Failed to post result: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case RESULT_CANCELED:
                        clearJson();
                        endActivity(RESULT_CANCELED);
                        break;
                    case RESULT_FAILED:
                        clearJson();
                        endActivity(RESULT_FAILED);
                        break;
                    case RESET_PATTERN:
                        Toast.makeText(AuthScreen.this, "Forgot pattern", Toast.LENGTH_SHORT).show();
                        clearJson();
                        endActivity(RESET_PATTERN);
                        break;
                }
            }
            break;

            case PIN_SET: {
                switch (resultCode) {
                    case RESULT_OK:
                        pin = data.getStringExtra("pin");
                        completeSignup();
                        break;

                    case RESULT_CANCELED:
                        clearJson();
                        endActivity(RESULT_CANCELED);
                        break;

                    case RESULT_FAILED:
                        clearJson();
                        endActivity(RESULT_FAILED);
                        break;

                    case RESET_PATTERN:
                        clearJson();
                        endActivity(RESET_PATTERN);
                        break;

                    default:
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

        callSensorVerify(biggerJson);

    }

    private void callSensorVerify(String biggerJson) throws InvalidKeyException, JSONException {
        JSONObject jsonObject = new JSONObject();

        if (!TextUtils.isEmpty(biggerJson)) {

            try {
                jsonObject = new JSONObject(biggerJson);
                jsonObject.put("OrderId", referenceId);
                jsonObject.put("User", email);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            endActivity(RESULT_FAILED, "Unable to fetch sensor data");
            return;
        }

        jsonObject.put("Otp", config.getOTP());

        Callback callback = new Callback() {
            @Override
            public void onTaskExecuted(String response) {
                endActivity(LOGIN_PATTERN, response);
            }
        };

        io.authme.sdk.server.PostData postData = new io.authme.sdk.server.PostData(callback, config.getApiKey());
        try {
            postData.runPost(config.getServerURL() + "api/sensor", jsonObject.toString());
        } catch (IOException e) {
            endActivity(RESULT_FAILED, "Failed to post result to sensor API");
        }
    }

    private void endActivity(int result) {
        setResult(result);
        AuthScreen.this.finish();
    }

    private void endActivity(int result, String data) {
        Intent intent = new Intent();
        intent.putExtra("response", data);
        setResult(result, intent);
        AuthScreen.this.finish();
    }

    private void completeSignup() {
        Callback callback = new Callback() {
            @Override
            public void onTaskExecuted(String response) {
                JSONObject jsonObject;
                try {
                    jsonObject = new JSONObject(response);
                    if (jsonObject.getInt("Status") == 201) {
                        JSONObject data = jsonObject.getJSONObject("Data");
                        config.setEmailId(email);
                        config.setSecretKey(data.getString("Key"));
                        endActivity(SIGNUP_PATTERN);
                    }
                    else if (jsonObject.getInt("Status") == 200) {
                        endActivity(RESET_PATTERN);
                    }
                    else{
                        endActivity(RESULT_FAILED, response);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        };

        JSONObject request = new JSONObject();
        try {
            request.put("Identifier", config.getDeviceId());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            request.put("Email", email);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            request.put("Pin", pin);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (!TextUtils.isEmpty(resetKey)) {
            try {
                request.put("ResetKey", resetKey);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        try {
            new PostData(callback, config.getApiKey()).runPost(config.getServerURL() + "user/new", request.toString());
        } catch (IOException e) {
            e.printStackTrace();
            endActivity(RESULT_FAILED);
        }

    }

    private void checkIfuserExists(Callback callback) {

        JSONObject user = new JSONObject();

        if (TextUtils.equals(email, config.getEmailId())) {
            JSONObject response = new JSONObject();
            try {
                response.put("Status", 200);
                JSONObject data = new JSONObject();
                data.put("Message", "FOUND");
                data.put("Data", null);
                response.put("Data", data);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            callback.onTaskExecuted(response.toString());
            return;
        }

        try {
            user.put("Email", email);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            new PostData(callback, config.getApiKey()).runPost(config.getServerURL() + "user/is", user.toString());
        } catch (IOException e) {
            e.printStackTrace();
            endActivity(RESULT_FAILED);
        }
    }

    private void startLockScreen(String userstatus) {
        String stringArray = config.getPatternString();

        Intent intent = new Intent(AuthScreen.this, LockPatternActivity.class);

        JSONObject status;

        try {
            status = new JSONObject(userstatus);
            if (status.getInt("Status") == 200) {
                String userExists = status.getJSONObject("Data").getString("Message");
                if (TextUtils.equals(userExists, "FOUND")) {
                    if (TextUtils.isEmpty(stringArray)) {
                        endActivity(RESET_PATTERN);
                    }
                    else {
                        char[] charArray = stringArray.toCharArray();
                        intent.setAction(LockPatternActivity.ACTION_COMPARE_PATTERN);
                        intent.putExtra(LockPatternActivity.EXTRA_PATTERN, charArray);
                        startActivityForResult(addOns(intent), LOGIN_PATTERN);
                    }

                }
                else if (TextUtils.equals(userExists, "NOTFOUND")){
                    signupUser(intent);
                }
                else {
                    return;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void resetFlow() {
        Intent intent = new Intent(AuthScreen.this, LockPatternActivity.class);
        signupUser(addOns(intent));
    }

    private void startPinActivity() {
        Intent intent = new Intent(AuthScreen.this, PinScreen.class);
        startActivityForResult(addOns(intent), PIN_SET);
    }

    private void signupUser(Intent intent) {
        intent.setAction(LockPatternActivity.ACTION_CREATE_PATTERN);
        startActivityForResult(addOns(intent), SIGNUP_PATTERN);
    }

    private Intent addOns(Intent intent) {
        if (!TextUtils.isEmpty(statusbar)) {
            intent.putExtra("statusbar", statusbar);
        }

        if (!TextUtils.isEmpty(titletext)) {
            intent.putExtra("titletext", titletext);
        }

        if (!TextUtils.isEmpty(titlecolor)) {
            intent.putExtra("titlecolor", titlecolor);
        }

        if (!TextUtils.isEmpty(logo)) {
            intent.putExtra("logo", logo);
        }

        return intent;
    }
}
