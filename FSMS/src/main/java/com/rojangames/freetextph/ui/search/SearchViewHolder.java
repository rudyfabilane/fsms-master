package com.rojangames.freetextph.ui.search;

import android.view.View;
import com.rojangames.freetextph.R;
import com.rojangames.freetextph.ui.base.ClickyViewHolder;
import com.rojangames.freetextph.ui.base.QKActivity;
import com.rojangames.freetextph.ui.view.AvatarView;
import com.rojangames.freetextph.ui.view.QKTextView;

public class SearchViewHolder extends ClickyViewHolder<SearchData> {

    protected View root;
    protected AvatarView avatar;
    protected QKTextView name;
    protected QKTextView date;
    protected QKTextView snippet;

    public SearchViewHolder(QKActivity context, View view) {
        super(context, view);

        root = view;
        avatar = (AvatarView) view.findViewById(R.id.search_avatar);
        name = (QKTextView) view.findViewById(R.id.search_name);
        date = (QKTextView) view.findViewById(R.id.search_date);
        snippet = (QKTextView) view.findViewById(R.id.search_snippet);
    }
}
