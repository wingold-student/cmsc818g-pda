package com.cmsc818g.StressContextEngine.Reporters;

import java.util.Optional;
import java.util.Stack;

import com.cmsc818g.StressEntityManager.Entities.PhoneCommand;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.model.DateTime;

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
public class MediaPlayer extends AbstractBehavior<MediaPlayer.Command> {
    public interface Command {}
    public interface Response {}

    public static final class AddPhone implements Command {}
    public static final class PlayMedia implements Command {
        final String mediaName;
        final Optional<String> deviceName;

        public PlayMedia(String mediaName, Optional<String> deviceName) {
            this.mediaName = mediaName;
            this.deviceName = deviceName;
        }
    }

    public static final class PlayGenre implements Command {
        final String mediaName;
        final String genre;
        final Optional<String> deviceName;

        public PlayGenre(String mediaName, String genre, Optional<String> deviceName) {
            this.mediaName = mediaName;
            this.genre = genre;
            this.deviceName = deviceName;
        }
    }

    public static final class StopPlaying implements Command {
        public StopPlaying() {}
    }

    public static final class GetRecentPlays implements Command {
        final ActorRef<Response> replyTo;
        final Optional<String> genre;

        public GetRecentPlays(ActorRef<Response> replyTo, Optional<String> genre) {
            this.replyTo = replyTo;
            this.genre = genre;
        }
    }

    public static final class RecentPlaysResponse implements Response {
        final String[] recentPlays;

        public RecentPlaysResponse(String[] recentPlays) {
            this.recentPlays = recentPlays;
        }
    }


    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new MediaPlayer(context));
    }


    private ActorRef<PhoneCommand> phoneEntity;
    private Stack<Media> recentPlays;

    public MediaPlayer(ActorContext<Command> context) {
        super(context);
        this.recentPlays = new Stack<>();
    }


    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(AddPhone.class, this::onAddPhone)
            .onMessage(PlayMedia.class, this::onPlayMedia)
            .onMessage(PlayGenre.class, this::onPlayGenre)
            .onMessage(StopPlaying.class, this::onStopPlaying)
            .onMessage(GetRecentPlays.class, this::onGetRecentPlays)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private Behavior<Command> onAddPhone(AddPhone msg) {
        return this;
    }

    private Behavior<Command> onPlayMedia(PlayMedia msg) {
        return this;
    }

    private Behavior<Command> onPlayGenre(PlayGenre msg) {
        return this;
    }

    private Behavior<Command> onStopPlaying(StopPlaying msg) {
        return this;
    }

    private Behavior<Command> onGetRecentPlays(GetRecentPlays msg) {
        return this;
    }

    private MediaPlayer onPostStop() {
        getContext().getLog().info("Media Player stopped");
        return this;
    }


    private class Media {
        private String name;
        private String genre;
        private DateTime playedAt;

        public Media(String name, String genre) {
            this.name = name;
            this.genre = genre;
        }
    }
}
