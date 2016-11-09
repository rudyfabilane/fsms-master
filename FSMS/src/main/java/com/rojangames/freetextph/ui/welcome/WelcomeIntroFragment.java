package com.rojangames.freetextph.ui.welcome;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.rojangames.freetextph.R;
import com.rojangames.freetextph.ui.view.QKTextView;

public class WelcomeIntroFragment extends BaseWelcomeFragment implements View.OnClickListener {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_welcome_greeting, container, false);

        QKTextView start = (QKTextView) view.findViewById(R.id.welcome_start);
        start.setOnClickListener(this);

        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.welcome_start:
                mPager.setCurrentItem(1);
                break;
        }
    }
}
