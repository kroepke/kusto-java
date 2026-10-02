// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: .NET DateTime value (100 ns ticks since 0001-01-01) with parse and format (PORTING.md 3.7, 5.2).
// The parsing algorithm (lexer, state table, date/time assembly) is derived from dotnet/runtime
// src/libraries/System.Private.CoreLib/src/System/Globalization/DateTimeParse.cs and
// DateTimeFormatInfo.cs (branch release/10.0), Copyright (c) .NET Foundation and Contributors,
// licensed under the MIT License (see NOTICE). Invariant-culture path only.

package org.graylog.kusto.language.utils.dotnet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Immutable mirror of .NET {@code System.DateTime}: ticks since 0001-01-01T00:00 in the proleptic
 * Gregorian calendar plus a {@link Kind}. Equality and ordering use the ticks only, as in .NET.
 */
public final class DateTime implements Comparable<DateTime> {
    /** .NET {@code DateTimeKind}. */
    public enum Kind {
        Unspecified,
        Utc,
        Local
    }

    private static final long TICKS_PER_DAY = TimeSpan.TICKS_PER_DAY;
    private static final long MAX_TICKS = 3_155_378_975_999_999_999L;
    /** {@code LocalDate.of(1, 1, 1).toEpochDay()}. */
    private static final long EPOCH_DAY_0001 = -719_162L;

    public static final DateTime MIN_VALUE = new DateTime(0L, Kind.Unspecified);
    public static final DateTime MAX_VALUE = new DateTime(MAX_TICKS, Kind.Unspecified);

    private final long ticks;
    private final Kind kind;

    private DateTime(long ticks, Kind kind) {
        this.ticks = ticks;
        this.kind = kind;
    }

    /**
     * .NET {@code new DateTime(ticks, kind)}.
     *
     * @throws IndexOutOfBoundsException (.NET {@code ArgumentOutOfRangeException}) outside 0..MaxValue
     */
    public static DateTime ofTicks(long ticks, Kind kind) {
        if (ticks < 0 || ticks > MAX_TICKS) {
            throw new IndexOutOfBoundsException("Ticks must be between DateTime.MinValue.Ticks and DateTime.MaxValue.Ticks.");
        }
        return new DateTime(ticks, kind);
    }

    public long ticks() {
        return ticks;
    }

    public Kind kind() {
        return kind;
    }

    private LocalDateTime local() {
        long day = ticks / TICKS_PER_DAY;
        long nanoOfDay = ticks % TICKS_PER_DAY * 100;
        return LocalDate.ofEpochDay(day + EPOCH_DAY_0001).atStartOfDay().plusNanos(nanoOfDay);
    }

    public int year() {
        return local().getYear();
    }

    public int month() {
        return local().getMonthValue();
    }

    public int day() {
        return local().getDayOfMonth();
    }

    public int hour() {
        return (int) (ticks / TimeSpan.TICKS_PER_HOUR % 24);
    }

    public int minute() {
        return (int) (ticks / TimeSpan.TICKS_PER_MINUTE % 60);
    }

    public int second() {
        return (int) (ticks / TimeSpan.TICKS_PER_SECOND % 60);
    }

    public int millisecond() {
        return (int) (ticks / TimeSpan.TICKS_PER_MILLISECOND % 1000);
    }

    /** .NET {@code DayOfWeek}: 0 = Sunday .. 6 = Saturday. */
    public int dayOfWeek() {
        return (int) ((ticks / TICKS_PER_DAY + 1) % 7);
    }

    @Override
    public int compareTo(DateTime o) {
        return Long.compare(ticks, o.ticks);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DateTime d && d.ticks == ticks;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(ticks);
    }

    /** .NET {@code DateTime.ToString()} under the invariant culture: {@code MM/dd/yyyy HH:mm:ss}. */
    @Override
    public String toString() {
        LocalDateTime t = local();
        StringBuilder sb = new StringBuilder(19);
        pad(sb, t.getMonthValue(), 2).append('/');
        pad(sb, t.getDayOfMonth(), 2).append('/');
        pad(sb, t.getYear(), 4).append(' ');
        pad(sb, t.getHour(), 2).append(':');
        pad(sb, t.getMinute(), 2).append(':');
        pad(sb, t.getSecond(), 2);
        return sb.toString();
    }

    private static StringBuilder pad(StringBuilder sb, int v, int width) {
        String s = Integer.toString(v);
        for (int i = s.length(); i < width; i++) {
            sb.append('0');
        }
        return sb.append(s);
    }

    // ---- parsing ----

