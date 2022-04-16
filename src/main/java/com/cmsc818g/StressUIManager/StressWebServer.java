package com.cmsc818g.StressUIManager;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletionStage;
import java.time.Duration;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;

/**
 * TODO: Maybe an Actor just should NOT be the server. Instead it is started in main...
 * Unless it is not actually blocking?
 */
public class StressWebServer extends AbstractBehavior<StressWebServer.Command> {
    public interface Command {}

    /** Command to start the server with the supplied routes. */
    public static final class StartServer implements Command {
        final Route route;

        public StartServer(Route route) {
            this.route = route;
        }
    }

    /** Just a sample message/command. */
    public static final class Hello implements Command {
        final String msg;
        public Hello(String msg) {
            this.msg = msg;
        }
    }

    /** Command to stop the server. */
    public static final class StopServer implements Command {
        public StopServer() {}
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new StressWebServer(context));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(StartServer.class, this::onStartServer)
            .onMessage(StopServer.class, this::onStopServer)
            .onMessage(Hello.class, this::onHello)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    } 

    private CompletionStage<ServerBinding> futureBinding;

    public StressWebServer(ActorContext<Command> context) {
        super(context);
        context.getLog().info("Web Server Actor started");
    }

    /**
     * Start up the server with the provided routes (within the msg).
     * @param msg Holds the routes to serve
     * @return this
     */
    private Behavior<Command> onStartServer(StartServer msg) {
        getContext().getLog().info("Starting web server...");
        ActorSystem<?> system = getContext().getSystem();
        Route route = msg.route;

        // Waits for the binding to succeed essentially
        this.futureBinding =
            Http.get(getContext().getSystem()).newServerAt("localhost", 8080).bind(route);

        // Once succeeded, print out the data and that it is started
        this.futureBinding.whenComplete((binding, exception) -> {
            if (binding != null) {
                InetSocketAddress address = binding.localAddress();
                system.log().info("Server online at http://{}:{}",
                    address.getHostString(),
                    address.getPort());
            } else {
                system.log().error("Failed to bind to HTTP endpoint, terminating", exception);
                system.terminate(); // TODO: Just terminate the actor?
            }
        });

        return this;
    }

    /**
     * Attempts to 'gracefully' shutdown the server if it is running.
     * 
     * TODO: Return boolean on if it was running or successful?
     */
    private void shutdownServer() {
        this.futureBinding.whenComplete((binding, exception) -> {
            if (binding != null) {
                getContext().getLog().info("Stopping the web server...");
                binding.terminate(Duration.ofSeconds(3));
            } else {
                getContext().getLog().warn("Server not running or bound");
            }
        });
    }

    private StressWebServer onPostStop() {
        getContext().getLog().info("StressWebServer stopped");
        shutdownServer();
        return this;
    }

    private Behavior<Command> onStopServer(StopServer msg) {
        getContext().getLog().info("Requested to stop server");
        shutdownServer();
        return this;
    }

    private Behavior<Command> onHello(Hello msg) {
        getContext().getLog().info("Got Hello message: {}", msg.msg);
        return this;
    }
}
