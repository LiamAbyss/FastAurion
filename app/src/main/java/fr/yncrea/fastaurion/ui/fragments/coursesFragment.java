package fr.yncrea.fastaurion.ui.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import fr.yncrea.fastaurion.MainActivity;
import fr.yncrea.fastaurion.R;
import fr.yncrea.fastaurion.adapters.CoursesAdapter;
import fr.yncrea.fastaurion.utils.Course;


import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import fr.yncrea.fastaurion.FastAurionApplication;

public class coursesFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener{

    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private MainActivity parent;

    public coursesFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_courses_list, container, false);
        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.coursesRecyclerView);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(FastAurionApplication.getContext()));
        mRecyclerView.setOnTouchListener((v, event) -> {
            mRecyclerView.onTouchEvent(event);
            if(event != null)
                parent.onTouchEvent(event);
            return true;
        });
        return rootView;
    }



    public void onCoursesRetrieved(List<Course> planning) {
        if(null != planning){
            final CoursesAdapter adapter = new CoursesAdapter(planning);
            mRecyclerView.setHasFixedSize(false);
            mRecyclerView.setLayoutManager(new LinearLayoutManager(FastAurionApplication.getContext()));
            mRecyclerView.setAdapter(adapter);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSwipeRefreshLayout = getView().findViewById(R.id.refreshLayout);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        parent = (MainActivity)getActivity();
    }

    @Override
    public void onRefresh() {
        mExecutor.execute(() -> {
            parent.refresh();
            parent.runOnUiThread(()->mSwipeRefreshLayout.setRefreshing(false));
        });
    }

    public void setRefreshing(boolean bool) {
        parent.runOnUiThread(()->mSwipeRefreshLayout.setRefreshing(bool));
    }

}