    /**
     * .NET {@code DateTime.TryParse(string)} under the invariant culture ({@code DateTimeStyles.None},
     * {@code TZ=UTC}); null on failure. This is a port of the lenient token state machine in .NET's
     * {@code DateTimeParse.TryParse} / {@code Lex} (System.Private.CoreLib, release/10.0), restricted to
     * the invariant {@code DateTimeFormatInfo}: Gregorian calendar, short date {@code MM/dd/yyyy},
     * month/day {@code MMMM dd}, year/month {@code yyyy MMMM}, two-digit year max 2049.
     *
     * <p>Lexing. White space ({@code char.IsWhiteSpace}) separates tokens and is otherwise ignored, as
     * are {@code ,} and {@code .} (so {@code 2025, 6, 14} reads like {@code 2025 6 14}). Tokens:
     * <ul>
     *   <li>numbers: up to two digits are plain numbers, three to eight digits a year (taken as is, so
     *       {@code 0025} is year 25), more digits fail;</li>
     *   <li>the separator after a number or month name: {@code /} (date), {@code -} (date, or the
     *       start of a {@code ±hh[:mm]}/{@code ±hhmm} offset when a date separator cannot continue the
     *       current state), {@code :} (time), {@code T} (ISO 8601 time mark), {@code AM}/{@code PM},
     *       the CJK/Korean year, month, day, hour, minute and second suffixes, or anything else
     *       (white space, {@code ,}, a letter) as a plain space; white space may surround any separator
     *       ({@code 2017 - 08 - 01});</li>
     *   <li>words, case-insensitive and whole-word only: English month and day names and their
     *       three-letter abbreviations, {@code AM}/{@code PM}, the era {@code AD}/{@code A.D.}, and the
     *       zones {@code GMT}/{@code Z}; any other letter fails;</li>
     *   <li>a stray {@code +}/{@code -} starts a zone offset; {@code #...#} around the value and
     *       trailing NULs are tolerated (VB compatibility).</li>
     * </ul>
     *
     * <p>Grammar. A state table groups up to three numbers and an optional month name into a date and
     * a time; reaching a terminal state commits that part and starts over, so date and time may come
     * in either order ({@code 15:04 2025-6-14}). Dates: three numbers are {@code M d y} unless one is a
     * year token ({@code y M d}, {@code M d y}); two numbers are {@code M d} of the current year (or
     * {@code y M} / {@code M y} with a year token); a month name with one number is
     * {@code MMMM d} of the current year, but {@code d MMMM} reads the number as a year
     * ({@code 14 June} is 2014-06-01, as in .NET); a month name with two numbers tries day-then-year,
     * then year-then-day. Two-digit years map to 1950..2049. Times: {@code H:m[:s[.f]]}, or {@code H}
     * with {@code AM}/{@code PM}; {@code AM} needs an hour up to 12, {@code PM} adds 12 below 12.
     * After a {@code y-M-d} or {@code y M d} date, {@code T} switches to .NET's ISO 8601 branch:
     * {@code HH:mm[:ss[.f...]]} with exactly two-digit fields and an optional {@code Z} or offset.
     * Fractions of any length are summed as a double digit by digit and rounded half to even to
     * ticks, as .NET's {@code ParseFraction} does ({@code .12345678} gives 1234568 ticks). Missing
     * year/month/day default as in .NET's {@code CheckDefaultDateTime}: the current year (UTC), month
     * 1 and day 1, or today when only a time is given. A day name must match the date.
     *
     * <p>A zone converts the value to UTC and the result has {@link Kind#Utc} (D11; .NET returns
     * {@code Local}, which equals UTC on the oracle). Offsets beyond ±14:00 fail. As in .NET's
     * {@code AdjustTimeZoneToLocal}, a negative UTC result gets one day added
     * ({@code 0001-01-01T00:00:00+01:00} gives 0001-01-01 23:00), and a result past {@code MaxValue}
     * fails.
     *
     * <p>Not ported: other cultures' tokens and date words, Hebrew numbers, Japanese/Taiwan eras and
     * the "same date and time separator" rules (the invariant culture uses {@code /} and {@code :}).
     */
    public static DateTime tryParse(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        return new Parser(input).tryParse();
    }

    /** {@code DateTimeParse.DTT}: lexer token kinds; the order matches the state table's columns. */
    private enum Dtt {
        End, NumEnd, NumAmpm, NumSpace, NumDatesep, NumTimesep, MonthEnd, MonthSpace, MonthDatesep,
        NumDatesuff, NumTimesuff, DayOfWeek, YearSpace, YearDateSep, YearEnd, TimeZone, Era,
        NumUTCTimeMark, Unk, NumLocalTimeMark
    }

    /** {@code DateTimeParse.DS}: parser states; states after {@code ERROR} are terminal. */
    private enum Ds {
        BEGIN, N, NN, D_Nd, D_NN, D_NNd, D_M, D_MN, D_NM, D_MNd, D_NDS, D_Y, D_YN, D_YNd, D_YM, D_YMd,
        D_S, T_S, T_Nt, T_NNt, ERROR,
        DX_NN, DX_NNN, DX_MN, DX_NM, DX_MNN, DX_DS, DX_DSN, DX_NDS, DX_NNDS, DX_YNN, DX_YMN, DX_YN,
        DX_YM, TX_N, TX_NN, TX_NNN, TX_TS, DX_NNY;

        boolean isTerminal() {
            return ordinal() > ERROR.ordinal();
        }
    }

    // {@code DateTimeParse.s_dateParsingStates}, verbatim. Columns: End NumEnd NumAmPm NumSpace
    // NumDaySep NumTimesep MonthEnd MonthSpace MonthDSep NumDateSuff NumTimeSuff DayOfWeek YearSpace
    // YearDateSep YearEnd TimeZone Era UTCTimeMark.
    private static final Ds[][] STATES;

