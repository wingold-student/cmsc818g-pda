package com.cmsc818g.StressEntityManager.Entities;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.ClassRule;
import org.junit.Test;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class SmartWatchDeviceTest {
    @ClassRule public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Test
    public void testReplyWithEmptyReadingIfNoHeartrateIsKnown() {
        TestProbe<SmartWatchDevice.RespondHeartrate> probe = testKit.createTestProbe(SmartWatchDevice.RespondHeartrate.class);

        ActorRef<SmartWatchDevice.Command> deviceActor = testKit.spawn(SmartWatchDevice.create("group", "device"));
        deviceActor.tell(new SmartWatchDevice.ReadHeartrate(42L, probe.getRef()));
        SmartWatchDevice.RespondHeartrate response = probe.receiveMessage();

        assertEquals(42L, response.requestId);
        assertEquals(Optional.empty(), response.value);
    }

    @Test
    public void testReplyWithLatestHeartrateReading() {
        TestProbe<SmartWatchDevice.HeartrateRecorded> recordProbe = testKit.createTestProbe(SmartWatchDevice.HeartrateRecorded.class);
        TestProbe<SmartWatchDevice.RespondHeartrate> readProbe = testKit.createTestProbe(SmartWatchDevice.RespondHeartrate.class);

        ActorRef<SmartWatchDevice.Command> deviceActor = testKit.spawn(SmartWatchDevice.create("group", "device"));

        deviceActor.tell(new SmartWatchDevice.RecordHeartrate(1L, 65, recordProbe.getRef()));
        assertEquals(1L, recordProbe.receiveMessage().requestId);

        deviceActor.tell(new SmartWatchDevice.ReadHeartrate(2L, readProbe.getRef()));
        SmartWatchDevice.RespondHeartrate response1 = readProbe.receiveMessage();
        assertEquals(2L, response1.requestId);
        assertEquals(Optional.of(65), response1.value);

        deviceActor.tell(new SmartWatchDevice.RecordHeartrate(3L, 70, recordProbe.getRef()));
        assertEquals(3L, recordProbe.receiveMessage().requestId);

        deviceActor.tell(new SmartWatchDevice.ReadHeartrate(4L, readProbe.getRef()));
        SmartWatchDevice.RespondHeartrate response2 = readProbe.receiveMessage();
        assertEquals(4L, response2.requestId);
        assertEquals(Optional.of(70), response2.value);
    }
}