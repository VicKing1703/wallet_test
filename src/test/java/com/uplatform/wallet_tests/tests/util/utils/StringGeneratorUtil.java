package com.uplatform.wallet_tests.tests.util.utils;

import lombok.experimental.UtilityClass;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.Collectors;

@UtilityClass
public final class StringGeneratorUtil {

    public enum GeneratorType {
        INTEGER,
        LETTERS,
        ALPHANUMERIC,
        NUMBER,
        NAME,
        EMAIL,
        PASSWORD,
        BIRTHDAY_DDMMYYYY,
        BIRTHDAY_YYYYMMDD,
        CYRILLIC,
        SPECIAL,
        HEX,
        NON_HEX,
        IBAN,
        PERSONAL_ID,
        ALIAS,
        TITLE,
        GAME_TITLE,
        PHONE
    }

    public static final String DIGITS = "0123456789";
    public static final String NON_ZERO_DIGITS = "123456789";
    public static final String LATIN_LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    public static final String LATIN_UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String LATIN_LETTERS = LATIN_LOWERCASE + LATIN_UPPERCASE;
    public static final String ALPHANUMERIC_CHARS = LATIN_LETTERS + DIGITS;

    public static final String CYRILLIC_LOWERCASE = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя";
    public static final String CYRILLIC_UPPERCASE = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ";
    public static final String CYRILLIC_LETTERS = CYRILLIC_LOWERCASE + CYRILLIC_UPPERCASE;

    public static final String CONSONANTS = "bcdfghjklmnpqrstvwxz";
    public static final String VOWELS = "aeiouy";
    public static final String GENERAL_SPECIAL_CHARS = "!@#$%^&_";
    public static final String PASSWORD_SPECIAL_CHARS = "!:@#$%";
    public static final String HEX_CHARS = "0123456789abcdef";
    public static final String NON_HEX_CHARS = "ghijklmnopqrstuvwxyz";
    public static final String ALIAS_CHARS = LATIN_LOWERCASE + DIGITS + "-";
    public static final String TITLE_CHARS = ALPHANUMERIC_CHARS + CYRILLIC_LETTERS + " -";
    public static final String GAME_TITLE_CHARS = ALPHANUMERIC_CHARS + " -";

    private static final int DEFAULT_LENGTH = 10;
    private static final int MAX_ALIAS_LEN = 100;
    private static final int MAX_TITLE_LEN = 25;
    private static final int MAX_GAME_TITLE_LEN = 255;
    private static final int MIN_GAME_TITLE_LEN = 2;
    private static final int MIN_PASSWORD_LEN = 4;
    private static final int DEFAULT_AMOUNT_SCALE = 2;

    private static volatile RandomGenerator RNG =
            RandomGeneratorFactory.getDefault().create();

