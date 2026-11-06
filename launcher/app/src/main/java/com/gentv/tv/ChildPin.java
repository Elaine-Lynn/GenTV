package com.gentv.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.Base64;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.Gravity;
import android.view.ViewGroup;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class ChildPin {
    private static final String SALT = "pin_salt";
    private static final String HASH = "pin_hash";

    private ChildPin() {}

    static boolean configured(SharedPreferences state) {
        return state.contains(HASH) && state.contains(SALT);
    }

    static AlertDialog ask(Activity activity, SharedPreferences state, boolean create, Runnable accepted, Runnable cancelled) {
        EditText input = new PinInput(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint(create ? R.string.child_lock_new_pin : R.string.child_lock_enter_pin);
        EditText confirmation = new PinInput(activity);
        confirmation.setSingleLine(true);
        confirmation.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        confirmation.setHint(R.string.child_lock_confirm_pin);
        AlertDialog dialog = new PinDialog(activity, create, input, confirmation);
        dialog.setOnCancelListener(d -> cancelled.run());
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = input.getText().toString();
            if (!value.matches("[0-9]{4,8}")) {
                input.setError(activity.getString(R.string.child_lock_pin_length));
                return;
            }
            if (create && !value.equals(confirmation.getText().toString())) {
                confirmation.setError(activity.getString(R.string.child_lock_pin_mismatch));
                return;
            }
            try {
                if (create) {
                    byte[] salt = new byte[16];
                    new SecureRandom().nextBytes(salt);
                    if (!state.edit().putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                            .putString(HASH, Base64.encodeToString(hash(value, salt), Base64.NO_WRAP)).commit()) {
                        input.setError(activity.getString(R.string.not_available));
                        return;
                    }
                } else {
                    byte[] salt = Base64.decode(state.getString(SALT, ""), Base64.NO_WRAP);
                    byte[] expected = Base64.decode(state.getString(HASH, ""), Base64.NO_WRAP);
                    if (!MessageDigest.isEqual(expected, hash(value, salt))) {
                        input.setError(activity.getString(R.string.child_lock_pin_incorrect));
                        return;
                    }
                }
            } catch (GeneralSecurityException | IllegalArgumentException error) {
                input.setError(activity.getString(R.string.not_available));
                return;
            }
            dialog.dismiss();
            accepted.run();
        }));
        input.requestFocus();
        dialog.show();
        return dialog;
    }

    private static final class PinInput extends EditText {
        private Runnable openKeyboard;

        PinInput(Activity activity) {
            super(activity);
        }

        @Override public boolean onKeyPreIme(int code, KeyEvent event) {
            // Android TV IMEs can keep intercepting DPAD after hiding. Reopen before
            // a hidden keyboard consumes OK as a digit.
            if (android.os.Build.VERSION.SDK_INT >= 30 && code == KeyEvent.KEYCODE_DPAD_CENTER
                    && openKeyboard != null) {
                android.view.WindowInsets insets = getRootWindowInsets();
                if (insets != null && !insets.isVisible(android.view.WindowInsets.Type.ime())) {
                    if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) openKeyboard.run();
                    return true;
                }
            }
            return super.onKeyPreIme(code, event);
        }
    }

    private static final class PinDialog extends AlertDialog {
        private final Activity activity;
        private final boolean create;
        private final EditText input;
        private final EditText confirmation;
        private Button positive;
        private Button negative;
        private boolean keyboardRequested;
        private EditText pendingEditor;

        PinDialog(Activity activity, boolean create, EditText input, EditText confirmation) {
            super(activity, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
            this.activity = activity;
            this.create = create;
            this.input = input;
            this.confirmation = confirmation;
        }

        @Override protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            LinearLayout shell = ChildStyle.page(activity);
            ChildStyle.header(shell);
            ScrollView scroll = new ScrollView(activity);
            scroll.setFillViewport(true);
            shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            LinearLayout centered = new LinearLayout(activity);
            centered.setOrientation(LinearLayout.VERTICAL);
            centered.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            centered.setPadding(0, ChildStyle.dp(activity, 35), 0, ChildStyle.dp(activity, 14));
            scroll.addView(centered);
            LinearLayout form = new LinearLayout(activity);
            form.setOrientation(LinearLayout.VERTICAL);
            int width = Math.min(ChildStyle.dp(activity, 380),
                    activity.getResources().getDisplayMetrics().widthPixels - ChildStyle.dp(activity, 96));
            centered.addView(form, new LinearLayout.LayoutParams(width, -2));
            TextView eyebrow = ChildStyle.text(activity, activity.getString(R.string.child_lock_parent),
                    11, ChildStyle.FOCUS, true);
            form.addView(eyebrow);
            TextView heading = ChildStyle.text(activity, activity.getString(
                    create ? R.string.child_lock_pin_setup_title : R.string.child_lock_enter_pin),
                    28, ChildStyle.INK, true);
            form.addView(heading);
            TextView hint = ChildStyle.text(activity, activity.getString(
                    create ? R.string.child_lock_pin_setup_hint : R.string.child_lock_pin_verify_hint),
                    12, ChildStyle.MUTED, false);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
            hintParams.topMargin = ChildStyle.dp(activity, 6);
            hintParams.bottomMargin = ChildStyle.dp(activity, 12);
            form.addView(hint, hintParams);
            field(form, input, create ? R.string.child_lock_new_pin : R.string.child_lock_enter_pin);
            if (create) field(form, confirmation, R.string.child_lock_confirm_pin);
            positive = ChildStyle.button(activity, activity.getString(
                    create ? R.string.child_lock_pin_save : R.string.child_lock_pin_verify), true);
            LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(-1, ChildStyle.dp(activity, 43));
            submitParams.topMargin = ChildStyle.dp(activity, 14);
            form.addView(positive, submitParams);
            negative = ChildStyle.button(activity, activity.getString(android.R.string.cancel), false);
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, ChildStyle.dp(activity, 36));
            cancelParams.topMargin = ChildStyle.dp(activity, 7);
            form.addView(negative, cancelParams);
            negative.setOnClickListener(view -> cancel());
            TextView footer = ChildStyle.text(activity, activity.getString(R.string.child_lock_remote_hint),
                    11, ChildStyle.MUTED, false);
            footer.setGravity(Gravity.END);
            shell.addView(footer);
            setContentView(shell);
            getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(ChildStyle.PAPER));
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            getWindow().setDimAmount(0);
            // AlertDialog's empty framework content is installed before our custom page.
            // Clear its editor-blocking flag after adding the real EditTexts.
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            input.requestFocus();
        }

        @Override public void onWindowFocusChanged(boolean hasFocus) {
            super.onWindowFocusChanged(hasFocus);
            if (!hasFocus) return;
            if (!keyboardRequested) {
                keyboardRequested = true;
                showKeyboard(input);
            } else {
                showPendingKeyboard();
            }
        }

        private void showKeyboard(EditText field) {
            field.requestFocus();
            pendingEditor = field;
            field.post(this::showPendingKeyboard);
        }

        private void showPendingKeyboard() {
            EditText field = pendingEditor;
            if (field == null || !isShowing() || !field.hasWindowFocus()) return;
            InputMethodManager keyboard = activity.getSystemService(InputMethodManager.class);
            if (keyboard != null) {
                boolean requested = keyboard.showSoftInput(field, 0);
                if (requested) pendingEditor = null;
            }
        }

        private void field(LinearLayout form, EditText field, int label) {
            TextView title = ChildStyle.text(activity, activity.getString(label), 12, ChildStyle.INK, true);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
            titleParams.topMargin = ChildStyle.dp(activity, 7);
            titleParams.bottomMargin = ChildStyle.dp(activity, 5);
            form.addView(title, titleParams);
            field.setHint("");
            field.setImeOptions((create && field == input ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE)
                    | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            field.setOnClickListener(view -> showKeyboard(field));
            if (field instanceof PinInput pin) pin.openKeyboard = () -> showKeyboard(field);
            field.setOnKeyListener((view, code, event) -> {
                if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER
                        && code != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) showKeyboard(field);
                return true;
            });
            field.setOnEditorActionListener((view, action, event) -> {
                if (action == EditorInfo.IME_ACTION_NEXT && create && field == input) {
                    showKeyboard(confirmation);
                    return true;
                }
                if (action == EditorInfo.IME_ACTION_DONE) {
                    positive.performClick();
                    return true;
                }
                return false;
            });
            field.setTextColor(ChildStyle.INK);
            field.setTextSize(22);
            field.setPadding(ChildStyle.dp(activity, 12), 0, ChildStyle.dp(activity, 12), 0);
            android.graphics.drawable.StateListDrawable background = new android.graphics.drawable.StateListDrawable();
            background.addState(new int[]{android.R.attr.state_focused},
                    ChildStyle.raised(activity, 0xffffffff, ChildStyle.FOCUS, 10));
            background.addState(new int[0], ChildStyle.raised(activity, 0xffffffff, ChildStyle.LINE, 10));
            field.setBackground(background);
            form.addView(field, new LinearLayout.LayoutParams(-1, ChildStyle.dp(activity, 42)));
        }

        @Override public Button getButton(int which) {
            if (which == BUTTON_POSITIVE) return positive;
            if (which == BUTTON_NEGATIVE) return negative;
            return null;
        }
    }

    private static byte[] hash(String value, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec key = new PBEKeySpec(value.toCharArray(), salt, 60000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(key).getEncoded();
        } finally {
            key.clearPassword();
        }
    }
}
