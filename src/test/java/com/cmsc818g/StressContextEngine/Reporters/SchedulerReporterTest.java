package com.cmsc818g.StressContextEngine.Reporters;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.ClassRule;
import org.junit.Test;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class SchedulerReporterTest {
    @ClassRule public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Test
    public void testReplyWithEmptyIfBadDateTime() {
        // Basically a mock actor that will be responded to
        TestProbe<SchedulerReporter.RespondIsFreeAt> probe =
            testKit.createTestProbe(SchedulerReporter.RespondIsFreeAt.class);

        // The scheduler reporter who can ask another one (just for testing purposes)
        ActorRef<ReporterMessages.Command> schedReporter = testKit.spawn(SchedulerReporter.create("scheduler", "group"));

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

        // The scheduler reporter who can ask another one (just for testing purposes)
        ActorRef<ReporterMessages.Command> schedReporter = testKit.spawn(SchedulerReporter.create("scheduler", "group"));

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

        // The scheduler reporter who can ask another one (just for testing purposes)
        ActorRef<ReporterMessages.Command> schedReporter = testKit.spawn(SchedulerReporter.create("scheduler", "group"));

        schedReporter.tell(new SchedulerReporter.AddToSchedule(42L, "2022-04-01T00:00:00", "first event", probe.getRef()));
        SchedulerReporter.ScheduleAddedTo response = probe.receiveMessage();
        assertEquals(42L, response.requestId);
        assertEquals(true, response.value);

        schedReporter.tell(new SchedulerReporter.AddToSchedule(12L, "2022-04-01T00:00:00", "second event", probe.getRef()));
        response = probe.receiveMessage();
        assertEquals(12L, response.requestId);
        assertEquals(false, response.value);
    }

}