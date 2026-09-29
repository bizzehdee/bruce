package com.bizzeh.bruce.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class FactsTest {
    @Test
    fun firstPersonFactsAreTurnedToBeAboutTheUser() {
        assertEquals("The user is Sam", Facts.aboutUser("I am Sam"))
        assertEquals("The user is a nurse", Facts.aboutUser("I'm a nurse"))
        assertEquals("The user has two cats", Facts.aboutUser("I have two cats"))
        assertEquals("The user has been to Rome", Facts.aboutUser("I've been to Rome"))
        assertEquals("The user's name is Sam.", Facts.aboutUser("My name is Sam."))
        assertEquals("The user lives in Leeds with their dog Rex.", Facts.aboutUser("I live in Leeds with my dog Rex."))
        assertEquals("The user studies history", Facts.aboutUser("I study history"))
        assertEquals("The user watches films with them", Facts.aboutUser("I watch films with me"))
        assertEquals("The user does yoga", Facts.aboutUser("I do yoga"))
        assertEquals("The user goes running", Facts.aboutUser("I go running"))
        assertEquals("The user plays chess", Facts.aboutUser("I play chess"))
        assertEquals("The user can drive", Facts.aboutUser("I can drive"))
    }

    @Test
    fun anythingElseIsQuoted() {
        assertEquals("The user said: \"Leeds is home\"", Facts.aboutUser("  Leeds is home "))
    }
}
