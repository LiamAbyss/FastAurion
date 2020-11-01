package fr.yncrea.fastaurion;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import fr.yncrea.fastaurion.api.Aurion;
import fr.yncrea.fastaurion.api.AurionService;
import fr.yncrea.fastaurion.utils.*;

public class LoginActivity extends AppCompatActivity implements View.OnClickListener{
    private Executor executor = Executors.newSingleThreadExecutor();
    private EditText mLoginEditText;
    private EditText mPasswordEditText;
    private AurionService aurionService;
    private Aurion aurion = new Aurion();
    private Toast mToast;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_layout);
        mLoginEditText = (EditText) findViewById(R.id.loginEditText);
        mPasswordEditText = (EditText) findViewById(R.id.passwordEditText);

        final String username = PreferenceUtils.getLogin();
        final String password = PreferenceUtils.getPassword();
        if(!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
            connect(username, password);
        }

        /*OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl("https://aurion.yncrea.fr")
                .build();

        this.aurionService = retrofit.create(AurionService.class);*/
    }

    @Override
    public void onClick(View v) {

        if(TextUtils.isEmpty(mLoginEditText.getText())){
            showToast(this, R.string.error_no_login, Toast.LENGTH_LONG);
            return;
        }

        String login = mLoginEditText.getText().toString();

        if(TextUtils.isEmpty(mPasswordEditText.getText())){
            showToast(this, R.string.error_no_password, Toast.LENGTH_LONG);
            return;
        }

        connect(mLoginEditText.getText().toString(), mPasswordEditText.getText().toString());
    }

    void connect(String username, String password){
        mLoginEditText.setEnabled(false);
        mPasswordEditText.setEnabled(false);

        executor.execute(() -> {
            runOnUiThread(()-> {
                showToast(FastAurionApplication.getContext(), "Logging in...", Toast.LENGTH_SHORT);
            });
            String[] sessionID = aurion.connect(username, password);
            if(sessionID[0] == "success"){
                PreferenceUtils.setLogin(sessionID[1]);
                runOnUiThread(()-> {
                    showToast(FastAurionApplication.getContext(), "Retrieving data...", Toast.LENGTH_LONG);
                });
                String[] name = aurion.getName(sessionID[1]);
                if(name[0] == "success"){
                    PreferenceUtils.setName(name[1]);
                    PreferenceUtils.setLogin(mLoginEditText.getText().toString());
                    PreferenceUtils.setPassword(mPasswordEditText.getText().toString());
                    runOnUiThread(() -> {
                        mLoginEditText.setEnabled(true);
                        mPasswordEditText.setEnabled(true);
                    });
                    startActivity(getHomeIntent(sessionID[1], name[1]));
                    return;
                }
                else {
                    if(name[0].contains("connection")) {
                        runOnUiThread(() -> {
                            showToast(FastAurionApplication.getContext(), "Connection error", Toast.LENGTH_LONG);
                        });
                    }
                    else {
                        runOnUiThread(() -> {
                            showToast(FastAurionApplication.getContext(), "Authentication Failed", Toast.LENGTH_LONG);
                        });
                    }
                    runOnUiThread(() -> {
                        mLoginEditText.setEnabled(true);
                        mPasswordEditText.setEnabled(true);
                    });
                    return;
                }
            }
            runOnUiThread(() -> {
                mLoginEditText.setEnabled(true);
                mPasswordEditText.setEnabled(true);
            });
            if(sessionID[0].contains("connection")){
                runOnUiThread(()-> {
                    showToast(FastAurionApplication.getContext(), "Connection error", Toast.LENGTH_LONG);
                });
            }
            else {
                runOnUiThread(() -> {
                    showToast(FastAurionApplication.getContext(), "Authentication Failed", Toast.LENGTH_LONG);
                });
            }
            /*Response<ResponseBody> res = null;
            final String[] sessionID = {""};
            Call<ResponseBody> request = aurionService.getSessionIdResponse(mLoginEditText.getText().toString(), mPasswordEditText.getText().toString());
            runOnUiThread(()-> {
                showToast(FastAurionApplication.getContext(), "Logging in...", Toast.LENGTH_SHORT);
            });
            request.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    String cookies = response.headers().get("Set-Cookie");
                    if(response.code() == 302){
                        Log.d("LOGIN", "Login success \n");
                        sessionID[0] = cookies.substring(cookies.indexOf("JSESSIONID"), cookies.indexOf(";", cookies.indexOf("JSESSIONID")));
                        PreferenceUtils.setLogin(sessionID[0]);

                        // Request home page to get user real name
                        // Stores it into PreferenceUtils
                        getNameThenLogIn();
                    }
                    else{
                        runOnUiThread(()-> {
                            showToast(FastAurionApplication.getContext(), "Login Failed", Toast.LENGTH_LONG);
                        });
                        mLoginEditText.setEnabled(true);
                        mPasswordEditText.setEnabled(true);
                    }

                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.d("LOGIN", t.getMessage());
                    runOnUiThread(()-> {
                        showToast(FastAurionApplication.getContext(), "Connection error", Toast.LENGTH_LONG);
                    });
                    mLoginEditText.setEnabled(true);
                    mPasswordEditText.setEnabled(true);
                }
            });*/
        });
    }

    /*private void getNameThenLogIn(){
        String login = PreferenceUtils.getLogin();
        final String[] name = {""};
        Call<ResponseBody> requestName = aurionService.getHomePageHtml(login);
        requestName.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                String body = null;
                try {
                    body = response.body().string();
                    name[0] = body.substring(body.indexOf("<h3>") + 4, body.indexOf("</h3>"));
                    PreferenceUtils.setName(name[0]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if(response.isSuccessful()){
                    Log.d("LOGIN", "Name parsing success \n");
                    startActivity(getHomeIntent(login, name[0]));
                    mLoginEditText.setEnabled(true);
                    mPasswordEditText.setEnabled(true);
                }
                else{
                    runOnUiThread(()-> {
                        showToast(FastAurionApplication.getContext(), "Name parsing failed", Toast.LENGTH_LONG);
                    });
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.d("LOGIN", t.getMessage());
                runOnUiThread(()-> {
                    showToast(FastAurionApplication.getContext(), "Connection error", Toast.LENGTH_LONG);
                });
                mLoginEditText.setEnabled(true);
                mPasswordEditText.setEnabled(true);
            }
        });
    }*/

    private Intent getHomeIntent(String userName, String name)
    {
        Intent intent = new Intent(this, MainActivity.class);
        final Bundle extras = new Bundle();
        extras.putString(Constants.Login.EXTRA_LOGIN, userName);
        extras.putString(Constants.Preferences.PREF_NAME, name);
        intent.putExtras(extras);
        return intent;
    }

    private void showToast(Context context, int resId, int duration){
        if(mToast != null) mToast.cancel();
        mToast = Toast.makeText(context, resId, duration);
        mToast.show();
    }

    private void showToast(Context context, CharSequence text, int duration){
        if(mToast != null) mToast.cancel();
        mToast = Toast.makeText(context, text, duration);
        mToast.show();
    }
}