    static {
        String[] rows = {
            /* BEGIN  */ "BEGIN ERROR TX_N N D_Nd T_Nt ERROR D_M D_M D_S T_S BEGIN D_Y D_Y ERROR BEGIN BEGIN ERROR",
            /* N      */ "ERROR DX_NN TX_NN NN D_NNd ERROR DX_NM D_NM D_MNd D_NDS ERROR N D_YN D_YNd DX_YN N N ERROR",
            /* NN     */ "DX_NN DX_NNN TX_NNN DX_NNN ERROR T_Nt DX_MNN DX_MNN ERROR ERROR T_S NN DX_NNY ERROR DX_NNY NN NN ERROR",
            /* D_Nd   */ "ERROR DX_NN ERROR D_NN D_NNd ERROR DX_NM D_MN D_MNd ERROR ERROR D_Nd D_YN D_YNd DX_YN ERROR D_Nd ERROR",
            /* D_NN   */ "DX_NN DX_NNN TX_N DX_NNN ERROR T_Nt DX_MNN DX_MNN ERROR DX_DS T_S D_NN DX_NNY ERROR DX_NNY ERROR D_NN ERROR",
            /* D_NNd  */ "ERROR DX_NNN DX_NNN DX_NNN ERROR ERROR DX_MNN DX_MNN ERROR DX_DS ERROR D_NNd DX_NNY ERROR DX_NNY ERROR D_NNd ERROR",
            /* D_M    */ "ERROR DX_MN ERROR D_MN D_MNd ERROR ERROR ERROR ERROR ERROR ERROR D_M D_YM D_YMd DX_YM ERROR D_M ERROR",
            /* D_MN   */ "DX_MN DX_MNN DX_MNN DX_MNN ERROR T_Nt ERROR ERROR ERROR DX_DS T_S D_MN DX_YMN ERROR DX_YMN ERROR D_MN ERROR",
            /* D_NM   */ "DX_NM DX_MNN DX_MNN DX_MNN ERROR T_Nt ERROR ERROR ERROR DX_DS T_S D_NM DX_YMN ERROR DX_YMN ERROR D_NM ERROR",
            /* D_MNd  */ "ERROR DX_MNN ERROR DX_MNN ERROR ERROR ERROR ERROR ERROR ERROR ERROR D_MNd DX_YMN ERROR DX_YMN ERROR D_MNd ERROR",
            /* D_NDS  */ "DX_NDS DX_NNDS DX_NNDS DX_NNDS ERROR T_Nt ERROR ERROR ERROR D_NDS T_S D_NDS ERROR ERROR ERROR ERROR D_NDS ERROR",
            /* D_Y    */ "ERROR DX_YN ERROR D_YN D_YNd ERROR DX_YM D_YM D_YMd D_YM ERROR D_Y ERROR ERROR ERROR ERROR D_Y ERROR",
            /* D_YN   */ "DX_YN DX_YNN DX_YNN DX_YNN ERROR ERROR DX_YMN DX_YMN ERROR ERROR ERROR D_YN ERROR ERROR ERROR ERROR D_YN ERROR",
            /* D_YNd  */ "ERROR DX_YNN DX_YNN DX_YNN ERROR ERROR DX_YMN DX_YMN ERROR ERROR ERROR D_YN ERROR ERROR ERROR ERROR D_YN ERROR",
            /* D_YM   */ "DX_YM DX_YMN DX_YMN DX_YMN ERROR ERROR ERROR ERROR ERROR ERROR ERROR D_YM ERROR ERROR ERROR ERROR D_YM ERROR",
            /* D_YMd  */ "ERROR DX_YMN DX_YMN DX_YMN ERROR ERROR ERROR ERROR ERROR ERROR ERROR D_YM ERROR ERROR ERROR ERROR D_YM ERROR",
            /* D_S    */ "DX_DS DX_DSN TX_N T_Nt ERROR T_Nt ERROR ERROR ERROR D_S T_S D_S ERROR ERROR ERROR ERROR D_S ERROR",
            /* T_S    */ "TX_TS TX_TS TX_TS T_Nt D_Nd ERROR ERROR ERROR ERROR D_S T_S T_S ERROR ERROR ERROR T_S T_S ERROR",
            /* T_Nt   */ "ERROR TX_NN TX_NN TX_NN ERROR T_NNt DX_NM D_NM ERROR ERROR T_S ERROR ERROR ERROR ERROR T_Nt T_Nt TX_NN",
            /* T_NNt  */ "ERROR TX_NNN TX_NNN TX_NNN ERROR ERROR ERROR ERROR ERROR ERROR T_S T_NNt ERROR ERROR ERROR T_NNt T_NNt TX_NNN",
        };
        STATES = new Ds[rows.length][];
        for (int i = 0; i < rows.length; i++) {
            String[] cells = rows[i].split(" ");
            STATES[i] = new Ds[cells.length];
            for (int j = 0; j < cells.length; j++) {
                STATES[i][j] = Ds.valueOf(cells[j]);
            }
        }
    }

    private static Ds next(Ds state, Dtt token) {
        return STATES[state.ordinal()][token.ordinal()];
    }

    // .NET TokenType values (DateTimeParse.cs).
    private static final int NUMBER_TOKEN = 1;
    private static final int YEAR_NUMBER_TOKEN = 2;
    private static final int AM = 3;
    private static final int PM = 4;
    private static final int MONTH_TOKEN = 5;
    private static final int END_OF_STRING = 6;
    private static final int DAY_OF_WEEK_TOKEN = 7;
    private static final int TIME_ZONE_TOKEN = 8;
    private static final int ERA_TOKEN = 9;
    private static final int UNKNOWN_TOKEN = 11;
    private static final int IGNORABLE_SYMBOL = 15;
    private static final int SEP_UNK = 0x100;
    private static final int SEP_END = 0x200;
    private static final int SEP_SPACE = 0x300;
    private static final int SEP_AM = 0x400;
    private static final int SEP_PM = 0x500;
    private static final int SEP_DATE = 0x600;
    private static final int SEP_TIME = 0x700;
    private static final int SEP_YEAR_SUFF = 0x800;
    private static final int SEP_MONTH_SUFF = 0x900;
    private static final int SEP_DAY_SUFF = 0xa00;
    private static final int SEP_HOUR_SUFF = 0xb00;
    private static final int SEP_MINUTE_SUFF = 0xc00;
    private static final int SEP_SECOND_SUFF = 0xd00;
    private static final int SEP_LOCAL_TIME_MARK = 0xe00;
    private static final int SEP_DATE_OR_OFFSET = 0xf00;
    private static final int REGULAR_TOKEN_MASK = 0x00ff;
    private static final int SEPARATOR_TOKEN_MASK = 0xff00;

    private record Token(String text, int type, int value) {
    }

    private static final String[] MONTHS = {
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    };
    private static final String[] DAYS = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    /**
     * The invariant {@code DateTimeFormatInfo.CreateTokenHashTable} entries. A string inserted twice
     * with a regular and a separator type keeps both ({@code AM}, {@code PM}). Lookup order does not
     * matter: tokens starting with a letter only match whole words, and no two entries can match at the
     * same position.
     */
    private static final Token[] TOKENS;

    static {
        java.util.List<Token> t = new java.util.ArrayList<>();
        t.add(new Token(",", IGNORABLE_SYMBOL, 0));
        t.add(new Token(".", IGNORABLE_SYMBOL, 0));
        t.add(new Token(":", SEP_TIME, 0));
        t.add(new Token("AM", SEP_AM | AM, 0));
        t.add(new Token("PM", SEP_PM | PM, 1));
        t.add(new Token("年", SEP_YEAR_SUFF, 0));
        t.add(new Token("년", SEP_YEAR_SUFF, 0));
        t.add(new Token("月", SEP_MONTH_SUFF, 0));
        t.add(new Token("월", SEP_MONTH_SUFF, 0));
        t.add(new Token("日", SEP_DAY_SUFF, 0));
        t.add(new Token("일", SEP_DAY_SUFF, 0));
        t.add(new Token("時", SEP_HOUR_SUFF, 0));
        t.add(new Token("时", SEP_HOUR_SUFF, 0));
        t.add(new Token("分", SEP_MINUTE_SUFF, 0));
        t.add(new Token("秒", SEP_SECOND_SUFF, 0));
        t.add(new Token("-", SEP_DATE_OR_OFFSET, 0));
        t.add(new Token("/", SEP_DATE, 0));
        for (int i = 0; i < 12; i++) {
            t.add(new Token(MONTHS[i], MONTH_TOKEN, i + 1));
            if (!MONTHS[i].equals("May")) {
                t.add(new Token(MONTHS[i].substring(0, 3), MONTH_TOKEN, i + 1));
            }
        }
        for (int i = 0; i < 7; i++) {
            t.add(new Token(DAYS[i], DAY_OF_WEEK_TOKEN, i));
            t.add(new Token(DAYS[i].substring(0, 3), DAY_OF_WEEK_TOKEN, i));
        }
        t.add(new Token("A.D.", ERA_TOKEN, 1));
        t.add(new Token("AD", ERA_TOKEN, 1));
        t.add(new Token("T", SEP_LOCAL_TIME_MARK, 0));
        t.add(new Token("GMT", TIME_ZONE_TOKEN, 0));
        t.add(new Token("Z", TIME_ZONE_TOKEN, 0));
        TOKENS = t.toArray(new Token[0]);
    }

