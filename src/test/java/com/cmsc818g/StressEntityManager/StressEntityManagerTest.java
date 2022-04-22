package com.cmsc818g.StressEntityManager;

import java.util.ArrayList;

import org.junit.ClassRule;
import org.junit.Test;

import akka.actor.testkit.typed.Effect;
import akka.actor.testkit.typed.javadsl.BehaviorTestKit;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;

public class StressEntityManagerTest {
    @ClassRule public static final TestKitJunitResource testKit = new TestKitJunitResource();

    static final class Tock {}

    @Test
    public void testStartPeriodicDatabaseReading() {
        TestProbe<Tock> test = testKit.createTestProbe();

    }
    
    @Test
    public void testTellEntitiesToRead() {
        BehaviorTestKit<StressEntityManager.Command> test = BehaviorTestKit.create(StressEntityManager.create("", ""));
        test.run(new StressEntityManager.TellEntitiesToRead(1));

        ArrayList<Effect> effects = new ArrayList<Effect>(test.getAllEffects());
        effects.forEach(e -> {
            System.out.println(e);
        });
    }
}
