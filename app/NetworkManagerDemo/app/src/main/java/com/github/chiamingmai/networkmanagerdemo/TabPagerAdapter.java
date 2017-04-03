package com.github.chiamingmai.networkmanagerdemo;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import java.util.ArrayList;


public class TabPagerAdapter extends FragmentPagerAdapter {
    ArrayList<MyFragment> fragList;

    public TabPagerAdapter(FragmentManager fm) {
        super(fm);
        fragList = new ArrayList<MyFragment>();
    }

    public void addItem(MyFragment frag, String title) {
        frag.setTitle(title);
        fragList.add(frag);
    }

    @Override
    public int getCount() {
        return fragList.size();
    }

    @Override
    public Fragment getItem(int position) {
        if (position < 0 || fragList == null || fragList.size() == 0)
            return new MyFragment();
        return fragList.get(position);
    }

    @Override
    public CharSequence getPageTitle(int position) {
        if (position < 0 || fragList == null || fragList.size() == 0)
            return null;
        return fragList.get(position).getTitle();
    }
}
