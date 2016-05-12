package com.quickblox.q_municate.utils;

import android.graphics.Color;

import com.quickblox.q_municate_db.models.User;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ColorUtils {

    private static final int COLOR_MAX_VALUE = 255;
    private static final float COLOR_ALPHA = 0.8f;
    private static Map<Integer, Integer> colorsMap = new HashMap<>();

    private Random random;

    public ColorUtils() {
        random = new Random();
    }

    public int getRandomTextColorById(Integer senderId) {
        if (colorsMap.get(senderId) != null) {
            return colorsMap.get(senderId);
        } else {
            int colorValue = getRandomColor();
            colorsMap.put(senderId, colorValue);
            return colorsMap.get(senderId);
        }
    }

    public int getColorByUser(User user) {
        if (colorsMap.get(user.getUserId()) != null) {
            return colorsMap.get(user.getUserId());
        } else {
            int colorValue = Color.parseColor("#000000");
             if (user.getSubscription() == "premium") {
                 colorValue = Color.parseColor("#34B809");

            } else if (user.getSubscription() == "vip") {
                colorValue = Color.parseColor("#34B809");
            }
            colorsMap.put(user.getUserId(), colorValue);
            return colorsMap.get(user.getUserId());
        }
    }

    public int getRandomColor() {
        float[] hsv = new float[3];
        int color = Color.argb(COLOR_MAX_VALUE, random.nextInt(COLOR_MAX_VALUE), random.nextInt(
                COLOR_MAX_VALUE), random.nextInt(COLOR_MAX_VALUE));
        Color.colorToHSV(color, hsv);
        hsv[2] *= COLOR_ALPHA;
        color = Color.HSVToColor(hsv);
        return color;
    }
}