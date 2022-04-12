package com.cmsc818g.StressUIManager;

import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Receive;

public class StressUIManager extends AbstractBehavior<StressUIManager.Command> {

    public interface Command {}

    @Override
    public Receive<Command> createReceive() {
        // TODO Auto-generated method stub
        return null;
    }

    public StressUIManager(ActorContext<Command> context) {
        super(context);
        //TODO Auto-generated constructor stub
    }
}
