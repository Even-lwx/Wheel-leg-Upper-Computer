package com.microsoft.cognitiveservices.speech.samples.sdkdemo;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class PlaceholderFragment extends Fragment {

    private FloatingActionButton btnPersonalitySwitch;
    private ImageView welcomeGif;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_placeholder, container, false);

        // 初始化视图
        welcomeGif = view.findViewById(R.id.welcome_gif);
        btnPersonalitySwitch = view.findViewById(R.id.btn_personality_switch);

        // 使用Glide加载GIF动画
        Glide.with(this)
                .asGif()
                .load(R.drawable.welcome_animation)
                .into(welcomeGif);

        // 设置人格切换按钮点击事件
        btnPersonalitySwitch.setOnClickListener(v -> showPersonalitySelector());

        return view;
    }

    /**
     * 显示人格选择对话框
     */
    private void showPersonalitySelector() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_personality_selector, null);

        RadioButton radioProfessional = dialogView.findViewById(R.id.radioProfessional);
        RadioButton radioLively = dialogView.findViewById(R.id.radioLively);
        RadioButton radioGentle = dialogView.findViewById(R.id.radioGentle);
        RadioButton radioConcise = dialogView.findViewById(R.id.radioConcise);
        RadioButton radioCustom = dialogView.findViewById(R.id.radioCustom);
        Button btnEditCustom = dialogView.findViewById(R.id.btnEditCustomPrompt);
        Button btnConfirm = dialogView.findViewById(R.id.btnConfirm);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);

        // 获取当前选中的人格ID并设置选中状态
        int currentId = VoicePersonality.getSelectedPersonalityId(requireContext());
        switch (currentId) {
            case VoicePersonality.PERSONALITY_PROFESSIONAL:
                radioProfessional.setChecked(true);
                break;
            case VoicePersonality.PERSONALITY_LIVELY:
                radioLively.setChecked(true);
                break;
            case VoicePersonality.PERSONALITY_GENTLE:
                radioGentle.setChecked(true);
                break;
            case VoicePersonality.PERSONALITY_CONCISE:
                radioConcise.setChecked(true);
                break;
            case VoicePersonality.PERSONALITY_CUSTOM:
                radioCustom.setChecked(true);
                break;
        }

        // 自定义人格编辑按钮
        btnEditCustom.setOnClickListener(v -> {
            dialog.dismiss();
            showCustomPersonalityDialog();
        });

        // 取消按钮
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // 确认按钮
        btnConfirm.setOnClickListener(v -> {
            int selectedPersonalityId = VoicePersonality.PERSONALITY_PROFESSIONAL; // 默认值
            
            if (radioProfessional.isChecked()) {
                selectedPersonalityId = VoicePersonality.PERSONALITY_PROFESSIONAL;
            } else if (radioLively.isChecked()) {
                selectedPersonalityId = VoicePersonality.PERSONALITY_LIVELY;
            } else if (radioGentle.isChecked()) {
                selectedPersonalityId = VoicePersonality.PERSONALITY_GENTLE;
            } else if (radioConcise.isChecked()) {
                selectedPersonalityId = VoicePersonality.PERSONALITY_CONCISE;
            } else if (radioCustom.isChecked()) {
                selectedPersonalityId = VoicePersonality.PERSONALITY_CUSTOM;
            }

            VoicePersonality.saveSelectedPersonality(requireContext(), selectedPersonalityId);
            
            String personalityName = VoicePersonality.getPersonalityById(selectedPersonalityId).name;
            Toast.makeText(requireContext(), 
                    "已切换到: " + personalityName, 
                    Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.setContentView(dialogView);
        dialog.show();
    }

    /**
     * 显示自定义人格对话框
     */
    private void showCustomPersonalityDialog() {
        Dialog dialog = new Dialog(requireContext());
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_custom_personality, null);

        EditText etSystemPrompt = dialogView.findViewById(R.id.et_custom_system_prompt);
        EditText etTemperature = dialogView.findViewById(R.id.et_custom_temperature);
        Button btnSave = dialogView.findViewById(R.id.btn_save_custom);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_custom);

        // 加载之前保存的自定义设置
        String savedPrompt = VoicePersonality.getCustomPrompt(requireContext());
        etSystemPrompt.setText(savedPrompt);
        etTemperature.setText("0.4");

        // 保存按钮
        btnSave.setOnClickListener(v -> {
            String customPrompt = etSystemPrompt.getText().toString().trim();

            if (customPrompt.isEmpty()) {
                Toast.makeText(requireContext(), 
                        "请输入系统提示词", 
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // 保存自定义人格
            VoicePersonality.saveCustomPrompt(requireContext(), customPrompt);
            VoicePersonality.saveSelectedPersonality(requireContext(), VoicePersonality.PERSONALITY_CUSTOM);

            Toast.makeText(requireContext(), 
                    "已切换到: 自定义人格", 
                    Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        // 取消按钮
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(dialogView);
        dialog.show();
    }
}
