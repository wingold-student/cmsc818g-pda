package com.cmsc818g.StressContextEngine.Reporters;

import java.util.Optional;


import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class LocationReporter extends AbstractBehavior<LocationReporter.Command>{

    public interface Command extends LocationReporter.Command {}

    public static final class AskIsFreeAt implements Command {
        final long requestId;
        final String dateTimeStr;
        final ActorRef<RespondIsFreeAt> replyTo;

        public AskIsFreeAt(long requestId, String dateTimeStr, ActorRef<RespondIsFreeAt> replyTo) {
            this.requestId = requestId;
            this.dateTimeStr = dateTimeStr;
            this.replyTo = replyTo;
        }
    }

    public static final class RespondIsFreeAt {
        final long requestId;
        final Optional<Boolean> value;

        public RespondIsFreeAt(long requestId, Optional<Boolean> value) {
            this.requestId = requestId;
            this.value = value;
        }
    }
    
}
