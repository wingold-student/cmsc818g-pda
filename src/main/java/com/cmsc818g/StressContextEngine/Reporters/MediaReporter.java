package com.cmsc818g.StressContextEngine.Reporters;

import java.util.Optional;
import java.util.Stack;

import com.cmsc818g.StressEntityManager.Entities.PhoneEntity;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.model.DateTime;
import akka.protobufv3.internal.Duration;

/**
 * Maybe instead of attempting to play media, which for this
 * prototype would go to the phone, which would then somehow go to the
 * UI...
 * 
 * This could track what the user likes to listen to and when? It could
 * be used by the Recommendation engine to play something that the user listens
 * to when stressed? (or when happy?) and by the Detection engine to maybe
 * correlate listening to stress level?
 */
public class MediaReporter extends AbstractBehavior<MediaReporter.Command> {
    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command {}

    public static final class AddPhone implements Command {}

    public static final class GetRecentPlays implements Command {
        final ActorRef<Response> replyTo;
        final Optional<String> genre;

        public GetRecentPlays(ActorRef<Response> replyTo, Optional<String> genre) {
            this.replyTo = replyTo;
            this.genre = genre;
        }
    }

    public static final class GetPlaysAtTime implements Command {
        final ActorRef<Response> replyTo;
        final DateTime atTime;
        final Duration giveOrTake;

        public GetPlaysAtTime(ActorRef<Response> replyTo, DateTime atTime, Duration giveOrTake) {
            this.replyTo = replyTo;
            this.atTime = atTime;
            this.giveOrTake = giveOrTake;
        }
    }

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/
    public interface Response {}

    public static final class RecentPlaysResponse implements Response {
        final PhoneEntity.Media[] recentPlays;

        public RecentPlaysResponse(PhoneEntity.Media[] recentPlays) {
            this.recentPlays = recentPlays;
        }
    }

    public static final class PlaysAtTimeResponse implements Response {
        final PhoneEntity.Media[] plays;

        public PlaysAtTimeResponse(PhoneEntity.Media[] plays) {
            this.plays = plays;
        }
    }

    /************************************* 
     * CREATION 
     *************************************/

    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new MediaReporter(context));
    }


    private ActorRef<PhoneEntity.Command> phoneEntity;
    private Stack<PhoneEntity.Media> recentPlays;

    public MediaReporter(ActorContext<Command> context) {
        super(context);
        this.recentPlays = new Stack<>();
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(AddPhone.class, this::onAddPhone)
            .onMessage(GetRecentPlays.class, this::onGetRecentPlays)
            .onMessage(GetPlaysAtTime.class, this::onGetPlaysAtTime)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private Behavior<Command> onAddPhone(AddPhone msg) {
        return this;
    }

    private Behavior<Command> onGetRecentPlays(GetRecentPlays msg) {
        return this;
    }

    private Behavior<Command> onGetPlaysAtTime(GetPlaysAtTime msg) {
        return this;
    }

    private MediaReporter onPostStop() {
        getContext().getLog().info("Media Player stopped");
        return this;
    }

}
