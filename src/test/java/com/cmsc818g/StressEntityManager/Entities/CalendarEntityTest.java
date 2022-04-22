package com.cmsc818g.StressEntityManager.Entities;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import java.time.Duration;

import akka.Done;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.http.javadsl.model.DateTime;
import akka.pattern.StatusReply;

public class CalendarEntityTest {
    @ClassRule public static final TestKitJunitResource testkit = new TestKitJunitResource();

    @BeforeClass
    public static void beforeClass() throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
    }

    @Test
    public void testReadRowOfData() {
        String dbURI = "jdbc:sqlite:src/test/resources/TestData.db";
        String table = "TestCalendar";
        TestProbe<StatusReply<Done>> replyProbe = testkit.createTestProbe();

        ActorRef<CalendarCommand> calendarActor = testkit.spawn(CalendarEntity.create(dbURI, table));
        calendarActor.tell(new CalendarEntity.ReadRowOfData(1, replyProbe.getRef()));
        assertEquals(StatusReply.Ack(), replyProbe.receiveMessage());
    }

    @Test
    public void testGetCurrentEvent() {
        String dbURI = "jdbc:sqlite:src/test/resources/TestData.db";
        String table = "TestCalendar";
        TestProbe<StatusReply<Done>> replyProbe = testkit.createTestProbe();

        ActorRef<CalendarCommand> calendarActor = testkit.spawn(CalendarEntity.create(dbURI, table));
        calendarActor.tell(new CalendarEntity.ReadRowOfData(1, replyProbe.getRef()));
        assertEquals(StatusReply.Ack(), replyProbe.receiveMessage());

        TestProbe<CalendarResponse> eventProbe = testkit.createTestProbe(CalendarResponse.class);
        calendarActor.tell(new CalendarEntity.GetCurrentEvent(eventProbe.getRef()));
        CalendarResponse msg = eventProbe.receiveMessage();

        assertTrue(msg instanceof CalendarEntity.GetCurrentEventResponse);
        CalendarEntity.GetCurrentEventResponse resp = (CalendarEntity.GetCurrentEventResponse) msg;
        assertTrue(resp.event.isPresent());

        assertEquals("work", resp.event.get().eventName);

        assertEquals(Duration.ofMinutes(30L), resp.event.get().length);

        Optional<DateTime> expectedDateTime = DateTime.fromIsoDateTimeString("2022-04-22T00:00:00");
        assertEquals(expectedDateTime.get(), resp.event.get().time);
    }
}
