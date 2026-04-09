package com.example.gameoflife;

import de.neuland.jade4j.Jade4J;
import de.neuland.jade4j.JadeConfiguration;
import de.neuland.jade4j.template.ClasspathTemplateLoader;
import de.neuland.jade4j.template.JadeTemplate;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import starfederation.datastar.adapters.response.HttpServletResponseAdapter;
import starfederation.datastar.events.PatchElements;
import starfederation.datastar.utils.ServerSentEventGenerator;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

@Controller
public class GameController {

    private final GameService gameService;
    private JadeConfiguration jade;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostConstruct
    public void init() {
        jade = new JadeConfiguration();
        jade.setTemplateLoader(new ClasspathTemplateLoader("UTF-8"));
        jade.setPrettyPrint(false);
        jade.setMode(Jade4J.Mode.HTML);
    }

    @GetMapping("/")
    @ResponseBody
    public String index() throws Exception {
        JadeTemplate template = jade.getTemplate("templates/index");
        Map<String, Object> model = new HashMap<>();
        model.put("boardSize", GameService.BOARD_SIZE);
        model.put("boardHtml", gameService.getCachedBoardHtml());
        return jade.renderTemplate(template, model);
    }

    @GetMapping("/sse")
    public void sse(HttpServletRequest request, HttpServletResponse response) throws Exception {
        AsyncContext asyncContext = request.startAsync();
        asyncContext.setTimeout(0);

        CountDownLatch closeLatch = new CountDownLatch(1);

        asyncContext.addListener(new AsyncListener() {
            @Override public void onComplete(AsyncEvent event) { closeLatch.countDown(); }
            @Override public void onTimeout(AsyncEvent event) { closeLatch.countDown(); }
            @Override public void onError(AsyncEvent event) { closeLatch.countDown(); }
            @Override public void onStartAsync(AsyncEvent event) {}
        });

        var responseAdapter = new HttpServletResponseAdapter(response);
        ServerSentEventGenerator sse = new ServerSentEventGenerator(responseAdapter);

        Runnable listener = () -> {
            try {
                String boardHtml = gameService.getCachedBoardHtml();
                String fragment = buildBoardFragment(boardHtml);
                PatchElements event = PatchElements.builder()
                        .data(fragment)
                        .build();
                sse.send(event);
            } catch (Exception e) {
                closeLatch.countDown();
            }
        };

        gameService.addListener(listener);

        // Send initial board state
        try {
            String boardHtml = gameService.getCachedBoardHtml();
            String fragment = buildBoardFragment(boardHtml);
            PatchElements event = PatchElements.builder()
                    .data(fragment)
                    .build();
            sse.send(event);
        } catch (Exception e) {
            gameService.removeListener(listener);
            asyncContext.complete();
            return;
        }

        // Wait until connection drops
        Thread.ofVirtual().name("sse-wait").start(() -> {
            try {
                closeLatch.await();
            } catch (InterruptedException ignored) {
            } finally {
                gameService.removeListener(listener);
                try {
                    sse.close();
                } catch (Exception ignored) {
                }
                try {
                    asyncContext.complete();
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static String buildBoardFragment(String boardHtml) {
        return "<div id=\"board\" class=\"board\" data-on:pointerdown=\"@post('/tap?id=' + evt.target.dataset.id)\">"
                + boardHtml + "</div>";
    }

    @PostMapping("/tap")
    @ResponseBody
    public void tap(@RequestParam("id") int id, HttpServletRequest request) {
        int userId = id; // use the cell id hash as a simple differentiator
        // Derive a color from the session
        String sessionId = request.getSession(true).getId();
        int color = Game.colorForUser(sessionId.hashCode());
        gameService.getGame().fillCross(id, color);
    }
}
