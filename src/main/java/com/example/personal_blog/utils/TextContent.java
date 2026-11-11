package com.example.personal_blog.utils;

import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;

/** Validate visible text without stripping format characters inside meaningful text (e.g. emoji ZWJ). */
public final class TextContent {
    private TextContent() {}

    public static String requireVisible(String value, String message) {
        if (value == null || value.codePoints().noneMatch(TextContent::isVisible)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, message);
        }
        return trimWhitespace(value);
    }

    public static String trimWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isWhitespace(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (end > start && isWhitespace(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static boolean isVisible(int codePoint) {
        int type = Character.getType(codePoint);
        return !isWhitespace(codePoint)
                && type != Character.FORMAT && type != Character.CONTROL;
    }
}
