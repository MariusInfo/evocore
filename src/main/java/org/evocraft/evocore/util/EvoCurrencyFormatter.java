package org.evocraft.evocore.util;

import java.util.Locale;

public final class EvoCurrencyFormatter {
    public static final String CURRENCY_NAME = "Evo";

    private EvoCurrencyFormatter() {
    }

    public static String format(double amount) {
        if (!Double.isFinite(amount)) return "0";

        double abs = Math.abs(amount);
        String sign = amount < 0.0D ? "-" : "";

        if (abs >= 1_000_000_000_000.0D) return sign + trim(abs / 1_000_000_000_000.0D) + "T";
        if (abs >= 1_000_000_000.0D) return sign + trim(abs / 1_000_000_000.0D) + "B";
        if (abs >= 1_000_000.0D) return sign + trim(abs / 1_000_000.0D) + "M";
        if (abs >= 1_000.0D) return sign + trim(abs / 1_000.0D) + "K";

        if (abs == Math.rint(abs)) return sign + String.format(Locale.US, "%.0f", abs);
        if (abs < 10.0D) return sign + trim(abs);
        return sign + String.format(Locale.US, "%.1f", abs);
    }

    public static String formatWithCurrency(double amount) {
        return format(amount) + " " + CURRENCY_NAME;
    }

    private static String trim(double value) {
        String formatted;
        if (value >= 100.0D) {
            formatted = String.format(Locale.US, "%.0f", value);
        } else if (value >= 10.0D) {
            formatted = String.format(Locale.US, "%.1f", value);
        } else {
            formatted = String.format(Locale.US, "%.2f", value);
        }

        while (formatted.contains(".") && formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }
}