    // DateTimeResult / ParseFlags bits used here.
    private static final int HAVE_YEAR = 0x1;
    private static final int HAVE_MONTH = 0x2;
    private static final int HAVE_DAY = 0x4;
    private static final int HAVE_HOUR = 0x8;
    private static final int HAVE_MINUTE = 0x10;
    private static final int HAVE_SECOND = 0x20;
    private static final int HAVE_TIME = 0x40;
    private static final int HAVE_DATE = 0x80;
    private static final int TIME_ZONE_USED = 0x100;
    private static final int TIME_ZONE_UTC = 0x200;
    private static final int YEAR_DEFAULT = 0x10000;

    private static final int TM_NOT_SET = -1;
    private static final int TM_AM = 0;
    private static final int TM_PM = 1;

    private static final int MAX_DATE_TIME_NUMBER_DIGITS = 8;
    private static final int TWO_DIGIT_YEAR_MAX = 2049;
    private static final long MAX_OFFSET_TICKS = 14 * TimeSpan.TICKS_PER_HOUR;
    private static final char RIGHT_TO_LEFT_MARK = '‏';

    /**
     * One parse: {@code __DTString} (cursor {@code index}, current char derived from it),
     * {@code DateTimeRawInfo} ({@code raw*}), {@code DateTimeResult} ({@code res*}) and
     * {@code DateTimeToken} ({@code dtok*}) folded into one object.
     */
    private static final class Parser {
        private final String s;
        private final int length;
        private int index = -1;

        // DateTimeToken
        private Dtt dtokDtt;
        private int dtokSuffix = SEP_UNK;
        private int dtokNum;

        // DateTimeRawInfo
        private final int[] rawNum = new int[3];
        private int rawNumCount;
        private int rawMonth = -1;
        private int rawYear = -1;
        private int rawDayOfWeek = -1;
        private int rawTimeMark = TM_NOT_SET;
        private double rawFraction = -1;

        // DateTimeResult
        private int resYear = -1;
        private int resMonth = -1;
        private int resDay = -1;
        private int resHour;
        private int resMinute;
        private int resSecond;
        private int resFlags;
        private long resTimeZoneOffset;

        Parser(String s) {
            this.s = s;
            this.length = s.length();
        }

        // ---- __DTString ----

        private char current() {
            return s.charAt(index);
        }

        private boolean getNext() {
            index++;
            return index < length;
        }

        private boolean atEnd() {
            return index >= length;
        }

        private static boolean isAsciiDigit(char c) {
            return c >= '0' && c <= '9';
        }

        /** {@code __DTString.GetRegularToken}: sets {@link #tokType}/{@link #tokValue}. */
        private int tokType;
        private int tokValue;

        private void getRegularToken() {
            tokValue = 0;
            if (index >= length) {
                tokType = END_OF_STRING;
                return;
            }
            while (true) {
                char c = current();
                if (isAsciiDigit(c)) {
                    int value = c - '0';
                    int start = index;
                    while (++index < length) {
                        char d = s.charAt(index);
                        if (isAsciiDigit(d)) {
                            value = value * 10 + (d - '0');
                        } else {
                            break;
                        }
                    }
                    if (index - start > MAX_DATE_TIME_NUMBER_DIGITS) {
                        tokType = NUMBER_TOKEN;
                        tokValue = -1;
                    } else if (index - start < 3) {
                        tokType = NUMBER_TOKEN;
                        tokValue = value;
                    } else {
                        tokType = YEAR_NUMBER_TOKEN;
                        tokValue = value;
                    }
                    return;
                } else if (DotNetChars.isWhiteSpace(c)) {
                    while (++index < length) {
                        if (!DotNetChars.isWhiteSpace(s.charAt(index))) {
                            break;
                        }
                    }
                    if (index >= length) {
                        tokType = END_OF_STRING;
                        return;
                    }
                } else {
                    if (!tokenize(REGULAR_TOKEN_MASK)) {
                        tokType = UNKNOWN_TOKEN;
                        tokValue = 0;
                    }
                    return;
                }
            }
        }

        /** {@code DateTimeFormatInfo.Tokenize} over {@link #TOKENS}; advances past a match. */
        private boolean tokenize(int mask) {
            char ch = current();
            boolean isLetter = DotNetChars.isLetter(ch);
            int remaining = length - index;
            for (Token t : TOKENS) {
                int type = t.type() & mask;
                String text = t.text();
                if (type == 0 || text.length() > remaining) {
                    continue;
                }
                // The hash table is keyed on the lower-cased first character.
                if (DotNetChars.toLowerInvariant(text.charAt(0)) != DotNetChars.toLowerInvariant(ch)) {
                    continue;
                }
                if (isLetter) {
                    int nextCharIndex = index + text.length();
                    if (nextCharIndex < length && DotNetChars.isLetter(s.charAt(nextCharIndex))) {
                        continue;
                    }
                }
                boolean matches = (text.length() == 1 && ch == text.charAt(0))
                        || DotNetStrings.equalsOrdinalIgnoreCase(s.substring(index, index + text.length()), text);
                if (matches) {
                    tokType = type;
                    tokValue = t.value();
                    index += text.length();
                    return true;
                }
            }
            return false;
        }

        private int indexBeforeSeparator;

        /** {@code __DTString.GetSeparatorToken}. */
        private int getSeparatorToken() {
            indexBeforeSeparator = index;
            if (!skipWhiteSpaceAndRtlMarkCurrent()) {
                return SEP_END;
            }
            if (!isAsciiDigit(current())) {
                int savedType = tokType;
                int savedValue = tokValue;
                int result = tokenize(SEPARATOR_TOKEN_MASK) ? tokType : SEP_SPACE;
                tokType = savedType;
                tokValue = savedValue;
                return result;
            }
            return SEP_SPACE;
        }

