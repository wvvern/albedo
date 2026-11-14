package dev.albedo.core.dev;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** The commands of the harness that wait to run, and the phase that holds them back. */
final class HarnessQueue {
    /** What holds the queue back: nothing, a count of ticks, the load of a world, or the screenshots of a {@code frames} command. */
    enum Phase { RUN, WAIT_TICKS, WAIT_WORLD, BURST }

    /** The result of a command: done, or run again on the next tick while it has tries left. */
    enum Outcome { DONE, RETRY }

    /** What the queue needs from the harness. */
    interface Runner {
        boolean inWorld();

        Outcome run(Command command);
    }

    /** One command line: its verb and the text after it. */
    static final class Command {
        final String line;
        final String verb;
        final String arg;
        private Integer triesLeft;
        private boolean retried;

        private Command(String line) {
            int space = line.indexOf(' ');
            this.line = line;
            this.verb = space < 0 ? line : line.substring(0, space);
            this.arg = space < 0 ? "" : line.substring(space + 1).trim();
        }

        /** @return null for an empty line or a comment line, which starts with "#" */
        static Command parse(String text) {
            String line = text.trim();
            return line.isEmpty() || line.startsWith("#") ? null : new Command(line);
        }

        /**
         * The tries left of a command that can return {@link Outcome#RETRY}.
         *
         * @param given the tries that the command line gives, which count only on the first run
         */
        int tries(int given) {
            if (triesLeft == null) triesLeft = given;
            return triesLeft;
        }
    }

    private final Deque<Command> commands = new ArrayDeque<Command>();
    private Phase phase = Phase.RUN;
    private int ticksLeft;
    private int burstLeft;
    private String burstName;
    private int burstIndex;

    void addAll(List<String> lines) {
        for (String text : lines) {
            Command command = Command.parse(text);
            if (command != null) commands.add(command);
        }
    }

    Phase phase() {
        return phase;
    }

    /** Holds the next commands back for this many ticks, where a count below 0 holds them back for good. */
    void waitTicks(int ticks) {
        if (ticks == 0) return;
        phase = Phase.WAIT_TICKS;
        ticksLeft = ticks;
    }

    void waitWorld() {
        phase = Phase.WAIT_WORLD;
    }

    /** Holds the next commands back until {@link #nextFrame} gave {@code count} names. */
    void burst(int count, String name) {
        // A count below 1 would stop the queue for good, because only a screenshot of the burst counts it down.
        if (count < 1) throw new IllegalArgumentException("frames <count of 1 or more> <name>");
        phase = Phase.BURST;
        burstLeft = count;
        burstName = name;
        burstIndex = 0;
    }

    /** @return the file name of the next screenshot of the burst, or null when no burst runs */
    String nextFrame() {
        if (phase != Phase.BURST) return null;
        String name = String.format(Locale.ROOT, "%s-%02d.png", burstName, burstIndex++);
        if (--burstLeft == 0) phase = Phase.RUN;
        return name;
    }

    /** Ends the phase when it is over, then runs commands until one starts a phase or none are left. */
    void advance(Runner runner) {
        switch (phase) {
            case WAIT_WORLD:
                if (!runner.inWorld()) return;
                phase = Phase.RUN;
                break;
            case WAIT_TICKS:
                if (ticksLeft > 0 && --ticksLeft == 0) phase = Phase.RUN;
                return;
            case BURST:
                return;
            default:
                break;
        }
        while (phase == Phase.RUN && !commands.isEmpty()) {
            Command command = commands.poll();
            if (!command.retried) HarnessLog.log("> " + command.line);
            try {
                if (runner.run(command) == Outcome.RETRY) retry(command);
            } catch (RuntimeException e) {
                HarnessLog.fail(e.toString());
            }
        }
    }

    private void retry(Command command) {
        int left = command.tries(0);
        if (left <= 0) {
            HarnessLog.fail(command.verb + ": this version has no sharp sky shader, or it does not compile");
            return;
        }
        command.triesLeft = left - 1;
        command.retried = true;
        commands.addFirst(command);
        waitTicks(1);
    }
}
