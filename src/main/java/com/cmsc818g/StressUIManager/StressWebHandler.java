package com.cmsc818g.StressUIManager;

import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Receive;

public class StressWebHandler extends AbstractBehavior<StressWebHandler.Command> {

    public interface Command {}

    @Override
    public Receive<Command> createReceive() {
        return null;
    }

    public StressWebHandler(ActorContext<Command> context) {
        super(context);
    }
}
