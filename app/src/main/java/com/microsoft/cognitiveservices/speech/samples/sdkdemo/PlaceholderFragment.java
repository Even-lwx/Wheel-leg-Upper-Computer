package com.microsoft.cognitiveservices.speech.samples.sdkdemo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;

public class PlaceholderFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_placeholder, container, false);

        // 使用Glide加载GIF动画
        ImageView welcomeGif = view.findViewById(R.id.welcome_gif);
        Glide.with(this)
                .asGif()
                .load(R.drawable.welcome_animation)
                .into(welcomeGif);

        return view;
    }
}
