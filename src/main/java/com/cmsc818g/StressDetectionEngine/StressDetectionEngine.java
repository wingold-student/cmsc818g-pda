package com.cmsc818g.StressDetectionEngine;

import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Receive;

public class StressDetectionEngine extends AbstractBehavior<StressDetectionEngine.Command> {

    public interface Command {}

    @Override
    public Receive<Command> createReceive() {
        // TODO Auto-generated method stub
        return null;
    }

    public StressDetectionEngine(ActorContext<Command> context) {
        super(context);
        //TODO Auto-generated constructor stub
    }
}
