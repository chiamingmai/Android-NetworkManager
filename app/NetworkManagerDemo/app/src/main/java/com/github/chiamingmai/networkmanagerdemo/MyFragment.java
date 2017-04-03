package com.github.chiamingmai.networkmanagerdemo;

import android.support.v4.app.Fragment;


public class MyFragment extends Fragment {
    String title = "";

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
