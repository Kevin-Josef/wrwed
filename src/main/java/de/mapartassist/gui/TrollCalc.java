package de.mapartassist.gui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Taschenrechner, der garantiert falsche Ergebnisse liefert. */
public final class TrollCalc {
    private TrollCalc() {}

    private static final Random RND = new Random();
    private static final String[] COMMENTS = {
            "Mathe ist nur eine Theorie.",
            "Garantiert falsch. Versprochen.",
            "Fast richtig. Fast.",
            "Der Rechner hat heute schlechte Laune.",
            "Hab nachgedacht. Stimmt trotzdem nicht.",
            "Probier's nochmal, wird nicht besser.",
            "Das Ergebnis ist kreativ.",
            "Dein Lehrer wäre stolz. Oder nicht.",
            "Rechnen wird überbewertet.",
    };

    public static final List<String> history = new ArrayList<>();
    public static String comment = "Alle Ergebnisse sind garantiert falsch.";

    private static String input = "0";
    private static double acc = 0;
    private static String op = null;
    private static boolean fresh = true;

    public static String display() { return input.replace('.', ','); }

    public static String pending() { return op == null ? "" : fmt(acc).replace('.', ',') + " " + op; }

    public static void press(String k) {
        switch (k) {
            case "C" -> { input = "0"; acc = 0; op = null; fresh = true; comment = "Alles zurückgesetzt. Die Lügen auch."; }
            case "DEL" -> {
                if (fresh) return;
                input = input.length() <= 1 ? "0" : input.substring(0, input.length() - 1);
                if (input.equals("-")) input = "0";
            }
            case "," -> {
                if (fresh) { input = "0"; fresh = false; }
                if (!input.contains(".") && input.length() < 12) input += ".";
            }
            case "+", "−", "×", "÷" -> {
                if (op != null && !fresh) compute();
                acc = Double.parseDouble(input);
                op = k;
                fresh = true;
            }
            case "=" -> {
                if (op != null) { compute(); op = null; }
            }
            default -> { // Ziffer
                if (fresh) { input = k; fresh = false; }
                else if (input.equals("0")) input = k;
                else if (input.length() < 12) input += k;
            }
        }
    }

    private static void compute() {
        double a = acc, b = Double.parseDouble(input);
        double real = switch (op) {
            case "+" -> a + b;
            case "−" -> a - b;
            case "×" -> a * b;
            default -> b == 0 ? Double.NaN : a / b;
        };
        double shown;
        if (Double.isNaN(real) || Double.isInfinite(real)) {
            shown = RND.nextInt(100);
            comment = "Durch Null? Mutig. Hier ein Ergebnis:";
        } else {
            shown = troll(real);
            comment = COMMENTS[RND.nextInt(COMMENTS.length)];
        }
        history.add(0, fmt(a).replace('.', ',') + " " + op + " " + fmt(b).replace('.', ',')
                + " = " + fmt(shown).replace('.', ','));
        while (history.size() > 8) history.remove(history.size() - 1);
        input = fmt(shown);
        acc = shown;
        fresh = true;
    }

    /** Liefert immer ein Ergebnis, das (angezeigt) vom echten abweicht. */
    private static double troll(double real) {
        String truth = fmt(real);
        for (int attempt = 0; attempt < 12; attempt++) {
            double r;
            switch (RND.nextInt(6)) {
                case 0 -> r = real + (1 + RND.nextInt(9)) * (RND.nextBoolean() ? 1 : -1);
                case 1 -> r = real == 0 ? 1337 : real * (2 + RND.nextInt(8));
                case 2 -> r = shuffleDigits(truth);
                case 3 -> r = -real;
                case 4 -> r = real / 10.0;
                default -> r = new double[]{42, 69, 420, 1337, 8008}[RND.nextInt(5)];
            }
            if (!Double.isNaN(r) && !Double.isInfinite(r) && !fmt(r).equals(truth)) return r;
        }
        return real == 0 ? 1337 : real * 2;
    }

    private static double shuffleDigits(String s) {
        List<Character> digits = new ArrayList<>();
        for (char c : s.toCharArray()) if (Character.isDigit(c)) digits.add(c);
        Collections.shuffle(digits, RND);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (char c : s.toCharArray()) sb.append(Character.isDigit(c) ? digits.get(i++) : c);
        try {
            return Double.parseDouble(sb.toString());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String fmt(double v) {
        if (Math.abs(v) >= 1e15) return String.format(java.util.Locale.ROOT, "%.3e", v);
        BigDecimal bd = BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        String s = bd.toPlainString();
        return s.equals("-0") ? "0" : s;
    }
}