    public static void useDeterministicSeed(long seed) {
        RNG = RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    public static void useRandomGenerator(RandomGenerator generator) {
        if (generator == null) throw new IllegalArgumentException("generator must not be null");
        RNG = generator;
    }

    public static String get(GeneratorType type, int... lengths) {
        int length = (lengths.length > 0 && lengths[0] > 0) ? lengths[0] : DEFAULT_LENGTH;
        Integer yearsAgoOrNull = (lengths.length > 0) ? lengths[0] : null;
        return switch (type) {
            case INTEGER -> randomStringFromSet(DIGITS, length);
            case LETTERS -> randomStringFromSet(LATIN_LETTERS, length);
            case ALPHANUMERIC -> randomStringFromSet(ALPHANUMERIC_CHARS, length);
            case NUMBER -> generateNumber(length);
            case NAME -> generateName(length);
            case EMAIL -> randomStringFromSet(ALPHANUMERIC_CHARS, length) + "@generated.com";
            case PASSWORD -> generatePassword(Math.max(length, MIN_PASSWORD_LEN));
            case BIRTHDAY_DDMMYYYY -> generateBirthday(yearsAgoOrNull, "dd.MM.yyyy");
            case BIRTHDAY_YYYYMMDD -> generateBirthday(yearsAgoOrNull, "yyyyMMdd");
            case CYRILLIC -> randomStringFromSet(CYRILLIC_LETTERS, length);
            case SPECIAL -> randomStringFromSet(GENERAL_SPECIAL_CHARS, length);
            case HEX -> randomStringFromSet(HEX_CHARS, length);
            case NON_HEX -> randomStringFromSet(NON_HEX_CHARS, length);
            case IBAN -> generateIbanLV();
            case PERSONAL_ID -> generatePersonalIdLV();
            case ALIAS -> generateAlias(length);
            case TITLE -> generateStandardTitle(length);
            case GAME_TITLE -> generateGameTitle(length);
            case PHONE -> generateTelephoneNumberLV();
        };
    }

    private static String randomStringFromSet(String charset, int length) {
        if (length <= 0 || charset == null || charset.isEmpty()) {
            return "";
        }
        char[] result = new char[length];
        int n = charset.length();
        for (int i = 0; i < length; i++) {
            result[i] = charset.charAt(RNG.nextInt(n));
        }
        return new String(result);
    }

    private static String generateNumber(int length) {
        if (length <= 0) return "";
        if (length == 1) return randomStringFromSet(DIGITS, 1);
        char first = NON_ZERO_DIGITS.charAt(RNG.nextInt(NON_ZERO_DIGITS.length()));
        String rest = randomStringFromSet(DIGITS, length - 1);
        return first + rest;
    }

    private static String generateName(int length) {
        if (length <= 0) return "";
        StringBuilder sb = new StringBuilder(length);
        char firstChar = CONSONANTS.charAt(RNG.nextInt(CONSONANTS.length()));
        sb.append(Character.toUpperCase(firstChar));
        for (int i = 1; i < length; i++) {
            if ((i % 2) != 0) {
                sb.append(VOWELS.charAt(RNG.nextInt(VOWELS.length())));
            } else {
                sb.append(CONSONANTS.charAt(RNG.nextInt(CONSONANTS.length())));
            }
        }
        return sb.toString();
    }

    private static String generatePassword(int length) {
        if (length < MIN_PASSWORD_LEN) {
            throw new IllegalArgumentException("Password length must be " + MIN_PASSWORD_LEN + " or more");
        }
        List<String> requiredCharSets = List.of(
                DIGITS,
                LATIN_UPPERCASE,
                LATIN_LOWERCASE,
                PASSWORD_SPECIAL_CHARS
        );
        List<Character> result = new ArrayList<>(length);
        for (String set : requiredCharSets) {
            if (set == null || set.isEmpty()) {
                throw new IllegalStateException("Password generation error: a required character set is empty");
            }
            result.add(set.charAt(RNG.nextInt(set.length())));
        }
        String allChars = ALPHANUMERIC_CHARS + PASSWORD_SPECIAL_CHARS;
        for (int i = result.size(); i < length; i++) {
            result.add(allChars.charAt(RNG.nextInt(allChars.length())));
        }
        for (int i = result.size() - 1; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            Collections.swap(result, i, j);
        }
        StringBuilder sb = new StringBuilder(length);
        for (char c : result) sb.append(c);
        return sb.toString();
    }

    private static String generateBirthday(Integer yearsAgoOrNull, String pattern) {
        int yearsAgo = (yearsAgoOrNull == null || yearsAgoOrNull <= 0)
                ? (18 + RNG.nextInt(43))
                : yearsAgoOrNull;
        LocalDate now = LocalDate.now();
        int birthYear = now.getYear() - yearsAgo;
        int month = 1 + RNG.nextInt(12);
        YearMonth ym = YearMonth.of(birthYear, month);
        int day = 1 + RNG.nextInt(ym.lengthOfMonth());
        LocalDate birthDate = LocalDate.of(birthYear, month, day);
        return birthDate.format(DateTimeFormatter.ofPattern(pattern));
    }

    private static String generateIbanLV() {
        final String country = "LV";
        final String bank = randomStringFromSet(LATIN_UPPERCASE, 4);
        final String account = randomStringFromSet((LATIN_UPPERCASE + DIGITS), 13);
        String rearranged = (bank + account + country + "00");
        String numeric = rearranged.chars()
                .mapToObj(ch -> {
                    if (Character.isLetter(ch)) {
                        int v = Character.toUpperCase(ch) - 'A' + 10;
                        return Integer.toString(v);
                    } else {
                        return Character.toString((char) ch);
                    }
                })
                .collect(Collectors.joining());
        int mod = mod97(numeric);
        int check = 98 - mod;
        String checkDigits = (check < 10 ? "0" : "") + check;
        return country + checkDigits + bank + account;
    }

    private static int mod97(String s) {
        int rem = 0;
        for (int i = 0; i < s.length(); i++) {
            rem = (rem * 10 + (s.charAt(i) - '0')) % 97;
        }
        return rem;
    }

    private static String generatePersonalIdLV() {
        LocalDate date = randomDate(1950, 2005);
        String datePart = date.format(DateTimeFormatter.ofPattern("ddMMyy"));
        String serial = randomStringFromSet(DIGITS, 5);
        return datePart + "-" + serial;
    }

    private static LocalDate randomDate(int minYearInclusive, int maxYearInclusive) {
        int year = minYearInclusive + RNG.nextInt(maxYearInclusive - minYearInclusive + 1);
        int month = 1 + RNG.nextInt(12);
        YearMonth ym = YearMonth.of(year, month);
        int day = 1 + RNG.nextInt(ym.lengthOfMonth());
        return LocalDate.of(year, month, day);
    }

    private static String generateAlias(int length) {
        return generateDelimitedString(length, ALIAS_CHARS, "-", MAX_ALIAS_LEN);
    }

    private static String generateStandardTitle(int length) {
        return generateDelimitedString(length, TITLE_CHARS, " -", MAX_TITLE_LEN);
    }

    private static String generateGameTitle(int length) {
        int finalLength = Math.max(length, MIN_GAME_TITLE_LEN);
        return generateDelimitedString(finalLength, GAME_TITLE_CHARS, " -", MAX_GAME_TITLE_LEN);
    }

    private static String generateDelimitedString(int length, String charSet, String boundaryChars, int maxLen) {
        if (length <= 0 || charSet == null || charSet.isEmpty()) {
            return "";
        }
        int finalLength = Math.min(Math.max(length, 1), maxLen);
        String allowedBoundary = boundaryChars.chars()
                .mapToObj(c -> String.valueOf((char) c))
                .filter(ch -> charSet.indexOf(ch) >= 0)
                .collect(Collectors.joining());
        String nonBoundary = charSet.chars()
                .filter(c -> allowedBoundary.indexOf(c) < 0)
                .mapToObj(c -> String.valueOf((char) c))
                .collect(Collectors.joining());
        if (nonBoundary.isEmpty()) {
            return randomStringFromSet(charSet, finalLength);
        }
        char[] res = new char[finalLength];
        res[0] = nonBoundary.charAt(RNG.nextInt(nonBoundary.length()));
        for (int i = 1; i < finalLength - 1; i++) {
            char c = charSet.charAt(RNG.nextInt(charSet.length()));
            if (!allowedBoundary.isEmpty() && allowedBoundary.indexOf(c) >= 0) {
                char prev = res[i - 1];
                if (allowedBoundary.indexOf(prev) >= 0) {
                    c = nonBoundary.charAt(RNG.nextInt(nonBoundary.length()));
                }
            }
            res[i] = c;
        }
        if (finalLength > 1) {
            res[finalLength - 1] = nonBoundary.charAt(RNG.nextInt(nonBoundary.length()));
        }
        return new String(res);
    }

    private static String generateTelephoneNumberLV() {
        return generateTelephoneNumber("+376", "2", 7);
    }

    private static String generateTelephoneNumber(String countryCode, String firstDigit, int restDigits) {
        return countryCode + firstDigit + randomStringFromSet(DIGITS, restDigits);
    }

    public static BigDecimal generateBigDecimalAmount(BigDecimal maxAmount) {
        return generateBigDecimalAmount(maxAmount, DEFAULT_AMOUNT_SCALE);
    }

    public static BigDecimal generateBigDecimalAmount(BigDecimal maxAmount, int scale) {
        if (maxAmount == null || maxAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Максимальная сумма (maxAmount) должна быть положительной и не null.");
        }
        if (scale < 0) {
            throw new IllegalArgumentException("Масштаб (scale) должен быть неотрицательным.");
        }
        BigDecimal smallestPossibleValue = BigDecimal.ONE.movePointLeft(scale);
        if (maxAmount.compareTo(smallestPossibleValue) < 0) {
            throw new IllegalArgumentException(String.format(
                    "maxAmount (%s) меньше, чем наименьшее возможное положительное значение с масштабом %d (%s).",
                    maxAmount.toPlainString(), scale, smallestPossibleValue.toPlainString()
            ));
        }
        BigDecimal scaledMaxAmount = maxAmount.movePointRight(scale).setScale(0, RoundingMode.DOWN);
        if (scaledMaxAmount.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            throw new ArithmeticException("maxAmount is too large to be represented as a long after scaling.");
        }
        long maxLong = scaledMaxAmount.longValue();
        if (maxLong < 1) {
            return smallestPossibleValue;
        }
        long randomLong = (maxLong == Long.MAX_VALUE)
                ? 1 + RNG.nextLong(Long.MAX_VALUE)
                : RNG.nextLong(1, maxLong + 1);
        return new BigDecimal(randomLong)
                .movePointLeft(scale)
                .setScale(scale, RoundingMode.HALF_UP);
    }

    public static String generateAliasCustom(int length, String charSet) {
        return generateDelimitedString(length, charSet, "-", MAX_ALIAS_LEN);
    }

    public static String generateTitleCustom(int length, String charSet) {
        return generateDelimitedString(length, charSet, " -", MAX_TITLE_LEN);
    }

    public static String generateGameTitleCustom(int length, String charSet) {
        int finalLength = Math.max(length, MIN_GAME_TITLE_LEN);
        return generateDelimitedString(finalLength, charSet, " -", MAX_GAME_TITLE_LEN);
    }
}
