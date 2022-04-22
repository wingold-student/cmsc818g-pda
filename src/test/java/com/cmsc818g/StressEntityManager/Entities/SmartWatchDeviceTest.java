package com.cmsc818g.StressEntityManager.Entities;

import akka.Done;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.pattern.StatusReply;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class SmartWatchDeviceTest {
    @ClassRule public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @BeforeClass
    public static void beforeClass() throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
    }

    @Test
    public void testReplyWithEmptyReadingIfNoHeartrateIsKnown() {
        String dbURI = "jdbc:sqlite:src/test/resources/TestData.db";
        String table = "TestSmartWatch";

        TestProbe<SmartWatchDevice.HeartRateReading> probe = testKit.createTestProbe(SmartWatchDevice.HeartRateReading.class);

        ActorRef<SmartWatchDevice.Command> deviceActor = testKit.spawn(SmartWatchDevice.create(dbURI, table));
        deviceActor.tell(new SmartWatchDevice.ReadHeartrate(probe.getRef()));
        SmartWatchDevice.HeartRateReading response = probe.receiveMessage();

        assertEquals(Optional.empty(), response.value);
        assertEquals(Optional.empty(), response.readingTime);
    }

    @Test
    public void testReplyWithEmptyReadingIfNoBloodPressureIsKnown() {
        String dbURI = "jdbc:sqlite:src/test/resources/TestData.db";
        String table = "TestSmartWatch";

        TestProbe<SmartWatchDevice.BloodPressureReading> probe = testKit.createTestProbe(SmartWatchDevice.BloodPressureReading.class);

        ActorRef<SmartWatchDevice.Command> deviceActor = testKit.spawn(SmartWatchDevice.create(dbURI, table));
        deviceActor.tell(new SmartWatchDevice.ReadBloodPressure(probe.getRef()));
        SmartWatchDevice.BloodPressureReading response = probe.receiveMessage();

        assertEquals(Optional.empty(), response.value);
        assertEquals(Optional.empty(), response.readingTime);
    }
    @Test
    public void testReadRowOfData() {
        String dbURI = "jdbc:sqlite:src/test/resources/TestData.db";
        String table = "TestSmartWatch";

        TestProbe<StatusReply<Done>> replyProbe = testKit.createTestProbe();

        ActorRef<SmartWatchDevice.Command> deviceActor = testKit.spawn(SmartWatchDevice.create(dbURI, table));
        deviceActor.tell(new SmartWatchDevice.ReadRowOfData(1, replyProbe.getRef()));
        assertEquals(StatusReply.Ack(), replyProbe.receiveMessage());
    }

    @Test
    public void testReplyWithLatestHeartrateReading() {
        String dbURI = "jdbc:sqlite:src/test/resources/TestData.db";
        String table = "TestSmartWatch";
        TestProbe<StatusReply<Done>> replyProbe = testKit.createTestProbe();

        ActorRef<SmartWatchDevice.Command> smartWatchActor = testKit.spawn(SmartWatchDevice.create(dbURI, table));
        smartWatchActor.tell(new SmartWatchDevice.ReadRowOfData(1, replyProbe.getRef()));
        assertEquals(StatusReply.Ack(), replyProbe.receiveMessage());

        TestProbe<SmartWatchDevice.HeartRateReading> eventProbe = testKit.createTestProbe(SmartWatchDevice.HeartRateReading.class);
        smartWatchActor.tell(new SmartWatchDevice.ReadHeartrate(eventProbe.getRef()));
        SmartWatchDevice.HeartRateReading msg = eventProbe.receiveMessage();

        Integer expected = 65;
        assertEquals(expected, msg.value.get());
    }

    @Test
    public void testReplyWithLatestBloodPressureReading() {
        String dbURI = "jdbc:sqlite:src/test/resources/TestData.db";
        String table = "TestSmartWatch";
        TestProbe<StatusReply<Done>> replyProbe = testKit.createTestProbe();

        ActorRef<SmartWatchDevice.Command> smartWatchActor = testKit.spawn(SmartWatchDevice.create(dbURI, table));
        smartWatchActor.tell(new SmartWatchDevice.ReadRowOfData(1, replyProbe.getRef()));
        assertEquals(StatusReply.Ack(), replyProbe.receiveMessage());

        TestProbe<SmartWatchDevice.BloodPressureReading> eventProbe = testKit.createTestProbe(SmartWatchDevice.BloodPressureReading.class);
        smartWatchActor.tell(new SmartWatchDevice.ReadBloodPressure(eventProbe.getRef()));
        SmartWatchDevice.BloodPressureReading msg = eventProbe.receiveMessage();

        String expected = "120/80";
        assertEquals(expected, msg.value.get());
    }
}