        private boolean skipWhiteSpaceAndRtlMarkCurrent() {
            if (index >= length) {
                return false;
            }
            while (index < length) {
                char c = s.charAt(index);
                if (!DotNetChars.isWhiteSpace(c) && c != RIGHT_TO_LEFT_MARK) {
                    return true;
                }
                index++;
            }
            return false;
        }

        private void skipWhiteSpaces() {
            while (index + 1 < length && DotNetChars.isWhiteSpace(s.charAt(index + 1))) {
                index++;
            }
        }

        /** {@code __DTString.Match(char)}. */
        private boolean match(char ch) {
            if (++index >= length) {
                return false;
            }
            char c = s.charAt(index);
            if (c == ch || (ch == ' ' && (c == ' ' || c == ' '))) {
                return true;
            }
            index--;
            return false;
        }

        // ---- DateTimeParse helpers ----

        /** {@code ParseFraction}: from the delimiter at {@code index}; leaves {@code index} on the first non-digit. */
        private double parsedFraction;

        private boolean parseFraction() {
            double result = 0;
            double decimalBase = 0.1;
            int digits = 0;
            while (getNext() && isAsciiDigit(current())) {
                result += (current() - '0') * decimalBase;
                decimalBase *= 0.1;
                digits++;
            }
            parsedFraction = result;
            return digits > 0;
        }

        /** {@code ParseTimeZone}: sign at {@code index}, then {@code h}, {@code hh}, {@code h[h]:m[m]}, {@code hmm} or {@code hhmm}. */
        private boolean parseTimeZone() {
            if (index >= length) {
                return false;
            }
            char sign = s.charAt(index);
            if (sign != '+' && sign != '-') {
                return false;
            }
            index++;
            int start = index;
            int end = start;
            while (end < length && isAsciiDigit(s.charAt(end))) {
                end++;
            }
            int len = end - start;
            if (len == 0 || len > MAX_DATE_TIME_NUMBER_DIGITS) {
                return false;
            }
            int value = Integer.parseInt(s, start, end, 10);
            int hourOffset;
            int minuteOffset = 0;
            if (len == 1 || len == 2) {
                hourOffset = value;
                index = end;
                if (index < length && s.charAt(index) == ':') {
                    int ms = index + 1;
                    int me = ms;
                    while (me < length && isAsciiDigit(s.charAt(me))) {
                        me++;
                    }
                    if (me - ms < 1 || me - ms > 2) {
                        return false;
                    }
                    minuteOffset = Integer.parseInt(s, ms, me, 10);
                    index = me;
                }
            } else if (len == 3 || len == 4) {
                hourOffset = value / 100;
                minuteOffset = value % 100;
                index = end;
            } else {
                return false;
            }
            if (minuteOffset >= 60) {
                return false;
            }
            long offset = hourOffset * TimeSpan.TICKS_PER_HOUR + minuteOffset * TimeSpan.TICKS_PER_MINUTE;
            resTimeZoneOffset = sign == '-' ? -offset : offset;
            return true;
        }

        /** {@code HandleTimeZone}: an offset right after a time number, possibly after white space. */
        private boolean handleTimeZone() {
            if (index < length - 1) {
                char nextCh = s.charAt(index);
                int whitespaceCount = 0;
                while (DotNetChars.isWhiteSpace(nextCh) && index + whitespaceCount < length - 1) {
                    whitespaceCount++;
                    nextCh = s.charAt(index + whitespaceCount);
                }
                if (nextCh == '+' || nextCh == '-') {
                    index += whitespaceCount;
                    if ((resFlags & TIME_ZONE_USED) != 0) {
                        return false;
                    }
                    resFlags |= TIME_ZONE_USED;
                    return parseTimeZone();
                }
            }
            return true;
        }

        /** {@code VerifyValidPunctuation}: {@code #...#} around the value, or trailing NULs. */
        private boolean verifyValidPunctuation() {
            char ch = s.charAt(index);
            if (ch == '#') {
                boolean foundStart = false;
                boolean foundEnd = false;
                for (int i = 0; i < length; i++) {
                    ch = s.charAt(i);
                    if (ch == '#') {
                        if (foundStart) {
                            if (foundEnd) {
                                return false;
                            }
                            foundEnd = true;
                        } else {
                            foundStart = true;
                        }
                    } else if (ch == '\0') {
                        if (!foundEnd) {
                            return false;
                        }
                    } else if (!DotNetChars.isWhiteSpace(ch)) {
                        if (!foundStart || foundEnd) {
                            return false;
                        }
                    }
                }
                if (!foundEnd) {
                    return false;
                }
                getNext();
                return true;
            } else if (ch == '\0') {
                for (int i = index + 1; i < length; i++) {
                    if (s.charAt(i) != '\0') {
                        return false;
                    }
                }
                index = length;
                return true;
            }
            return false;
        }

        private void addNumber(int value) {
            rawNum[rawNumCount++] = value;
        }

        private int number(int i) {
            return rawNum[i];
        }

        // ---- Lex ----

