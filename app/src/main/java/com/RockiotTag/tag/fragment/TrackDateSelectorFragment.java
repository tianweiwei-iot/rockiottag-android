package com.RockiotTag.tag.fragment;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.RockiotTag.tag.R;

import java.util.Calendar;

/**
 * 轨迹日期选择 Fragment
 * 职责：管理日期选择和显示
 */
public class TrackDateSelectorFragment extends Fragment {
    private static final String TAG = "TrackDateSelectorFragment";
    
    private TextView dateDisplayText;
    private Button prevDayBtn;
    private Button nextDayBtn;
    
    public interface OnDateChangeListener {
        void onDateChanged(Calendar newDate);
    }
    
    private OnDateChangeListener listener;
    private Calendar selectedDate = Calendar.getInstance();
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_track_date_selector, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        updateDateDisplay();
    }
    
    private void initViews(View view) {
        dateDisplayText = view.findViewById(R.id.date_display_text);
        prevDayBtn = view.findViewById(R.id.prev_day_btn);
        nextDayBtn = view.findViewById(R.id.next_day_btn);
        
        dateDisplayText.setOnClickListener(v -> showDatePicker());
        prevDayBtn.setOnClickListener(v -> changeDay(-1));
        nextDayBtn.setOnClickListener(v -> changeDay(1));
    }
    
    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            requireContext(),
            (view, year, month, dayOfMonth) -> {
                selectedDate.set(Calendar.YEAR, year);
                selectedDate.set(Calendar.MONTH, month);
                selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                updateDateDisplay();
                
                if (listener != null) {
                    listener.onDateChanged(selectedDate);
                }
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }
    
    private void changeDay(int delta) {
        selectedDate.add(Calendar.DAY_OF_MONTH, delta);
        updateDateDisplay();
        
        if (listener != null) {
            listener.onDateChanged(selectedDate);
        }
    }
    
    private void updateDateDisplay() {
        if (dateDisplayText != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                "yyyy-MM-dd", java.util.Locale.getDefault());
            dateDisplayText.setText(sdf.format(selectedDate.getTime()));
        }
    }
    
    public void setOnDateChangeListener(OnDateChangeListener listener) {
        this.listener = listener;
    }
    
    public Calendar getSelectedDate() {
        return selectedDate;
    }
    
    public void setSelectedDate(Calendar date) {
        this.selectedDate = (Calendar) date.clone();
        updateDateDisplay();
    }
}
