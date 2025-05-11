package spring.ai.tools.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.fusesource.jansi.AnsiColors;
import org.fusesource.jansi.AnsiMode;
import org.fusesource.jansi.AnsiType;
import org.fusesource.jansi.io.AnsiOutputStream;

import spring.ai.agents.Agent.Event;
import spring.ai.events.EventService;
import spring.ai.events.Events;

import org.slf4j.Logger;
import org.springframework.beans.factory.DisposableBean;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class TerminalService implements DisposableBean {
    
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(TerminalService.class);

    private boolean isReady;
    private PtyProcess process;
    private Integer columns;
    private Integer rows;
    private BufferedWriter outputWriter;
    private Flux<TerminalOutput> terminalOutputFlux;
    private final EventService eventService;
    private final StringBuffer screenBuffer;
    
    private Disposable terminalOutputDisposable;

    public TerminalService(EventService eventService) {
        this.screenBuffer = new StringBuffer();
        this.eventService = eventService;
    }

    public void onTerminalReady(Integer rows, Integer cols) {
        try {
            initializeProcess();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        onTerminalResize(rows, cols);
    }

    private void initializeProcess() throws Exception {
        if (isReady) {
            return;
        }
        var command = new String[] { "/bin/bash", "-i" }; // TODO: move to application.yml
        var env = new HashMap<>(System.getenv());
        var process = new PtyProcessBuilder()
                .setCommand(command)
                .setEnvironment(env)
                .setRedirectErrorStream(true)
                .start();
        this.outputWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        this.terminalOutputFlux = setupInputStreamReader(process.getInputStream())
                .mergeWith(setupInputStreamReader(process.getErrorStream()))
                .flatMap(this::toTermOutput)
                .doOnNext(o -> appendTermOutput(screenBuffer, o))
                .flatMap(o -> eventService.toOutput(new Event<>("", Map.of(), new Events.Terminal(o.withAscii()))).then(Mono.just(o)))
                .share();
        this.terminalOutputDisposable = this.terminalOutputFlux
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
        this.isReady = true;
    }

    public Mono<TerminalOutput> toTermOutput(String text) {
        var processor = new BasicTerminalProcessor(null);
        try (var bos = new ByteArrayOutputStream();
                var ansiOutput = new AnsiOutputStream(
                        bos,
                        () -> this.columns,
                        AnsiMode.Default,
                        processor,
                        AnsiType.Native,
                        AnsiColors.TrueColor,
                        StandardCharsets.UTF_8,
                        null,
                        null,
                        false)) {
            ansiOutput.write(text.getBytes(StandardCharsets.UTF_8));
            ansiOutput.flush();
            var cbr = countBackSpaces(bos.toString(StandardCharsets.UTF_8));
            return Mono.just(new TerminalOutput(text, cbr.text(), processor.isChangeWindowDetected(),
                    processor.isEraseScreenDetected(), cbr.count()));
        } catch (IOException e) {
            return Mono.error(e);
        }
    }

    private static void appendTermOutput(StringBuffer buffer, TerminalOutput o) {
        if (o.eraseScreen()) {
            buffer.setLength(0);
        }
        var bs = o.backspaces();
        if (bs > 0) {
            buffer.setLength(buffer.length() - Math.min(bs, buffer.length()));
        }
        buffer.append(o.output());
        log.debug("Terminal output: {}", buffer.toString());
    }

    private static CountBackspaceResult countBackSpaces(String text) {
        int count = 0;
        var buffer = new StringBuffer();
        for (var i = 0; i < text.length(); i++) {
            var c = text.charAt(i);
            if (c == '\b') {
                count++;
            } else {
                buffer.append(c);
            }
        }
        return new CountBackspaceResult(count, buffer.toString());
    }

    private Flux<String> setupInputStreamReader(InputStream inputStream) {
        return Flux.using(() -> new BufferedReader(new InputStreamReader(inputStream)), reader -> {
            return Flux.generate(sink -> {
                try {
                    var buffer = new char[1024];
                    var nRead = reader.read(buffer, 0, buffer.length);
                    if (nRead == -1) {
                        sink.complete();
                    } else {
                        sink.next(new String(buffer, 0, nRead));
                    }
                } catch (Exception e) {
                    sink.error(e);
                }
            });
        }, reader -> {
            try {
                reader.close();
            } catch (IOException e) {
                log.error("Error closing reader", e);
            }
        });
    }

    public Mono<String> commandWithOutput(String command) {
        if (Objects.isNull(command)) {
            return Mono.empty(); // throw exception?
        }
        var buffer = new StringBuffer();
        for (var i = this.screenBuffer.length() - 1; i >= 0; i--) {
            var c = this.screenBuffer.charAt(i);
            if (c == '\n') {
                break;
            }
            buffer.insert(0, c);
        }

        // TODO: add a check if prompt is present in the buffer?

        // Create a Mono that writes the command when subscribed to
        var writeCommand = Mono.<Void>fromRunnable(() -> {
            try {
                outputWriter.write(command);
                outputWriter.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());

        return Flux.defer(() -> {
            var limitedOutput = this.terminalOutputFlux
                    .doOnNext(s -> appendTermOutput(buffer, s))
                    .takeUntil(o -> o.isPrompt());
            // First subscribe to output, then send command
            return limitedOutput.doOnRequest(i -> writeCommand.subscribe());
        })
        .then(Mono.fromCallable(() -> buffer.toString()));
    }

    public Mono<Void> onCommand(String command) {
        if (Objects.isNull(command)) {
            return Mono.empty();
        }
        return Mono.defer(() -> {
            try {
                outputWriter.write(command);
                outputWriter.flush();
            } catch (IOException e) {
                return Mono.error(e);
            }
            return Mono.empty();
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then();
    }

    public void onTerminalResize(Integer columns, Integer rows) {
        if (Objects.nonNull(columns) && Objects.nonNull(rows)) {
            this.columns = columns;
            this.rows = rows;
            if (Objects.nonNull(process)) {
                process.setWinSize(new WinSize(this.columns, this.rows));
            }
        }
    }

    private void onTerminalClose() {
        if (!Objects.isNull(process) && process.isAlive()) {
            process.destroy();
        }
        if (!Objects.isNull(outputWriter)) {
            try {
                outputWriter.close();
            } catch (IOException e) {
                log.error("Error closing output writer", e);
            }
        }
        if (this.terminalOutputDisposable != null) {
            this.terminalOutputDisposable.dispose();
            this.terminalOutputDisposable = null;
        }
        this.isReady = false;
    }

    @Override
    public void destroy() throws Exception {
        onTerminalClose();
    }

    private record TerminalOutput(String withAscii, String output, boolean isPrompt, boolean eraseScreen, int backspaces) {
    }

    private record CountBackspaceResult(int count, String text) {
    }
}
