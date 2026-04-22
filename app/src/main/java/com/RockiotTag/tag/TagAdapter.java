package com.RockiotTag.tag;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class TagAdapter extends ArrayAdapter<String> {

    private Context context;
    private List<String> tags;
    private List<String> icons;

    public TagAdapter(Context context, List<String> tags, List<String> icons) {
        super(context, android.R.layout.simple_spinner_item, tags);
        this.context = context;
        this.tags = tags;
        this.icons = icons;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return createView(position, convertView, parent, false);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return createView(position, convertView, parent, true);
    }

    private View createView(int position, View convertView, ViewGroup parent, boolean isDropDown) {
        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
        }

        TextView textView = view.findViewById(android.R.id.text1);
        
        if (position >= 0 && position < tags.size()) {
            String tag = tags.get(position);
            String icon = (position < icons.size()) ? icons.get(position) : "";
            
            if (position == 0) {
                textView.setText(tag);
            } else {
                textView.setText(icon + " " + tag);
            }
        }

        if (isDropDown) {
            textView.setPadding(32, 24, 32, 24);
        }

        return view;
    }
}
