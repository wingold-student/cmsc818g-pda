package com.cmsc818g.ContextActors;

import akka.actor.typed.ActorRef;

public interface ReporterMessages {
    interface Command {}

    static enum Passivate implements Command {
        INSTANCE
    }
}
