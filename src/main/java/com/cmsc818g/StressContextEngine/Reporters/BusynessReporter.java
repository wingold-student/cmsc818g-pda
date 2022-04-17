package com.cmsc818g.StressContextEngine.Reporters;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class BusynessReporter extends AbstractBehavior<BusynessReporter.Command> {
    public interface Command {}

    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new BusynessReporter(context));
    }

    public BusynessReporter(ActorContext<Command> context) {
        super(context);
        //TODO Auto-generated constructor stub
    }

    @Override
    public Receive<Command> createReceive() {
        // TODO Auto-generated method stub
        return null;
    }

}
