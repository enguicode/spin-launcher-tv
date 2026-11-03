package com.enguicode.spinlaunchertv;

/** Pure rules for the time-of-day background and the weather line, kept apart so they can be unit tested. */
final class Sky {
    private Sky() { }

    static final int MOUNTAINS = 0, MEDITERRANEAN = 1, FOREST = 2, DUNES = 3, AURORA = 4;

    /** Background for the local hour (0-23): mountains 6-9, sea 9-14, forest 14-18, dunes 18-21, aurora 21-6. */
    static int period(int hour) {
        if (hour >= 6 && hour < 9) return MOUNTAINS;
        if (hour >= 9 && hour < 14) return MEDITERRANEAN;
        if (hour >= 14 && hour < 18) return FOREST;
        if (hour >= 18 && hour < 21) return DUNES;
        return AURORA;
    }

    /** Spanish label for an Open-Meteo WMO weather code. */
    static String label(int code) {
        if (code == 0) return "Despejado";
        if (code <= 2) return "Algunas nubes";
        if (code == 3) return "Nublado";
        if (code == 45 || code == 48) return "Niebla";
        if (code >= 51 && code <= 57) return "Llovizna";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "Lluvia";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Nieve";
        if (code >= 95) return "Tormenta";
        return "";
    }

    static final int ICON_CLEAR = 0, ICON_CLOUD = 1, ICON_RAIN = 2, ICON_STORM = 3;

    static int icon(int code) {
        if (code <= 1) return ICON_CLEAR;
        if (code >= 95) return ICON_STORM;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return ICON_RAIN;
        return ICON_CLOUD;
    }

    /** "21,4°" from a Home Assistant state such as "21.43"; null when the state is not a number. */
    static String degrees(String state) {
        try {
            return String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f°", Double.parseDouble(state));
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }
}
