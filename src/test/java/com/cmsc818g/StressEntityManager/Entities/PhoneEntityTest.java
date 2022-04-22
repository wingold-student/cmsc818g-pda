package com.cmsc818g.StressEntityManager.Entities;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;

import com.cmsc818g.StressEntityManager.StressEntityManager;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import akka.Done;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.actor.typed.scaladsl.Behaviors;
import akka.pattern.StatusReply;
import akka.testkit.TestActorRef;

public class PhoneEntityTest {
    @ClassRule public static final TestKitJunitResource testkit = new TestKitJunitResource();
    private static TestProbe<StressEntityManager.Command> mockEntityManager;

    @BeforeClass
    public static void beforeClass() throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        mockEntityManager = testkit.createTestProbe();
    }

    @Test
    public void testReadRowOfData() {
        String dbURI = "jdbc:sqlite:src/test/resources/TestData.db";
        String table = "TestPhone";
        TestProbe<StatusReply<Done>> replyProbe = testkit.createTestProbe();

        ActorRef<PhoneEntity.Command> phoneActor = testkit.spawn(PhoneEntity.create(mockEntityManager.getRef(), dbURI, table));
        phoneActor.tell(new PhoneEntity.ReadRowOfData(1, replyProbe.getRef()));
        assertEquals(StatusReply.Ack(), replyProbe.receiveMessage());
    }

    @Test
    public void testLocation() {
        String dbURI = "jdbc:sqlite:src/test/resources/TestData.db";
        String table = "TestPhone";
        TestProbe<StatusReply<Done>> replyProbe = testkit.createTestProbe();

        ActorRef<PhoneEntity.Command> phoneActor = testkit.spawn(PhoneEntity.create(mockEntityManager.getRef(), dbURI, table));
        phoneActor.tell(new PhoneEntity.ReadRowOfData(1, replyProbe.getRef()));
        assertEquals(StatusReply.Ack(), replyProbe.receiveMessage());

        TestProbe<PhoneEntity.Response> locationProbe = testkit.createTestProbe(PhoneEntity.Response.class);
        phoneActor.tell(new PhoneEntity.AskingForLocation(locationProbe.getRef()));
        PhoneEntity.Response msg = locationProbe.receiveMessage();

        assertTrue(msg instanceof PhoneEntity.LocationResponse);
        PhoneEntity.LocationResponse resp = (PhoneEntity.LocationResponse) msg;
        assertTrue(resp.location.isPresent());
        assertEquals("home", resp.location.get());
    }

    @Test
    public void testSleep() {
        String dbURI = "jdbc:sqlite:src/test/resources/TestData.db";
        String table = "TestPhone";
        TestProbe<StatusReply<Done>> replyProbe = testkit.createTestProbe();

        ActorRef<PhoneEntity.Command> phoneActor = testkit.spawn(PhoneEntity.create(mockEntityManager.getRef(), dbURI, table));
        phoneActor.tell(new PhoneEntity.ReadRowOfData(1, replyProbe.getRef()));
        assertEquals(StatusReply.Ack(), replyProbe.receiveMessage());

        TestProbe<PhoneEntity.Response> sleepProbe = testkit.createTestProbe(PhoneEntity.Response.class);
        phoneActor.tell(new PhoneEntity.AskingForSleepData(sleepProbe.getRef()));
        PhoneEntity.Response msg = sleepProbe.receiveMessage();

        assertTrue(msg instanceof PhoneEntity.SleepDataResponse);
        PhoneEntity.SleepDataResponse resp = (PhoneEntity.SleepDataResponse) msg;
        assertTrue(resp.amountSlept.isPresent());
        Integer expected = 3;
        assertEquals(expected, resp.amountSlept.get());
    }
}
