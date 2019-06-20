/*
 * Copyright (C) 2015 Domoticz - Mark Heinis
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package nl.hnogames.domoticz.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import hugo.weaving.DebugLog;
import jp.wasabeef.recyclerview.animators.SlideInUpAnimator;
import nl.hnogames.domoticz.Interfaces.DomoticzFragmentListener;
import nl.hnogames.domoticz.MainActivity;
import nl.hnogames.domoticz.PlanActivity;
import nl.hnogames.domoticz.R;
import nl.hnogames.domoticz.Utils.SharedPrefUtil;
import nl.hnogames.domoticz.Utils.UsefulBits;
import nl.hnogames.domoticzapi.Containers.ConfigInfo;
import nl.hnogames.domoticzapi.Containers.UserInfo;
import nl.hnogames.domoticzapi.Domoticz;
import nl.hnogames.domoticzapi.DomoticzValues;
import nl.hnogames.domoticzapi.Utils.PhoneConnectionUtil;
import nl.hnogames.domoticzapi.Utils.ServerUtil;

public class DomoticzRecyclerFragment extends Fragment {

    public RecyclerView gridView;
    public SwipeRefreshLayout mSwipeRefreshLayout;
    public CoordinatorLayout coordinatorLayout;
    public Domoticz mDomoticz;
    public SharedPrefUtil mSharedPrefs;
    public PhoneConnectionUtil mPhoneConnectionUtil;
    private DomoticzFragmentListener listener;
    private String fragmentName;
    private TextView debugText;
    private boolean debug;
    private ViewGroup root;
    private String sort = "";

    private int scrolledDistance = 0;
    private int SCROLL_THRESHOLD = 600;
    private boolean controlsVisible = true;

    public DomoticzRecyclerFragment() {
    }

    public void setTheme() {
        if (mSharedPrefs == null)
            mSharedPrefs = new SharedPrefUtil(getActivity());

        if (mSharedPrefs.darkThemeEnabled()) {
            if (gridView != null)
                gridView.setBackgroundColor(getResources().getColor(R.color.background_dark));
            if ((root.findViewById(R.id.debugLayout)) != null)
                (root.findViewById(R.id.debugLayout)).setBackgroundColor(getResources().getColor(R.color.background_dark));
            if ((root.findViewById(R.id.coordinatorLayout)) != null)
                (root.findViewById(R.id.coordinatorLayout)).setBackgroundColor(getResources().getColor(R.color.background_dark));
            if (root.findViewById(R.id.errorImage) != null)
                ((ImageView) root.findViewById(R.id.errorImage)).setImageDrawable(getResources().getDrawable(R.drawable.sad_smiley_dark));

            mSwipeRefreshLayout.setColorSchemeResources(
                R.color.secondary,
                R.color.secondary_dark,
                R.color.background_dark);
        }
    }

    public ConfigInfo getServerConfigInfo(Context context) {
        try {
            Activity activity = getActivity();
            if (activity instanceof MainActivity) {
                return ((MainActivity) getActivity()).getConfig();
            } else if (activity instanceof PlanActivity) {
                return ((PlanActivity) getActivity()).getConfig();
            } else return null;
        } catch (Exception ex) {
        }
        return null;
    }

    public UserInfo getCurrentUser(Context context, Domoticz domoticz) {
        try {
            ConfigInfo config = getServerConfigInfo(context);
            if (config != null) {
                for (UserInfo user : config.getUsers()) {
                    if (user.getUsername().equals(domoticz.getUserCredentials(Domoticz.Authentication.USERNAME)))
                        return user;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @DebugLog
    public void hideViews() {
        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).hideViews();
        controlsVisible = false;
        scrolledDistance = 0;
    }

    @DebugLog
    public void showViews() {
        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).showViews();
        controlsVisible = true;
        scrolledDistance = 0;
    }

    public String getSort() {
        return sort;
    }

    public ServerUtil getServerUtil() {
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            return ((MainActivity) getActivity()).getServerUtil();
        } else if (activity instanceof PlanActivity) {
            return ((PlanActivity) getActivity()).getServerUtil();
        } else return null;
    }

    public void sortFragment(String sort) {
        this.sort = sort;
        refreshFragment();
    }

    public void initViews(View root) {
        gridView = root.findViewById(R.id.my_recycler_view);
        if (mSharedPrefs == null)
            mSharedPrefs = new SharedPrefUtil(getContext());
        setGridViewLayout();
        coordinatorLayout = root.findViewById(R.id.coordinatorLayout);
        mSwipeRefreshLayout = root.findViewById(R.id.swipe_refresh_layout);

        //setting up our OnScrollListener
        gridView.setOnScrollListener(new RecyclerView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int firstVisibleItem = 0;
                try {
                    firstVisibleItem = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
                } catch (Exception ex) {
                    int[] firstVisibleItems = null;
                    firstVisibleItems = ((StaggeredGridLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPositions(firstVisibleItems);
                    if (firstVisibleItems != null)
                        firstVisibleItem = firstVisibleItems[0];
                }
                if (firstVisibleItem == 0) {
                    if (!controlsVisible) {
                        showViews();
                    }
                } else {
                    if (scrolledDistance > SCROLL_THRESHOLD && controlsVisible) {
                        hideViews();
                    } else if (scrolledDistance < -SCROLL_THRESHOLD && !controlsVisible) {
                        showViews();
                    }
                }
                if ((controlsVisible && dy > 0) || (!controlsVisible && dy < 0)) {
                    scrolledDistance += dy;
                }
            }
        });
    }

    public void setGridViewLayout() {
        try {
            boolean isTablet = false;
            boolean isPortrait = false;

            if (getActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
                isPortrait = true;
            if (getActivity() instanceof MainActivity) {
                isTablet = !
                    ((MainActivity) getActivity()).onPhone;
            }
            gridView.setHasFixedSize(true);

            if (isTablet) {
                if (isPortrait) {
                    GridLayoutManager mLayoutManager = new GridLayoutManager(getActivity(), 1);
                    gridView.setLayoutManager(mLayoutManager);
                } else {
                    GridLayoutManager mLayoutManager = new GridLayoutManager(getActivity(), 2);
                    gridView.setLayoutManager(mLayoutManager);
                }
            } else {
                GridLayoutManager mLayoutManager = new GridLayoutManager(getActivity(), 1);
                gridView.setLayoutManager(mLayoutManager);
            }

            gridView.setItemAnimator(new SlideInUpAnimator(new OvershootInterpolator(1f)));
        } catch (Exception ex) {
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        root = (ViewGroup) inflater.inflate(R.layout.fragment_cameras, null);

        initViews(root);
        setTheme();
        return root;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSharedPrefs = new SharedPrefUtil(getActivity());
        mDomoticz = new Domoticz(getActivity(), AppController.getInstance().getRequestQueue());
        debug = mSharedPrefs.isDebugEnabled();

        if (debug)
            showDebugLayout();

        checkConnection();
    }

    /**
     * Connects to the attached fragment to cast the DomoticzFragmentListener to.
     * Throws ClassCastException if the fragment does not implement the DomoticzFragmentListener
     *
     * @param fragment fragment to cast the DomoticzFragmentListener to
     */
    public void onAttachFragment(Fragment fragment) {
        fragmentName = fragment.toString();

        try {
            listener = (DomoticzFragmentListener) fragment;
        } catch (ClassCastException e) {
            throw new ClassCastException(
                fragment.toString() + " must implement DomoticzFragmentListener");
        }
    }

    public void showSpinner(boolean show) {
        if (show) {
            if (gridView != null)
                gridView.setVisibility(View.GONE);
        } else {
            if (gridView != null)
                gridView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Checks for a active connection
     */
    public void checkConnection() {
        if (listener == null) {
            //Get listener
            List<Fragment> fragments = getFragmentManager().getFragments();
            onAttachFragment(fragments.get(0) != null ? fragments.get(0) : fragments.get(1));
        }
        mPhoneConnectionUtil = new PhoneConnectionUtil(getContext());
        if (mPhoneConnectionUtil.isNetworkAvailable()) {
            addDebugText("Connection OK");
            listener.onConnectionOk();
        } else {
            listener.onConnectionFailed();
            setErrorMessage(getString(R.string.error_notConnected));
        }
    }

    /**
     * Handles the success messages
     *
     * @param result Result text to handle
     */
    public void successHandling(String result, boolean displayToast) {
        if (result.equalsIgnoreCase(DomoticzValues.Result.ERROR))
            Toast.makeText(getActivity(), R.string.action_failed, Toast.LENGTH_SHORT).show();
        else if (result.equalsIgnoreCase(DomoticzValues.Result.OK)) {
            if (displayToast)
                Toast.makeText(getActivity(), R.string.action_success, Toast.LENGTH_SHORT).show();
        } else {
            if (displayToast)
                Toast.makeText(getActivity(), R.string.action_unknown, Toast.LENGTH_SHORT).show();
        }
        if (debug) addDebugText("- Result: " + result);
    }

    /**
     * Handles the error messages
     *
     * @param error Exception
     */
    public void errorHandling(Exception error) {

        showSpinner(false);
        error.printStackTrace();
        String errorMessage = mDomoticz.getErrorMessage(error);

        if (mPhoneConnectionUtil == null)
            mPhoneConnectionUtil = new PhoneConnectionUtil(getContext());
        if (mPhoneConnectionUtil.isNetworkAvailable()) {
            if (errorMessage.contains("No value for result")) {
                setMessage(getString(R.string.no_data_on_domoticz));
            } else {
                setErrorMessage(errorMessage);
            }
        } else {
            if (coordinatorLayout != null) {
                UsefulBits.showSnackbar(getContext(), coordinatorLayout, R.string.error_notConnected, Snackbar.LENGTH_SHORT);
                if (getActivity() instanceof MainActivity)
                    ((MainActivity) getActivity()).Talk(R.string.error_notConnected);
            }
        }
    }

    public ActionBar getActionBar() {
        return ((AppCompatActivity) getActivity()).getSupportActionBar();
    }

    private void setErrorMessage(String message) {

        if (debug) addDebugText(message);
        else {
            Logger(fragmentName, message);
            setErrorLayoutMessage(message);
        }
    }

    public void addDebugText(String text) {
        if (text != null && text.length() > 0) {
            Logger(fragmentName, text);
            if (debug) {
                if (debugText != null) {
                    String temp = debugText.getText().toString();
                    if (temp.isEmpty() || temp.equals("")) debugText.setText(text);
                    else {
                        temp = temp + "\n";
                        temp = temp + text;
                        debugText.setText(temp);
                    }
                } else throw new RuntimeException(
                    "Layout should have a TextView defined with the ID \"debugText\"");
            }
        }
    }

    private void setErrorLayoutMessage(String message) {
        hideListView();

        RelativeLayout errorLayout = root.findViewById(R.id.errorLayout);
        if (errorLayout != null) {
            errorLayout.setVisibility(View.VISIBLE);
            TextView errorTextMessage = root.findViewById(R.id.errorTextMessage);
            errorTextMessage.setText(message);
        } else throw new RuntimeException(
            "Layout should have a RelativeLayout defined with the ID of errorLayout");
    }

    public void setMessage(String message) {
        RelativeLayout errorLayout = root.findViewById(R.id.errorLayout);
        if (errorLayout != null) {
            errorLayout.setVisibility(View.VISIBLE);

            ImageView errorImage = root.findViewById(R.id.errorImage);
            errorImage.setImageResource(R.drawable.empty);
            errorImage.setAlpha(0.5f);
            errorImage.setVisibility(View.VISIBLE);

            TextView errorTextWrong = root.findViewById(R.id.errorTextWrong);
            errorTextWrong.setVisibility(View.GONE);

            TextView errorTextMessage = root.findViewById(R.id.errorTextMessage);
            errorTextMessage.setText(message);
        } else throw new RuntimeException(
            "Layout should have a RelativeLayout defined with the ID of errorLayout");
    }

    private void hideListView() {
        if (gridView != null) {
            gridView.setVisibility(View.GONE);
        } else throw new RuntimeException(
            "Layout should have a ListView defined with the ID of listView");
    }

    private void showDebugLayout() {
        try {
            if (root != null) {
                LinearLayout debugLayout = root.findViewById(R.id.debugLayout);
                if (debugLayout != null) {
                    debugLayout.setVisibility(View.VISIBLE);

                    debugText = root.findViewById(R.id.debugText);
                    if (debugText != null) {
                        debugText.setMovementMethod(new ScrollingMovementMethod());
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void Logger(String tag, String text) {
        if (!UsefulBits.isEmpty(tag) && !UsefulBits.isEmpty(text))
            Log.d(tag, text);
    }

    public void Filter(String text) {
    }

    public void refreshFragment() {
    }
}