        private boolean lex(Ds dps) {
            dtokDtt = Dtt.Unk;
            getRegularToken();
            int tokenType = tokType;
            int tokenValue = tokValue;
            int sep;
            switch (tokenType) {
                case NUMBER_TOKEN, YEAR_NUMBER_TOKEN -> {
                    if (rawNumCount == 3 || tokenValue == -1) {
                        return false;
                    }
                    if (dps == Ds.T_NNt && index < length - 1) {
                        char nextCh = s.charAt(index);
                        if (nextCh == '.' || nextCh == ',') {
                            parseFraction();
                            rawFraction = parsedFraction;
                        }
                    }
                    if ((dps == Ds.T_NNt || dps == Ds.T_Nt) && index < length - 1) {
                        if (!handleTimeZone()) {
                            return false;
                        }
                    }
                    dtokNum = tokenValue;
                    if (tokenType == YEAR_NUMBER_TOKEN) {
                        if (rawYear != -1) {
                            return false;
                        }
                        rawYear = tokenValue;
                        switch (sep = getSeparatorToken()) {
                            case SEP_END -> dtokDtt = Dtt.YearEnd;
                            case SEP_AM, SEP_PM -> {
                                // A second AM/PM leaves the token unknown (.NET only records the failure).
                                if (rawTimeMark == TM_NOT_SET) {
                                    rawTimeMark = sep == SEP_AM ? TM_AM : TM_PM;
                                    dtokDtt = Dtt.YearSpace;
                                }
                            }
                            case SEP_SPACE -> dtokDtt = Dtt.YearSpace;
                            case SEP_DATE -> dtokDtt = Dtt.YearDateSep;
                            case SEP_DATE_OR_OFFSET -> {
                                if (next(dps, Dtt.YearDateSep) == Ds.ERROR && next(dps, Dtt.YearSpace).isTerminal()) {
                                    index = indexBeforeSeparator;
                                    dtokDtt = Dtt.YearSpace;
                                } else {
                                    dtokDtt = Dtt.YearDateSep;
                                }
                            }
                            case SEP_YEAR_SUFF, SEP_MONTH_SUFF, SEP_DAY_SUFF -> {
                                dtokDtt = Dtt.NumDatesuff;
                                dtokSuffix = sep;
                            }
                            case SEP_HOUR_SUFF, SEP_MINUTE_SUFF, SEP_SECOND_SUFF -> {
                                dtokDtt = Dtt.NumTimesuff;
                                dtokSuffix = sep;
                            }
                            default -> {
                                // SEP_Time (date and time separators differ), SEP_LocalTimeMark.
                                return false;
                            }
                        }
                        return true;
                    }
                    switch (sep = getSeparatorToken()) {
                        case SEP_END -> {
                            dtokDtt = Dtt.NumEnd;
                            addNumber(dtokNum);
                        }
                        case SEP_AM, SEP_PM -> {
                            if (rawTimeMark == TM_NOT_SET) {
                                rawTimeMark = sep == SEP_AM ? TM_AM : TM_PM;
                                dtokDtt = Dtt.NumAmpm;
                                // .NET: "Fix AM/PM parsing case, e.g. "1/10 5 AM"".
                                if (dps == Ds.D_NN) {
                                    if (!processTerminalState(Ds.DX_NN)) {
                                        return false;
                                    }
                                }
                                addNumber(dtokNum);
                            } else {
                                // .NET records the failure but carries on with an unknown token.
                                break;
                            }
                            if (dps == Ds.T_NNt || dps == Ds.T_Nt) {
                                if (!handleTimeZone()) {
                                    return false;
                                }
                            }
                        }
                        case SEP_SPACE -> {
                            dtokDtt = Dtt.NumSpace;
                            addNumber(dtokNum);
                        }
                        case SEP_DATE -> {
                            dtokDtt = Dtt.NumDatesep;
                            addNumber(dtokNum);
                        }
                        case SEP_DATE_OR_OFFSET -> {
                            if (next(dps, Dtt.NumDatesep) == Ds.ERROR && next(dps, Dtt.NumSpace).isTerminal()) {
                                index = indexBeforeSeparator;
                                dtokDtt = Dtt.NumSpace;
                            } else {
                                dtokDtt = Dtt.NumDatesep;
                            }
                            addNumber(dtokNum);
                        }
                        case SEP_TIME -> {
                            dtokDtt = Dtt.NumTimesep;
                            addNumber(dtokNum);
                        }
                        case SEP_YEAR_SUFF -> {
                            dtokNum = toFourDigitYear(tokenValue);
                            dtokDtt = Dtt.NumDatesuff;
                            dtokSuffix = sep;
                        }
                        case SEP_MONTH_SUFF, SEP_DAY_SUFF -> {
                            dtokDtt = Dtt.NumDatesuff;
                            dtokSuffix = sep;
                        }
                        case SEP_HOUR_SUFF, SEP_MINUTE_SUFF, SEP_SECOND_SUFF -> {
                            dtokDtt = Dtt.NumTimesuff;
                            dtokSuffix = sep;
                        }
                        case SEP_LOCAL_TIME_MARK -> {
                            dtokDtt = Dtt.NumLocalTimeMark;
                            addNumber(dtokNum);
                        }
                        default -> {
                            return false;
                        }
                    }
                }
                case DAY_OF_WEEK_TOKEN -> {
                    if (rawDayOfWeek != -1) {
                        return false;
                    }
                    rawDayOfWeek = tokenValue;
                    dtokDtt = Dtt.DayOfWeek;
                }
                case MONTH_TOKEN -> {
                    if (rawMonth != -1) {
                        return false;
                    }
                    switch (getSeparatorToken()) {
                        case SEP_END -> dtokDtt = Dtt.MonthEnd;
                        case SEP_SPACE -> dtokDtt = Dtt.MonthSpace;
                        case SEP_DATE -> dtokDtt = Dtt.MonthDatesep;
                        case SEP_DATE_OR_OFFSET -> {
                            if (next(dps, Dtt.MonthDatesep) == Ds.ERROR && next(dps, Dtt.MonthSpace).isTerminal()) {
                                index = indexBeforeSeparator;
                                dtokDtt = Dtt.MonthSpace;
                            } else {
                                dtokDtt = Dtt.MonthDatesep;
                            }
                        }
                        default -> {
                            return false;
                        }
                    }
                    rawMonth = tokenValue;
                }
                case ERA_TOKEN -> dtokDtt = Dtt.Era; // result.era starts at CurrentEra (0), never -1
                case TIME_ZONE_TOKEN -> {
                    if ((resFlags & TIME_ZONE_USED) != 0) {
                        return false;
                    }
                    dtokDtt = Dtt.TimeZone;
                    resFlags |= TIME_ZONE_USED | TIME_ZONE_UTC;
                    resTimeZoneOffset = 0;
                }
                case END_OF_STRING -> dtokDtt = Dtt.End;
                case IGNORABLE_SYMBOL -> {
                    // skipped
                }
                case AM, PM -> {
                    if (rawTimeMark != TM_NOT_SET) {
                        return false;
                    }
                    rawTimeMark = tokenValue;
                }
                case UNKNOWN_TOKEN -> {
                    char c = current();
                    if (DotNetChars.isLetter(c)) {
                        return false;
                    }
                    if ((c == '-' || c == '+') && (resFlags & TIME_ZONE_USED) == 0) {
                        int originalIndex = index;
                        if (parseTimeZone()) {
                            resFlags |= TIME_ZONE_USED;
                            return true;
                        }
                        index = originalIndex;
                    }
                    return verifyValidPunctuation();
                }
                default -> throw new IllegalStateException("token " + tokenType);
            }
            return true;
        }

        // ---- terminal states ----

