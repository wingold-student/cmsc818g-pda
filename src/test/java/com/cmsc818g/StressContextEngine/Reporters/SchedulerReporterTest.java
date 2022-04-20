package com.cmsc818g.StressContextEngine.Reporters;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.http.javadsl.model.DateTime;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class SchedulerReporterTest {
    @ClassRule public static final TestKitJunitResource testKit = new TestKitJunitResource();

    private ActorRef<SchedulerReporter.Command> schedReporter;

    @Before
    public void before() {
        schedReporter = testKit.spawn(SchedulerReporter.create());
    }

    @After
    public void after() {
        testKit.stop(schedReporter);
    }

    @Test
    public void testReplyWithEmptyIfBadDateTime() {
        // Basically a mock actor that will be responded to
        TestProbe<SchedulerReporter.RespondIsFreeAt> probe =
            testKit.createTestProbe(SchedulerReporter.RespondIsFreeAt.class);

        // Tell (1 time send) them we're asking if the user is free, but with a bad date time string
        schedReporter.tell(new SchedulerReporter.AskIsFreeAt(42L, "bad datetime str", probe.getRef()));

        // Get the response
        SchedulerReporter.RespondIsFreeAt response = probe.receiveMessage();

        // Is it what we expected
        assertEquals(42L, response.requestId);
        assertEquals(Optional.empty(), response.value);
    }

    @Test
    public void testReplyWithIsFree() {
        // Basically a mock actor that will be responded to
        TestProbe<SchedulerReporter.RespondIsFreeAt> probe =
            testKit.createTestProbe(SchedulerReporter.RespondIsFreeAt.class);

        // Tell (1 time send) them we're asking if the user is free, but with a bad date time string
        schedReporter.tell(new SchedulerReporter.AskIsFreeAt(42L, "2022-04-01T00:00:00", probe.getRef()));

        // Get the response
        SchedulerReporter.RespondIsFreeAt response = probe.receiveMessage();

        // Is it what we expected
        assertEquals(42L, response.requestId);
        assertEquals(Optional.of(true), response.value);
    }
    
    @Test
    public void testAddScheduledEvent() {
        // Basically a mock actor that will be responded to
        TestProbe<SchedulerReporter.ScheduleAddedTo> probe =
            testKit.createTestProbe(SchedulerReporter.ScheduleAddedTo.class);

        schedReporter.tell(new SchedulerReporter.AddToSchedule(42L, "2022-04-01T00:00:00", "first event", probe.getRef()));
        SchedulerReporter.ScheduleAddedTo response = probe.receiveMessage();
        assertEquals(42L, response.requestId);
        assertEquals(true, response.value);

        schedReporter.tell(new SchedulerReporter.AddToSchedule(12L, "2022-04-01T00:00:00", "second event", probe.getRef()));
        response = probe.receiveMessage();
        assertEquals(12L, response.requestId);
        assertEquals(false, response.value);
    }

    @Test
    public void testGetEventsInRangeEmpty() {
        TestProbe<SchedulerReporter.ResponseEventsInRange> probe =
            testKit.createTestProbe(SchedulerReporter.ResponseEventsInRange.class);

            DateTime start = DateTime.create(2022, 1, 1, 0, 0, 0);
            DateTime end = DateTime.create(2022, 1, 8, 0, 0, 0);
            schedReporter.tell(new SchedulerReporter.GetEventsInRange(probe.getRef(), Optional.empty(), start, end, false));

            SchedulerReporter.ResponseEventsInRange response = probe.receiveMessage();
            assertEquals(new HashMap<String, Object>(), response.events);
    }
}