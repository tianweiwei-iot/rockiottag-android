package com.RockiotTag.tag.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

/**
 * TrackViewModel的工厂类
 */
public class TrackViewModelFactory implements ViewModelProvider.Factory {
    
    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TrackViewModel.class)) {
            return (T) new TrackViewModel();
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