        private static int toFourDigitYear(int year) {
            return year < 100 ? (TWO_DIGIT_YEAR_MAX / 100 - (year > TWO_DIGIT_YEAR_MAX % 100 ? 1 : 0)) * 100 + year : year;
        }

        private static boolean isValidDay(int year, int month, int day) {
            return year >= 1 && year <= 9999 && month >= 1 && month <= 12 && day >= 1
                    && day <= LocalDate.of(year, month, 1).lengthOfMonth();
        }

        private boolean setDateYMD(int year, int month, int day) {
            if (isValidDay(year, month, day)) {
                resYear = year;
                resMonth = month;
                resDay = day;
                return true;
            }
            return false;
        }

        private void getDefaultYear() {
            resYear = LocalDate.now(ZoneOffset.UTC).getYear();
            resFlags |= YEAR_DEFAULT;
        }

        private boolean setDate(boolean ok) {
            if (ok) {
                resFlags |= HAVE_DATE;
            }
            return ok;
        }

        private boolean processTerminalState(Ds dps) {
            boolean passed = switch (dps) {
                // Month/day order MD (MMMM dd).
                case DX_NN -> (resFlags & HAVE_DATE) == 0 && setDate(defaultYearThen(() -> setDateYMD(resYear, number(0), number(1))));
                // Short date order MDY (MM/dd/yyyy).
                case DX_NNN -> (resFlags & HAVE_DATE) == 0 && setDate(setDateYMD(toFourDigitYear(number(2)), number(0), number(1)));
                // Month/day MD: a number after a month name is the day of the current year.
                case DX_MN -> (resFlags & HAVE_DATE) == 0 && defaultYearThen(() -> setDateYMD(resYear, rawMonth, number(0)));
                // Month/day MD and year/month YM: a number before a month name is a year.
                case DX_NM -> (resFlags & HAVE_DATE) == 0 && setDateYMD(toFourDigitYear(number(0)), rawMonth, 1);
                case DX_MNN -> (resFlags & HAVE_DATE) == 0
                        && setDate(setDateYMD(toFourDigitYear(number(1)), rawMonth, number(0))
                                || setDateYMD(toFourDigitYear(number(0)), rawMonth, number(1)));
                case DX_DS, TX_TS -> true;
                case DX_YNN, DX_NNY -> (resFlags & HAVE_DATE) == 0 && setDate(setDateYMD(rawYear, number(0), number(1)));
                case DX_YMN -> (resFlags & HAVE_DATE) == 0 && setDate(setDateYMD(rawYear, rawMonth, number(0)));
                case DX_YN -> (resFlags & HAVE_DATE) == 0 && setDate(setDateYMD(rawYear, number(0), 1));
                case DX_YM -> (resFlags & HAVE_DATE) == 0 && setDate(setDateYMD(rawYear, rawMonth, 1));
                case TX_N -> (resFlags & HAVE_TIME) == 0 && rawTimeMark != TM_NOT_SET && setTime(number(0), resMinute, resSecond);
                case TX_NN -> (resFlags & HAVE_TIME) == 0 && setTime(number(0), number(1), resSecond);
                case TX_NNN -> (resFlags & HAVE_TIME) == 0 && setTime(number(0), number(1), number(2));
                case DX_DSN -> {
                    if (rawNumCount != 1 || resDay != -1) {
                        yield false;
                    }
                    resDay = number(0);
                    yield true;
                }
                case DX_NDS -> {
                    if (resMonth == -1 || resYear != -1) {
                        yield false;
                    }
                    resYear = toFourDigitYear(number(0));
                    resDay = 1;
                    yield true;
                }
                case DX_NNDS -> getDateOfNNDS();
                default -> true;
            };
            if (!passed) {
                return false;
            }
            if (dps.isTerminal()) {
                rawNumCount = 0;
            }
            return true;
        }

        private boolean defaultYearThen(java.util.function.BooleanSupplier set) {
            getDefaultYear();
            return set.getAsBoolean();
        }

        private boolean setTime(int hour, int minute, int second) {
            resHour = hour;
            resMinute = minute;
            resSecond = second;
            resFlags |= HAVE_TIME;
            return true;
        }

        private boolean getDateOfNNDS() {
            if ((resFlags & HAVE_YEAR) != 0) {
                if ((resFlags & HAVE_MONTH) == 0 && (resFlags & HAVE_DAY) == 0) {
                    resYear = toFourDigitYear(rawYear);
                    return setDateYMD(resYear, number(0), number(1));
                }
            } else if ((resFlags & HAVE_MONTH) != 0) {
                if ((resFlags & HAVE_YEAR) == 0 && (resFlags & HAVE_DAY) == 0) {
                    // Short date order MDY: day then year.
                    return setDateYMD(toFourDigitYear(number(1)), resMonth, number(0));
                }
            }
            return false;
        }

        private boolean processDateTimeSuffix() {
            int flag;
            switch (dtokSuffix) {
                case SEP_YEAR_SUFF -> {
                    flag = HAVE_YEAR;
                    resYear = rawYear = dtokNum;
                }
                case SEP_MONTH_SUFF -> {
                    flag = HAVE_MONTH;
                    resMonth = rawMonth = dtokNum;
                }
                case SEP_DAY_SUFF -> {
                    flag = HAVE_DAY;
                    resDay = dtokNum;
                }
                case SEP_HOUR_SUFF -> {
                    flag = HAVE_HOUR;
                    resHour = dtokNum;
                }
                case SEP_MINUTE_SUFF -> {
                    flag = HAVE_MINUTE;
                    resMinute = dtokNum;
                }
                case SEP_SECOND_SUFF -> {
                    flag = HAVE_SECOND;
                    resSecond = dtokNum;
                }
                default -> {
                    return true;
                }
            }
            // .NET checks the flag before assigning; a failure discards the whole parse either way.
            if ((resFlags & flag) != 0) {
                return false;
            }
            resFlags |= flag;
            return true;
        }

        // ---- TryParse ----

