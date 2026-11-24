package com.example.tteonam.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AuthValidatorTest {
    @Test
    public void email_앞뒤공백은_지우고_대소문자는_그대로둔다() {
        assertEquals("User@Example.com", AuthValidator.normalizeEmail("  User@Example.com \n"));
        assertEquals("", AuthValidator.normalizeEmail(null));
    }

    @Test
    public void email_올바른형식은_통과() {
        assertNull(AuthValidator.validateEmail("traveler@example.com"));
        assertNull(AuthValidator.validateEmail("a.b+trip@mail.co.kr"));
    }

    @Test
    public void email_비었거나_형식이틀리면_오류() {
        assertNotNull(AuthValidator.validateEmail(""));
        assertNotNull(AuthValidator.validateEmail("traveler"));
        assertNotNull(AuthValidator.validateEmail("traveler@example"));
        assertNotNull(AuthValidator.validateEmail("trav eler@example.com"));
        assertNotNull(AuthValidator.validateEmail("@example.com"));
    }

    @Test
    public void loginPassword_비어있지만_않으면_통과() {
        assertNull(AuthValidator.validateLoginPassword("abc"));
        assertNull(AuthValidator.validateLoginPassword(" "));
    }

    @Test
    public void loginPassword_비었으면_오류() {
        assertNotNull(AuthValidator.validateLoginPassword(""));
        assertNotNull(AuthValidator.validateLoginPassword(null));
    }

    @Test
    public void signUpPassword_8자_영문_숫자를_모두_만족하면_통과() {
        assertNull(AuthValidator.validateSignUpPassword("trip1234"));
        assertNull(AuthValidator.validateSignUpPassword("TRIP1234"));
        assertNull(AuthValidator.validateSignUpPassword("trip 12!@"));
    }

    @Test
    public void signUpPassword_규칙을_하나라도_어기면_오류() {
        assertNotNull(AuthValidator.validateSignUpPassword(""));
        assertNotNull(AuthValidator.validateSignUpPassword("trip123"));
        assertNotNull(AuthValidator.validateSignUpPassword("12345678"));
        assertNotNull(AuthValidator.validateSignUpPassword("tripTrip"));
        assertNotNull(AuthValidator.validateSignUpPassword("여행여행1234"));
    }

    @Test
    public void passwordConfirm_완전히_같아야_통과() {
        assertNull(AuthValidator.validatePasswordConfirm("trip1234", "trip1234"));
        assertNotNull(AuthValidator.validatePasswordConfirm("trip1234", ""));
        assertNotNull(AuthValidator.validatePasswordConfirm("trip1234", "trip1234 "));
        assertNotNull(AuthValidator.validatePasswordConfirm("trip1234", "Trip1234"));
    }

    @Test
    public void nickname_공백만있으면_오류() {
        String nickname = AuthValidator.normalizeNickname("   ");
        assertEquals("", nickname);
        assertNotNull(AuthValidator.validateNickname(nickname));
    }

    @Test
    public void nickname_30자는_통과_31자는_오류() {
        assertNull(AuthValidator.validateNickname(repeat("가", 30)));
        assertNotNull(AuthValidator.validateNickname(repeat("가", 31)));
    }

    @Test
    public void nickname_이모지는_한글자로_센다() {
        assertNull(AuthValidator.validateNickname(repeat("😀", 30)));
        assertNotNull(AuthValidator.validateNickname(repeat("😀", 31)));
    }

    private static String repeat(String s, int times) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) {
            builder.append(s);
        }
        return builder.toString();
    }
}
