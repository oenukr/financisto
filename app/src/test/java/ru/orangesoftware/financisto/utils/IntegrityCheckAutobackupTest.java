/*
 * Copyright (c) 2012 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package ru.orangesoftware.financisto.utils;

import static org.junit.Assert.assertEquals;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import ru.orangesoftware.financisto.db.AbstractDbTest;

public class IntegrityCheckAutobackupTest extends AbstractDbTest {

    private long currentTime = 1000L;
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(currentTime);
        }

        @Override
        public long millis() {
            return currentTime;
        }
    };

    IntegrityCheckAutobackup integrity;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        currentTime = 1000L;
        integrity = new IntegrityCheckAutobackup(TimeUnit.MILLISECONDS.toMillis(100), clock);
    }

    @Test
    public void should_check_if_autobackup_has_been_disabled() {
        // when reminder is enabled
        givenAutobackupReminderEnabledIs(true);
        givenAutobackupEnabledIs(false);

        givenFirstRunAfterRelease();
        assertEquals(IntegrityCheck.Level.OK, integrity.check(getContext()).getLevel());

        advanceTime(50);
        assertEquals(IntegrityCheck.Level.OK, integrity.check(getContext()).getLevel());
        advanceTime(51);
        // raise the info notification
        assertEquals(IntegrityCheck.Level.INFO, integrity.check(getContext()).getLevel());
        // and reset the check at the same time
        assertEquals(IntegrityCheck.Level.OK, integrity.check(getContext()).getLevel());

        givenAutobackupEnabledIs(true);
        advanceTime(101);
        assertEquals(IntegrityCheck.Level.OK, integrity.check(getContext()).getLevel());

        // when reminder is disabled
        givenAutobackupReminderEnabledIs(false);
        givenAutobackupEnabledIs(false);
        advanceTime(101);
        assertEquals(IntegrityCheck.Level.OK, integrity.check(getContext()).getLevel());
    }

    @Test
    public void should_check_if_the_last_autobackup_has_failed() {
        // when reminder is enabled
        givenAutobackupWarningEnabledIs(true);
        givenAutobackupEnabledIs(true);

        givenFirstRunAfterRelease();
        assertEquals(IntegrityCheck.Level.OK, integrity.check(getContext()).getLevel());

        givenTheLastAutobackupHasFailed();
        // raise the info notification
        assertEquals(IntegrityCheck.Level.ERROR, integrity.check(getContext()).getLevel());
        // and reset the check at the same time
        assertEquals(IntegrityCheck.Level.OK, integrity.check(getContext()).getLevel());

        givenTheLastAutobackupHasFailed();
        givenTheLastAutobackupHasSucceeded();
        assertEquals(IntegrityCheck.Level.OK, integrity.check(getContext()).getLevel());

        // when reminder is disabled
        givenAutobackupWarningEnabledIs(false);
        givenTheLastAutobackupHasFailed();
        assertEquals(IntegrityCheck.Level.OK, integrity.check(getContext()).getLevel());
    }

    private void givenTheLastAutobackupHasSucceeded() {
        MyPreferences.notifyAutobackupSucceeded(getContext());
    }

    private void givenTheLastAutobackupHasFailed() {
        MyPreferences.notifyAutobackupFailed(getContext(), new Exception("Error!"));
    }

    private void givenAutobackupEnabledIs(boolean isEnabled) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        sharedPreferences.edit().putBoolean("auto_backup_enabled", isEnabled).commit();
    }

    private void givenAutobackupReminderEnabledIs(boolean isEnabled) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        sharedPreferences.edit().putBoolean("auto_backup_reminder_enabled", isEnabled).commit();
    }

    private void givenAutobackupWarningEnabledIs(boolean isEnabled) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        sharedPreferences.edit().putBoolean("auto_backup_warning_enabled", isEnabled).commit();
    }

    private void givenFirstRunAfterRelease() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        sharedPreferences.edit()
                .remove("last_autobackup_check")
                .remove("auto_backup_failed_notify")
                .commit();
    }

    private void advanceTime(long millis) {
        currentTime += millis;
    }

}