        DateTime tryParse() {
            Ds dps = Ds.BEGIN;
            boolean reachTerminalState = false;
            getNext();
            do {
                if (!lex(dps)) {
                    return null;
                }
                if (dtokDtt == Dtt.Unk) {
                    continue;
                }
                if (dtokSuffix != SEP_UNK) {
                    if (!processDateTimeSuffix()) {
                        return null;
                    }
                    dtokSuffix = SEP_UNK;
                }
                if (dtokDtt == Dtt.NumLocalTimeMark) {
                    if (dps == Ds.D_YNd || dps == Ds.D_YN) {
                        return parseISO8601();
                    }
                    return null;
                }
                dps = next(dps, dtokDtt);
                if (dps == Ds.ERROR) {
                    return null;
                } else if (dps.isTerminal()) {
                    if (!processTerminalState(dps)) {
                        return null;
                    }
                    reachTerminalState = true;
                    dps = Ds.BEGIN;
                }
            } while (dtokDtt != Dtt.End && dtokDtt != Dtt.NumEnd && dtokDtt != Dtt.MonthEnd);

            if (!reachTerminalState) {
                return null;
            }
            if (!adjustHour()) {
                return null;
            }
            checkDefaultDateTime();
            if (!isValidDay(resYear, resMonth, resDay) || resHour < 0 || resHour > 23 || resMinute < 0
                    || resMinute > 59 || resSecond < 0 || resSecond > 59) {
                return null;
            }
            long ticks = dateTicks(resYear, resMonth, resDay) + resHour * TimeSpan.TICKS_PER_HOUR
                    + resMinute * TimeSpan.TICKS_PER_MINUTE + resSecond * TimeSpan.TICKS_PER_SECOND;
            if (rawFraction > 0) {
                ticks += (long) Math.rint(rawFraction * TimeSpan.TICKS_PER_SECOND);
                if (ticks > MAX_TICKS) {
                    return null;
                }
            }
            if (rawDayOfWeek != -1 && (ticks / TICKS_PER_DAY + 1) % 7 != rawDayOfWeek) {
                return null;
            }
            return determineTimeZoneAdjustments(ticks);
        }

        private boolean adjustHour() {
            if (rawTimeMark == TM_AM) {
                if (resHour < 0 || resHour > 12) {
                    return false;
                }
                resHour = resHour == 12 ? 0 : resHour;
            } else if (rawTimeMark == TM_PM) {
                if (resHour < 0 || resHour > 23) {
                    return false;
                }
                if (resHour < 12) {
                    resHour += 12;
                }
            }
            return true;
        }

        private void checkDefaultDateTime() {
            if (resYear == -1 || resMonth == -1 || resDay == -1) {
                LocalDate now = LocalDate.now(ZoneOffset.UTC);
                if (resMonth == -1 && resDay == -1) {
                    if (resYear == -1) {
                        resYear = now.getYear();
                        resMonth = now.getMonthValue();
                        resDay = now.getDayOfMonth();
                    } else {
                        resMonth = 1;
                        resDay = 1;
                    }
                } else {
                    if (resYear == -1) {
                        resYear = now.getYear();
                    }
                    if (resMonth == -1) {
                        resMonth = 1;
                    }
                    if (resDay == -1) {
                        resDay = 1;
                    }
                }
            }
        }

        private static long dateTicks(int year, int month, int day) {
            return (LocalDate.of(year, month, day).toEpochDay() - EPOCH_DAY_0001) * TICKS_PER_DAY;
        }

        /** {@code DetermineTimeZoneAdjustments} + {@code AdjustTimeZoneToLocal} with a UTC local zone. */
        private DateTime determineTimeZoneAdjustments(long ticks) {
            if (resTimeZoneOffset < -MAX_OFFSET_TICKS || resTimeZoneOffset > MAX_OFFSET_TICKS) {
                return null;
            }
            if ((resFlags & TIME_ZONE_USED) == 0) {
                return new DateTime(ticks, Kind.Unspecified);
            }
            long utc = ticks - resTimeZoneOffset;
            if (ticks < TICKS_PER_DAY && utc < 0) {
                utc += TICKS_PER_DAY;
            }
            if (utc < 0 || utc > MAX_TICKS) {
                return null;
            }
            return new DateTime(utc, Kind.Utc);
        }

        /** {@code ParseISO8601}: {@code index} is just past the {@code T}. */
        private DateTime parseISO8601() {
            index--;
            int second = 0;
            double partSecond = 0;
            skipWhiteSpaces();
            int hour = parseDigits2();
            if (hour < 0) {
                return null;
            }
            skipWhiteSpaces();
            if (!match(':')) {
                return null;
            }
            skipWhiteSpaces();
            int minute = parseDigits2();
            if (minute < 0) {
                return null;
            }
            skipWhiteSpaces();
            if (match(':')) {
                skipWhiteSpaces();
                second = parseDigits2();
                if (second < 0) {
                    return null;
                }
                if (match('.') || match(',')) {
                    if (!parseFraction()) {
                        return null;
                    }
                    partSecond = parsedFraction;
                    index--;
                }
                skipWhiteSpaces();
            }
            if (getNext()) {
                char ch = current();
                if (ch == '+' || ch == '-') {
                    resFlags |= TIME_ZONE_USED;
                    if (!parseTimeZone()) {
                        return null;
                    }
                    index--;
                } else if (ch == 'Z' || ch == 'z') {
                    resFlags |= TIME_ZONE_USED | TIME_ZONE_UTC;
                    resTimeZoneOffset = 0;
                } else {
                    index--;
                }
                skipWhiteSpaces();
                if (match('#')) {
                    if (!verifyValidPunctuation()) {
                        return null;
                    }
                    skipWhiteSpaces();
                }
                if (match('\0')) {
                    if (!verifyValidPunctuation()) {
                        return null;
                    }
                }
                if (getNext()) {
                    return null;
                }
            }
            if (!isValidDay(rawYear, number(0), number(1)) || hour > 23 || minute > 59 || second > 59) {
                return null;
            }
            long ticks = dateTicks(rawYear, number(0), number(1)) + hour * TimeSpan.TICKS_PER_HOUR
                    + minute * TimeSpan.TICKS_PER_MINUTE + second * TimeSpan.TICKS_PER_SECOND
                    + (long) Math.rint(partSecond * TimeSpan.TICKS_PER_SECOND);
            if (ticks > MAX_TICKS) {
                return null;
            }
            return determineTimeZoneAdjustments(ticks);
        }

        /** {@code ParseDigits(str, 2)}: exactly two digits after {@code index}; -1 (index kept) otherwise. */
        private int parseDigits2() {
            int start = index;
            if (index + 2 < length && isAsciiDigit(s.charAt(index + 1)) && isAsciiDigit(s.charAt(index + 2))) {
                index += 2;
                return (s.charAt(start + 1) - '0') * 10 + (s.charAt(start + 2) - '0');
            }
            return -1;
        }
    }
}
