package fr.yncrea.scorpion;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;
import androidx.room.Room;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.google.gson.JsonArray;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import fr.yncrea.scorpion.api.Aurion;
import fr.yncrea.scorpion.api.GithubService;
import fr.yncrea.scorpion.database.ScorpionDatabase;
import fr.yncrea.scorpion.model.Planning;
import fr.yncrea.scorpion.ui.fragments.CoursesFragment;
import fr.yncrea.scorpion.utils.Constants;
import fr.yncrea.scorpion.utils.Course;
import fr.yncrea.scorpion.utils.PreferenceUtils;
import fr.yncrea.scorpion.utils.StackableRequest;
import fr.yncrea.scorpion.utils.UtilsMethods;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity implements GestureDetector.OnGestureListener {
    private Executor mExecutorDatabase = Executors.newSingleThreadExecutor();
    private Executor addExecutor = Executors.newSingleThreadExecutor();
    private Executor computeExecutor = Executors.newSingleThreadExecutor();
    private Stack<StackableRequest> requestStack = new Stack<>();
    private Aurion mAurion = new Aurion();
    private GithubService mGithubService;
    private List<Course> mCourses;
    private CoursesFragment mCoursesFragment = new CoursesFragment();
    private CoursesFragment mOtherCoursesFragment = new CoursesFragment();
    private boolean isOtherFragment = false;
    private int animationType = 0;
    private Toast mToast;
    private GestureDetectorCompat mDetector;
    private ScorpionDatabase db;
    private int weekIndex = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);
    private int lastWeekShown;
    private Timer updater;
    private AlertDialog confirmWindow;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Intent intent = getIntent();
        if (null != intent) {
            final Bundle extras = intent.getExtras();
            if ((null != extras) && (extras.containsKey(Constants.Login.EXTRA_LOGIN))) {
                getSupportActionBar().setSubtitle(extras.getString(Constants.Preferences.PREF_NAME));
                getSupportActionBar().setTitle(getSupportActionBar().getTitle() + " " + getString(R.string.app_version));
            }
        }
        // Instantiate the gesture detector with the
        // application context and an implementation of
        // GestureDetector.OnGestureListener
        mDetector = new GestureDetectorCompat(this,this);

        mExecutorDatabase.execute(() -> {
            db = Room.databaseBuilder(getApplicationContext(), ScorpionDatabase.class, "Scorpion.db").build();
            getSupportFragmentManager().beginTransaction().add(R.id.container, mCoursesFragment, "planning").commit();
            computeRequests();
            addRequestToStack(weekIndex, false, true);
            addRequestToStack(weekIndex + 1, false, false);
            addRequestToStack(weekIndex - 1, false, false);
        });

        findViewById(R.id.toTheLeftButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toTheLeft();
            }
        });

        findViewById(R.id.toTheRightButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toTheRight();
            }
        });

        findViewById(R.id.todaybutton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                weekIndex = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);
                if(lastWeekShown < weekIndex) animationType = 1;
                else animationType = -1;
                showWeek(weekIndex);
            }
        });

        mGithubService = new Retrofit.Builder()
            .baseUrl("https://api.github.com")
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(GithubService.class);

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if(confirmWindow == null || !confirmWindow.isShowing()) requestUpdate();
            }
        }, 0, 300000);
    }

    public void requestUpdate() {
        //mExecutorGit.execute(() -> {
            try {
                Response<JsonArray> releases = mGithubService.getReleases().execute();
                String latestVersion = releases.body().get(0).getAsJsonObject().get("tag_name").getAsString();
                if(!latestVersion.equals(getString(R.string.app_version))) {
                    String latestVersionDesc = releases.body().get(0).getAsJsonObject().get("body").getAsString();
                    //String latestVersionLink = releases.body().get(0).getAsJsonObject().get("assets").getAsJsonArray().get(0).getAsJsonObject().get("browser_download_url").getAsString();
                    runOnUiThread(() -> {
                        confirmWindow = new AlertDialog.Builder(this)
                                .setTitle("Update available !")
                                .setMessage("A new version of Scorpion is available !\n\n"
                                        + "Version : " + latestVersion + "\n\n"
                                        + "Description : " + latestVersionDesc)
                                .setPositiveButton("Update now", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        try {
                                            Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://liamabyss.github.io/scorpion/"));
                                            startActivity(myIntent);
                                        }
                                        catch(ActivityNotFoundException e) {
                                            //runOnUiThread(() -> showToast(this, "No application can handle this request." + " Please install a web browser",  Toast.LENGTH_LONG));
                                            e.printStackTrace();
                                        }
                                    }
                                })
                                .setNegativeButton("Maybe later", null).show();
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        //});
    }

    public void addRequestToStack(Integer week, boolean forceRequest, boolean mustDraw) {
        addExecutor.execute(() -> {
            if(!mustDraw) {
                requestStack.add(requestStack.size() - 1, new StackableRequest(week, forceRequest, mustDraw));
            }
            else {
                requestStack.push(new StackableRequest(week, forceRequest, mustDraw));
                if(forceRequest || db.aurionPlanningDao().getPlanningById(week) == null)
                    runOnUiThread(() -> getCurrentCoursesFragment().setRefreshing(true));
            }
        });
    }

    private void computeRequests() {
        computeExecutor.execute(() -> {
            while(requestStack.isEmpty()) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            while(!requestStack.isEmpty()) {
                StackableRequest req = requestStack.pop();
                requestPlanning(req.getWeek(), req.isForceRequest(), req.isMustDraw());
                if(req.isMustDraw()) runOnUiThread(() -> getCurrentCoursesFragment().setRefreshing(false));
                while(requestStack.isEmpty()) {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
    
    public boolean isInternetAvailable() {
        try {
            InetAddress address = InetAddress.getByName("www.google.com");
            return !address.equals("");
        } catch (UnknownHostException e) {
            // Log error
        }
        return false;
    }
    public void requestPlanning(int index, boolean forceRequest, boolean mustDraw) {

        Planning planning = db.aurionPlanningDao().getPlanningById(index);
        List<Course> tmpCourses;
        if(planning != null) {
            tmpCourses = UtilsMethods.planningFromString(planning.toString());
        } else {
            tmpCourses = new ArrayList<>();
        }
        if(tmpCourses.size() != 0 && !forceRequest){
            if(index == weekIndex && mustDraw) {
                showWeek(index);
            }
        }
        else{
            Planning toInsert = new Planning();
            String[] response = mAurion.getCalendarAsXML(PreferenceUtils.getSessionId(), index);
            String xml;
            if(response[0] == "success") {
                xml = response[1];
            }
            else if(response[0].contains("authentication") && connect()) {
                requestPlanning(index, forceRequest, mustDraw);
                return;
            }
            else {
                runOnUiThread(() -> showToast(ScorpionApplication.getContext(), response[0], Toast.LENGTH_LONG));
                return;
            }
            JSONArray planningJSON = UtilsMethods.XMLToJSONArray(xml);
            if(planningJSON == null) {
                runOnUiThread(() -> showToast(ScorpionApplication.getContext(), "Request failed...Retrying...", Toast.LENGTH_LONG));
                requestPlanning(index, forceRequest, mustDraw);
                return;
            }
            try {
                tmpCourses = UtilsMethods.JSONArrayToCourseList(planningJSON);
                toInsert.id = index;
                toInsert.planningString = UtilsMethods.planningToString(tmpCourses);

                if(db.aurionPlanningDao().getPlanningById(index) == null) {
                    db.aurionPlanningDao().insertPlanning(toInsert);
                }
                else {
                    db.aurionPlanningDao().updatePlanning(toInsert);
                }
                if(index == weekIndex && mustDraw) {
                    showWeek(index);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void showWeek(int index) {
        mExecutorDatabase.execute(() -> {
            Planning planning = db.aurionPlanningDao().getPlanningById(index);
            int enterAnim;
            int exitAnim;
            if(animationType == 1) {
                enterAnim = R.anim.slide_in_from_right;
                exitAnim = R.anim.slide_out_from_right;
            }
            else {
                enterAnim = R.anim.slide_in_from_left;
                exitAnim = R.anim.slide_out_from_left;
            }
            if(planning != null) {
                mCourses = UtilsMethods.planningFromString(planning.toString());
                if(lastWeekShown == index) {
                    runOnUiThread(() -> getCurrentCoursesFragment().onCoursesRetrieved(mCourses));
                }
                else if(isOtherFragment) {
                    mCoursesFragment = new CoursesFragment();
                    getSupportFragmentManager().beginTransaction()
                            .setCustomAnimations(enterAnim, exitAnim)
                            .replace(R.id.container, mCoursesFragment, "planning")
                            .commit();
                    runOnUiThread(() -> mCoursesFragment.onCoursesRetrieved(mCourses));
                    isOtherFragment = false;
                }
                else {
                    mOtherCoursesFragment = new CoursesFragment();
                    getSupportFragmentManager().beginTransaction()
                            .setCustomAnimations(enterAnim, exitAnim)
                            .replace(R.id.container, mOtherCoursesFragment, "planning")
                            .commit();
                    runOnUiThread(() -> mOtherCoursesFragment.onCoursesRetrieved(mCourses));
                    isOtherFragment = true;
                }
            }
            lastWeekShown = index;
        });
    }

    public boolean connect() {
        String[] sessionID = mAurion.connect(PreferenceUtils.getLogin(), PreferenceUtils.getPassword());
        if(sessionID[0].equals("success")){
            PreferenceUtils.setSessionId(sessionID[1]);
            return true;
        }
        if(sessionID[0].contains("connection")){
            runOnUiThread(()-> showToast(ScorpionApplication.getContext(), "Connection error", Toast.LENGTH_LONG));
        }
        else {
            runOnUiThread(() -> showToast(ScorpionApplication.getContext(), "Authentication Failed", Toast.LENGTH_LONG));
        }
        return false;
    }

    public void refresh() {
        //Refresh the planning
        Log.d("REFRESH", "Refreshing...");
        addRequestToStack(weekIndex, true, true);
        addRequestToStack(weekIndex + 1, false, false);
        addRequestToStack(weekIndex - 1, false, false);
        Log.d("REFRESH", "Finish refreshing...");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.scorpion, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        int id = item.getItemId();
        if( id == R.id.actionLogout) {
            PreferenceUtils.setLogin(null);
            finish();
            return true;
        }
        else if( id == R.id.actionRefresh) {
            refresh();
            return true;
        }
        else if( id == R.id.actionReset) {
            mExecutorDatabase.execute(() -> {
                db.clearAllTables();
                weekIndex = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);
                refresh();
            });
            return true;
        }
        else if( id == R.id.actionGoToReleases) {
            Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://liamabyss.github.io/scorpion/"));
            startActivity(myIntent);
        }
        return super.onOptionsItemSelected(item);
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

    @Override
    public boolean onTouchEvent(MotionEvent event){
        if (this.mDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    public void toTheRight() {
        //mExecutorFling.execute(() -> {
            weekIndex++;
            animationType = 1;
            //runOnUiThread(() -> getCurrentCoursesFragment().setRefreshing(true));
            addRequestToStack(weekIndex, false, true);
            addRequestToStack(weekIndex + 1, false, false);
            //runOnUiThread(() -> getCurrentCoursesFragment().setRefreshing(false));
            /*mExecutor.execute(() -> {
                requestPlanning(weekIndex + 1, false, false);
                //requestPlanning(weekIndex - 1, false, false);
            });*/
        //});
    }

    public void toTheLeft() {
        //mExecutorFling.execute(() -> {
            animationType = -1;
            weekIndex--;
            //runOnUiThread(() -> getCurrentCoursesFragment().setRefreshing(true));
            addRequestToStack(weekIndex, false, true);
            addRequestToStack(weekIndex - 1, false, false);
            //runOnUiThread(() -> getCurrentCoursesFragment().setRefreshing(false));
            /*mExecutor.execute(() -> {
                //requestPlanning(weekIndex + 1, false, false);
                requestPlanning(weekIndex - 1, false, false);
            });*/
        //});
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        // To the left
        if(Math.abs(velocityY) > 3000) return false;
        if(velocityX > 3000) {
            Log.d("FLING", "To the left !");
            toTheLeft();
        }
        else if(velocityX < -3000) {
            Log.d("FLING", "To the right !");
            toTheRight();
        }
        return true;
    }

    public CoursesFragment getCurrentCoursesFragment() {
        if(isOtherFragment) return mOtherCoursesFragment;
        else return mCoursesFragment;
    }
}